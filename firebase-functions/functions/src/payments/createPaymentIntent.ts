import * as admin from "firebase-admin";
import {getFirestore} from "firebase-admin/firestore";
import {HttpsError, CallableRequest, onCall} from "firebase-functions/v2/https";
import {getPaymentConfig} from "../config/paymentConfig";
import {createRedsysSignature} from "./redsysSignature";

interface PaymentIntentData {
  excursionId: string;
  amount: number;
  userName: string;
  userEmail: string;
  databaseId?: string;
}

function encodeBase64(data: Record<string, string>): string {
  return Buffer.from(JSON.stringify(data), "utf8").toString("base64");
}

function createRedsysOrderId(): string {
  const timestampPart = Date.now().toString().slice(-8);
  const randomPart = Math.floor(Math.random() * 10000)
    .toString()
    .padStart(4, "0");

  return `${timestampPart}${randomPart}`;
}

function createPaymentDocumentId(userId: string, excursionId: string): string {
  return `${userId}_${excursionId}`;
}

function normalizeAmount(amount: unknown): number {
  const value = Number(amount);

  if (!Number.isFinite(value) || value <= 0) {
    throw new HttpsError("invalid-argument", "Importe de pago invalido");
  }

  return Math.round(value * 100) / 100;
}

function normalizeDatabaseId(databaseId: unknown): string {
  const value = String(databaseId || "(default)").trim();

  return value === "debug" ? "debug" : "(default)";
}

function sanitizeRedsysText(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Za-z0-9 .,_/-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function truncateRedsysText(value: string, maxLength: number): string {
  const sanitized = sanitizeRedsysText(value);

  return sanitized.length > maxLength ?
    sanitized.slice(0, maxLength).trim() :
    sanitized;
}

function buildPaymentDescription(
  excursionTitle: string,
  participantNames: string[]
): string {
  const title = sanitizeRedsysText(excursionTitle || "Excursion");
  const participants = participantNames
    .map((name) => sanitizeRedsysText(name))
    .filter((name) => name.length > 0);

  if (participants.length === 0) {
    return truncateRedsysText(title, 125);
  }

  return truncateRedsysText(`${title} - ${participants.join(", ")}`, 125);
}

export const createPaymentIntent = onCall(
  {region: "europe-west1"},
  async (request: CallableRequest<PaymentIntentData>) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesion");
    }

    const {excursionId, userName, userEmail} = request.data;
    const amount = normalizeAmount(request.data.amount);
    const databaseId = normalizeDatabaseId(request.data.databaseId);

    if (!excursionId || typeof excursionId !== "string") {
      throw new HttpsError("invalid-argument", "Falta la excursion");
    }

    const firestore = databaseId === "(default)" ?
      admin.firestore() :
      getFirestore(databaseId);
    const excursionRef = firestore.collection("excursions").doc(excursionId);
    const excursionSnap = await excursionRef.get();

    if (!excursionSnap.exists) {
      throw new HttpsError("not-found", "Excursion no encontrada");
    }

    const excursion = excursionSnap.data();
    const expectedAmount = Number(excursion?.price ?? 0);

    if (!Number.isFinite(expectedAmount) || expectedAmount <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "La excursion no tiene precio configurado"
      );
    }

    const approvedAuthorizationSnap = await firestore
      .collection("signedAuthorizations")
      .where("excursionId", "==", excursionId)
      .where("userId", "==", request.auth.uid)
      .where("status", "==", "APPROVED")
      .get();

    if (approvedAuthorizationSnap.empty) {
      throw new HttpsError(
        "failed-precondition",
        "La autorizacion debe estar aprobada antes de pagar"
      );
    }

    const authorizationIds = approvedAuthorizationSnap.docs.map((doc) => doc.id);
    const participantCount = approvedAuthorizationSnap.size;
    const expectedTotalAmount = Math.round(
      expectedAmount * participantCount * 100
    ) / 100;

    if (Math.abs(expectedTotalAmount - amount) > 0.01) {
      throw new HttpsError(
        "invalid-argument",
        "El importe no coincide con las autorizaciones aprobadas"
      );
    }

    const participantNames = approvedAuthorizationSnap.docs
      .map((doc) => String(doc.get("minorName") || "").trim())
      .filter((name) => name.length > 0);
    const paymentDescription = buildPaymentDescription(
      String(excursion?.title || "Excursion Asociacion Ciguena"),
      participantNames.length > 0 ? participantNames : [userName || "Usuario"]
    );

    const maxParticipants = Number(excursion?.maxParticipants || 0);
    if (maxParticipants > 0) {
      const activeAuthorizationsSnap = await firestore
        .collection("signedAuthorizations")
        .where("excursionId", "==", excursionId)
        .get();
      const activeAuthorizationsCount = activeAuthorizationsSnap.docs.filter(
        (doc) => ["PENDING", "APPROVED"].includes(doc.get("status"))
      ).length;

      if (activeAuthorizationsCount > maxParticipants) {
        throw new HttpsError(
          "failed-precondition",
          "No quedan plazas disponibles para esta excursion"
        );
      }
    }

    const config = getPaymentConfig();
    const orderId = createRedsysOrderId();
    const paymentDocumentId = createPaymentDocumentId(
      request.auth.uid,
      excursionId
    );
    const paymentRef = firestore.collection("payments").doc(paymentDocumentId);
    const existingPaymentSnap = await paymentRef.get();
    const existingPaymentStatus = String(
      existingPaymentSnap.get("status") || ""
    );

    if (existingPaymentStatus === "PAID") {
      throw new HttpsError(
        "failed-precondition",
        "El pago ya esta confirmado"
      );
    }

    const amountInCents = Math.round(expectedTotalAmount * 100).toString();
    const resultBaseUrl =
      "https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentResult";

    const paymentData: Record<string, string> = {
      DS_MERCHANT_AMOUNT: amountInCents,
      DS_MERCHANT_ORDER: orderId,
      DS_MERCHANT_MERCHANTCODE: config.MERCHANT_CODE,
      DS_MERCHANT_CURRENCY: config.CURRENCY,
      DS_MERCHANT_TRANSACTIONTYPE: "0",
      DS_MERCHANT_TERMINAL: config.TERMINAL,
      DS_MERCHANT_MERCHANTURL: config.URL_NOTIFICATION,
      DS_MERCHANT_MERCHANTNAME: config.MERCHANT_NAME,
      DS_MERCHANT_MERCHANTDESCRIPTOR: config.MERCHANT_NAME,
      DS_MERCHANT_URLOK: `${resultBaseUrl}?status=success&order=${orderId}`,
      DS_MERCHANT_URLKO: `${resultBaseUrl}?status=error&order=${orderId}`,
      DS_MERCHANT_CONSUMERLANGUAGE: "1",
      DS_MERCHANT_MERCHANTDATA: databaseId,
      DS_MERCHANT_PRODUCTDESCRIPTION: paymentDescription,
      DS_MERCHANT_TITULAR: String(userName || "Usuario").slice(0, 60),
    };

    const merchantParameters = encodeBase64(paymentData);
    const signature = createRedsysSignature(
      merchantParameters,
      orderId,
      config.SECRET_KEY
    );

    const paymentDocument: Record<string, unknown> = {
      excursionId,
      userId: request.auth.uid,
      userName: userName || "Usuario",
      userEmail: userEmail || request.auth.token.email || "",
      amount: expectedTotalAmount,
      unitPrice: expectedAmount,
      participantCount,
      orderId,
      status: "INITIATED",
      authorizationId: authorizationIds[0],
      authorizationIds,
      participantNames,
      paymentDescription,
      excursionTitle: excursion?.title || "",
      paymentMethod: "redsys",
      databaseId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (!existingPaymentSnap.exists) {
      paymentDocument.createdAt = admin.firestore.FieldValue.serverTimestamp();
    }

    await paymentRef.set(paymentDocument, {merge: true});

    return {
      success: true,
      orderId,
      tpvUrl: config.URL_TPV,
      params: {
        Ds_SignatureVersion: "HMAC_SHA256_V1",
        Ds_MerchantParameters: merchantParameters,
        Ds_Signature: signature,
      },
    };
  }
);
