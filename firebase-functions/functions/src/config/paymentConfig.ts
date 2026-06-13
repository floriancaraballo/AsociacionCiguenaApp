
export interface PaymentConfig {
  MERCHANT_CODE: string;
  TERMINAL: string;
  SECRET_KEY: string;
  MERCHANT_NAME: string;
  URL_TPV: string;
  URL_NOTIFICATION: string;
  CURRENCY: string;
}

const NOTIFICATION_URL =
  "https://europe-west1-asociacion-ciguena-188da.cloudfunctions.net/paymentNotification";

const paymentConfig = {
  test: {
    MERCHANT_CODE: "059515841",

    TERMINAL: "100",
    SECRET_KEY: "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
    MERCHANT_NAME: "Asociacion Ciguena",
    URL_TPV: "https://sis-t.redsys.es:25443/sis/realizarPago",
    URL_NOTIFICATION: NOTIFICATION_URL,
    CURRENCY: "978",
  },
  production: {
    MERCHANT_CODE: process.env.REDSYS_MERCHANT_CODE || "",
    TERMINAL: process.env.REDSYS_TERMINAL || "1",
    SECRET_KEY: process.env.REDSYS_SECRET_KEY || "",
    MERCHANT_NAME: process.env.REDSYS_MERCHANT_NAME || "Asociacion Ciguena",
    URL_TPV: "https://sis.redsys.es/sis/realizarPago",
    URL_NOTIFICATION: NOTIFICATION_URL,
    CURRENCY: "978",
  },
} satisfies Record<"test" | "production", PaymentConfig>;

function selectedEnvironment(): "test" | "production" {
  return process.env.REDSYS_ENV === "production" ? "production" : "test";
}

export function getPaymentConfig(): PaymentConfig {
  const env = selectedEnvironment();
  const config = paymentConfig[env];

  if (!config.MERCHANT_CODE || !config.TERMINAL || !config.SECRET_KEY) {
    throw new Error(`Configuracion Redsys incompleta para entorno ${env}`);
  }

  if (!/^\d{1,9}$/.test(config.MERCHANT_CODE)) {
    throw new Error(`FUC Redsys invalido para entorno ${env}`);
  }

  if (!/^\d{1,3}$/.test(config.TERMINAL)) {
    throw new Error(`Terminal Redsys invalido para entorno ${env}`);
  }

  if (Buffer.from(config.SECRET_KEY, "base64").length !== 24) {
    throw new Error(`Clave SHA-256 Redsys invalida para entorno ${env}`);
  }

  return config;
}
