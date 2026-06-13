import * as admin from "firebase-admin";
import {getFirestore} from "firebase-admin/firestore";
import {onRequest} from "firebase-functions/v2/https";
import {getPaymentConfig} from "../config/paymentConfig";
import {
  createRedsysSignature,
  timingSafeSignatureEquals,
} from "./redsysSignature";

function decodeMerchantParameters(encodedData: string): Record<string, unknown> {
  return JSON.parse(Buffer.from(encodedData, "base64").toString("utf8"));
}

function getRedsysField(
  data: Record<string, unknown>,
  canonicalName: string
): string {
  const entry = Object.entries(data).find(
    ([key]) => key.toLowerCase() === canonicalName.toLowerCase()
  );

  return entry?.[1] == null ? "" : String(entry[1]);
}

function isApprovedResponse(responseCode: string): boolean {
  const value = Number(responseCode);

  return Number.isInteger(value) && value >= 0 && value <= 99;
}

function normalizeDatabaseId(databaseId: string): string {
  return databaseId === "debug" ? "debug" : "(default)";
}

export const paymentNotification = onRequest(
  {region: "europe-west1"},
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method not allowed");
      return;
    }

    const signature = String(req.body?.Ds_Signature || "");
    const merchantParameters = String(req.body?.Ds_MerchantParameters || "");

    if (!signature || !merchantParameters) {
      res.status(400).send("Missing Redsys parameters");
      return;
    }

    try {
      const decodedData = decodeMerchantParameters(merchantParameters);
      const order = getRedsysField(decodedData, "Ds_Order");
      const responseCode = getRedsysField(decodedData, "Ds_Response");
      const amount = getRedsysField(decodedData, "Ds_Amount");
      const merchantCode = getRedsysField(decodedData, "Ds_MerchantCode");
      const databaseId = normalizeDatabaseId(
        getRedsysField(decodedData, "Ds_MerchantData")
      );

      if (!order || !responseCode) {
        res.status(400).send("Invalid Redsys parameters");
        return;
      }

      const expectedSignature = createRedsysSignature(
        merchantParameters,
        order,
        getPaymentConfig().SECRET_KEY
      );

      if (!timingSafeSignatureEquals(expectedSignature, signature)) {
        res.status(400).send("Invalid signature");
        return;
      }

      const isApproved = isApprovedResponse(responseCode);
      const firestore = databaseId === "(default)" ?
        admin.firestore() :
        getFirestore(databaseId);
      const paymentByOrderSnap = await firestore
        .collection("payments")
        .where("orderId", "==", order)
        .limit(1)
        .get();
      const paymentRef = paymentByOrderSnap.docs[0]?.ref ||
        firestore.collection("payments").doc(order);
      const paymentUpdate: Record<string, unknown> = {
        redsysResponse: responseCode,
        redsysAmount: amount,
        redsysMerchantCode: merchantCode,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
        validationSource: "redsys",
      };

      if (isApproved) {
        paymentUpdate.status = "PAID";
        paymentUpdate.validatedAt = admin.firestore.FieldValue.serverTimestamp();
      } else {
        paymentUpdate.status = "REJECTED";
        paymentUpdate.validatedAt = admin.firestore.FieldValue.delete();
        paymentUpdate.redsysLastFailedResponse = responseCode;
        paymentUpdate.redsysLastFailedAt =
          admin.firestore.FieldValue.serverTimestamp();
      }

      await paymentRef.set(
        paymentUpdate,
        {merge: true}
      );

      if (isApproved) {
        const paymentSnap = await paymentRef.get();
        const paymentData = paymentSnap.data();

        if (paymentData?.userId) {
          await firestore
            .collection("users")
            .doc(paymentData.userId)
            .collection("payments")
            .doc(order)
            .set(
              {
                excursionId: paymentData.excursionId,
                excursionTitle: paymentData.excursionTitle,
                amount: paymentData.amount,
                orderId: order,
                status: "PAID",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              },
              {merge: true}
            );
        }
      }

      res.status(200).send("OK");
    } catch (error) {
      console.error("Error processing Redsys notification:", error);
      res.status(500).send("Internal error");
    }
  }
);
