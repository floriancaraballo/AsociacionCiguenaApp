// functions/src/config/paymentConfig.ts

/**
 * Configuración de Redsys para pagos con TPV Virtual
 */
export const paymentConfig = {
  // 🔹 MODO TEST (Redsys sandbox - datos públicos de prueba)
  test: {
    MERCHANT_CODE: '123456789',                    // FUC de test
    TERMINAL: '1',                                  // Terminal
    SECRET_KEY: 'sq7HjrUOBfKmC576ILgskD5srU870gJ7', // Clave SHA-256 test (pública para sandbox)
    URL_TPV: 'https://sis-t.redsys.es:25443/sis/realizarPago',
    URL_NOTIFICATION: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentNotification',
    URL_OK: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentResult',
    URL_KO: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentResult',
    CURRENCY: '978', // EUR
  },
  // 🔹 MODO PRODUCCIÓN (Cajasur real - rellenar cuando te lo den)
  production: {
    MERCHANT_CODE: '', 
    TERMINAL: '1',
    SECRET_KEY: '', 
    URL_TPV: 'https://sis.redsys.es/sis/realizarPago',
    URL_NOTIFICATION: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentNotification',
    URL_OK: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentResult',
    URL_KO: 'https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentResult',
    CURRENCY: '978',
  }
};

/**
 * ✅ Función helper para obtener config según entorno
 * @param env - 'test' o 'production'
 * @returns Configuración de Redsys para el entorno especificado
 */
export function getPaymentConfig(env: 'test' | 'production' = 'test') {
  return env === 'test' ? paymentConfig.test : paymentConfig.production;
}

/**
 * Tipo para la configuración de pago
 */
export type PaymentConfig = typeof paymentConfig.test;