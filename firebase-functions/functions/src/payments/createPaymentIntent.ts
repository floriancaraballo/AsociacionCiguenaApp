// functions/src/payments/createPaymentIntent.ts

import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import { onCall, HttpsError, CallableRequest } from 'firebase-functions/v2/https';
import { getPaymentConfig } from '../config/paymentConfig';

admin.initializeApp();

function createSignature(data: string, key: string): string {
  const hmac = crypto.createHmac('sha256', Buffer.from(key, 'base64'));
  hmac.update(data);
  return hmac.digest('base64');
}

function encodeBase64(data: object): string {
  return Buffer.from(JSON.stringify(data)).toString('base64');
}

interface PaymentIntentData {
  excursionId: string;
  amount: number;
  userName: string;
  userEmail: string;
}

export const createPaymentIntent = onCall(
  { region: 'europe-west1' },
  async (request: CallableRequest<PaymentIntentData>) => {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'Debes estar logueado');
    }

    const { excursionId, amount, userName, userEmail } = request.data;

    if (!excursionId || !amount || amount <= 0) {
      throw new HttpsError('invalid-argument', 'Datos de pago inválidos');
    }

    try {
      const excursionRef = admin.firestore().collection('excursions').doc(excursionId);
      const excursionSnap = await excursionRef.get();

      if (!excursionSnap.exists) {
        throw new HttpsError('not-found', 'Excursión no encontrada');
      }

      const excursion = excursionSnap.data();
      const expectedAmount = excursion?.price || amount;
      
      if (Math.abs(expectedAmount - amount) > 0.01) {
        throw new HttpsError('invalid-argument', 'El monto no coincide');
      }

      // ✅ OBTENER CONFIGURACIÓN
      const config = getPaymentConfig('test');
      
	  // 🔍 LOG PARA DEBUG: Verificar clave secreta
	  console.log('🔍 [SECRET KEY DEBUG] Length:', config.SECRET_KEY.length);
      console.log('🔍 [SECRET KEY DEBUG] First 10 chars:', config.SECRET_KEY.substring(0, 10));
      console.log('🔍 [SECRET KEY DEBUG] Last 10 chars:', config.SECRET_KEY.substring(22));
	  
      // 🔍 LOGS DE DEBUG - VERIFICAR CONFIG
      console.log('🔍 [DEBUG 1/4] CONFIG OBTENIDA:', {
        MERCHANT_CODE: config.MERCHANT_CODE,
        TERMINAL: config.TERMINAL,
        SECRET_KEY_LENGTH: config.SECRET_KEY.length,
        URL_TPV: config.URL_TPV,
      });

      const orderId = `${Date.now()}_${request.auth.uid}`.substring(0, 12);

      // ✅ CONSTRUIR DATOS PARA REDSYS
      const paymentData: Record<string, string> = {
        DS_MERCHANT_AMOUNT: Math.round(amount * 100).toString(),
        DS_MERCHANT_ORDER: orderId,
        DS_MERCHANT_MERCHANTCODE: config.MERCHANT_CODE,
        DS_MERCHANT_CURRENCY: config.CURRENCY,
        DS_MERCHANT_TRANSACTIONTYPE: '0',
        DS_MERCHANT_TERMINAL: config.TERMINAL,
        DS_MERCHANT_URL: config.URL_NOTIFICATION,
        DS_MERCHANT_PRODUCTDESCRIPTION: excursion?.title || 'Excursión Asociación Cigüeña',
        DS_MERCHANT_TITULAR: userName || 'Usuario',
        DS_MERCHANT_MERCHANTURL: config.URL_OK,
        DS_MERCHANT_URLOK: config.URL_OK,
        DS_MERCHANT_URLKO: config.URL_KO,
        DS_MERCHANT_CONSUMERLANGUAGE: '1',
        DS_MERCHANT_SUMTOTAL: Math.round(amount * 100).toString(),
        DS_MERCHANT_DIRECTPAYMENT: 'false',
      };

      // 🔍 LOGS DE DEBUG - VERIFICAR DATOS ANTES DE CODIFICAR
      console.log('🔍 [DEBUG 2/4] DATOS PARA REDSYS:', {
        AMOUNT: paymentData.DS_MERCHANT_AMOUNT,
        ORDER: paymentData.DS_MERCHANT_ORDER,
        MERCHANTCODE: paymentData.DS_MERCHANT_MERCHANTCODE,
        TERMINAL: paymentData.DS_MERCHANT_TERMINAL,
        CURRENCY: paymentData.DS_MERCHANT_CURRENCY,
      });

      // ✅ CODIFICAR Y FIRMAR
      const encodedData = encodeBase64(paymentData);
      const signature = createSignature(encodedData, config.SECRET_KEY);

      // 🔍 LOGS DE DEBUG - VERIFICAR DATOS CODIFICADOS
      console.log('🔍 [DEBUG 3/4] DATOS CODIFICADOS:', {
        Ds_MerchantParameters: encodedData,
        Ds_Signature: signature.substring(0, 20) + '...',
        Ds_SignatureVersion: 'HMAC_SHA256_V1',
      });

      // 🔍 DECODIFICAR PARA VERIFICAR (opcional, para debug)
      const decodedCheck = Buffer.from(encodedData, 'base64').toString('utf-8');
      console.log('🔍 [DEBUG 4/4] DATOS DECODIFICADOS (verificación):', decodedCheck);

      // ✅ GUARDAR EN FIRESTORE
      await admin.firestore().collection('payments').doc(orderId).set({
        excursionId,
        userId: request.auth.uid,
        amount,
        orderId,
        status: 'pending',
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        userName,
        userEmail,
        excursionTitle: excursion?.title,
        paymentMethod: 'redsys',
      });

      console.log(`✅ Payment intent created: ${orderId} for ${amount}€`);

      // ✅ DEVOLVER PARÁMETROS
      return {
        success: true,
        orderId,
        tpvUrl: config.URL_TPV,
        params: {
          Ds_SignatureVersion: 'HMAC_SHA256_V1',
          Ds_MerchantParameters: encodedData,
          Ds_Signature: signature,
        },
      };

    } catch (error: any) {
      console.error('❌ Error creating payment intent:', error);
      if (error instanceof HttpsError) {
        throw error;
      }
      throw new HttpsError('internal', error.message || 'Error al crear el pago');
    }
  }
);