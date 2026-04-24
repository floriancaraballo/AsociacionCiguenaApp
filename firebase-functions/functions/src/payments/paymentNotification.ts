// functions/src/payments/paymentNotification.ts

import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import { onRequest } from 'firebase-functions/v2/https';  // ← SIN region
import { getPaymentConfig } from '../config/paymentConfig';

admin.initializeApp();

function verifySignature(encodedData: string, signature: string, key: string): boolean {
  try {
    const calculatedSignature = crypto
      .createHmac('sha256', Buffer.from(key, 'base64'))
      .update(encodedData)
      .digest('base64');
    return calculatedSignature === signature;
  } catch (error) {
    console.error('Error verifying signature:', error);
    return false;
  }
}

// ✅ CORREGIDO: Región como opción en onRequest (v2)
export const paymentNotification = onRequest(
  { region: 'europe-west1' },  // ← Opciones con región
  async (req, res) => {
    try {
      const { Ds_Signature, Ds_MerchantParameters, Ds_SignatureVersion } = req.body;

      if (!Ds_Signature || !Ds_MerchantParameters) {
        console.error('❌ Parámetros faltantes en notificación');
        res.status(400).send('Parámetros faltantes');
        return;
      }

      const config = getPaymentConfig('test');

      if (!verifySignature(Ds_MerchantParameters, Ds_Signature, config.SECRET_KEY)) {
        console.error('❌ Firma inválida');
        res.status(400).send('Firma inválida');
        return;
      }

      const decodedData = JSON.parse(Buffer.from(Ds_MerchantParameters, 'base64').toString('utf-8'));
      
      const {
        Ds_Order,
        Ds_Response,
        Ds_Amount,
        Ds_MerchantCode,
      } = decodedData;

      console.log(`📬 Notificación recibida: Order=${Ds_Order}, Response=${Ds_Response}`);

      const isApproved = Ds_Response === '0000';

      const paymentRef = admin.firestore().collection('payments').doc(Ds_Order);
      await paymentRef.update({
        status: isApproved ? 'completed' : 'rejected',
        redsysResponse: Ds_Response,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      if (isApproved) {
        const paymentSnap = await paymentRef.get();
        const paymentData = paymentSnap.data();

        if (paymentData) {
          await admin.firestore()
            .collection('users')
            .doc(paymentData.userId)
            .collection('payments')
            .doc(Ds_Order)
            .set({
              excursionId: paymentData.excursionId,
              excursionTitle: paymentData.excursionTitle,
              amount: paymentData.amount,
              orderId: Ds_Order,
              status: 'completed',
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
            });
        }

        console.log(`✅ Pago completado: ${Ds_Order}`);
      } else {
        console.log(`❌ Pago rechazado: ${Ds_Order}, Response: ${Ds_Response}`);
      }

      res.status(200).send('OK');

    } catch (error: any) {
      console.error('❌ Error procesando notificación:', error);
      res.status(500).send('Error interno');
    }
  }
);  // ← Solo UN paréntesis de cierre