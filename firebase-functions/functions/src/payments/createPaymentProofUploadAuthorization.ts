import * as admin from "firebase-admin";
import {randomBytes} from "node:crypto";
import {getFirestore} from "firebase-admin/firestore";
import {
  CallableRequest,
  HttpsError,
  onCall,
} from "firebase-functions/v2/https";

interface PaymentProofUploadAuthorizationData {
  excursionId: string;
  authorizationId: string;
  databaseId?: string;
}

const UPLOAD_AUTHORIZATION_TTL_MS = 10 * 60 * 1000;

/**
 * Limits callable requests to the Firestore databases used by the app.
 *
 * @param {unknown} databaseId Requested database identifier.
 * @return {string} A supported Firestore database identifier.
 */
function normalizeDatabaseId(databaseId: unknown): string {
  return String(databaseId || "(default)").trim() === "debug" ?
    "debug" :
    "(default)";
}

export const createPaymentProofUploadAuthorization = onCall(
  {
    region: "europe-west1",
    invoker: "public",
  },
  async (
    request: CallableRequest<PaymentProofUploadAuthorizationData>
  ) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesion");
    }

    const excursionId = String(request.data.excursionId || "").trim();
    const authorizationId = String(
      request.data.authorizationId || ""
    ).trim();
    const databaseId = normalizeDatabaseId(request.data.databaseId);

    if (!excursionId || !authorizationId) {
      throw new HttpsError(
        "invalid-argument",
        "Faltan datos para autorizar la subida"
      );
    }

    const sourceFirestore = databaseId === "(default)" ?
      admin.firestore() :
      getFirestore(databaseId);
    const authorizationSnapshot = await sourceFirestore
      .collection("signedAuthorizations")
      .doc(authorizationId)
      .get();

    if (!authorizationSnapshot.exists) {
      throw new HttpsError(
        "failed-precondition",
        "La autorizacion aprobada no existe"
      );
    }

    const authorization = authorizationSnapshot.data();
    const belongsToUser = authorization?.userId === request.auth.uid;
    const belongsToExcursion = authorization?.excursionId === excursionId;
    const isApproved = authorization?.status === "APPROVED";

    if (!belongsToUser || !belongsToExcursion || !isApproved) {
      throw new HttpsError(
        "permission-denied",
        "La autorizacion no permite subir este comprobante"
      );
    }

    const uploadAuthorizationId = randomBytes(24).toString("hex");
    const expiresAt = admin.firestore.Timestamp.fromMillis(
      Date.now() + UPLOAD_AUTHORIZATION_TTL_MS
    );

    await admin.firestore()
      .collection("paymentProofUploadAuthorizations")
      .doc(uploadAuthorizationId)
      .set({
        userId: request.auth.uid,
        excursionId,
        authorizationId,
        databaseId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        expiresAt,
      });

    return {
      uploadAuthorizationId,
      expiresAtMillis: expiresAt.toMillis(),
    };
  }
);
