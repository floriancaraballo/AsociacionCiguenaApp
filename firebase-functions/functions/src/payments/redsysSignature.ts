import * as crypto from "crypto";

function zeroPad(input: Buffer, blockSize: number): Buffer {
  const remainder = input.length % blockSize;
  if (remainder === 0) {
    return input;
  }

  return Buffer.concat([input, Buffer.alloc(blockSize - remainder, 0)]);
}

function createOperationKey(order: string, merchantKeyBase64: string): Buffer {
  const merchantKey = Buffer.from(merchantKeyBase64, "base64");
  const cipher = crypto.createCipheriv(
    "des-ede3-cbc",
    merchantKey,
    Buffer.alloc(8, 0)
  );

  cipher.setAutoPadding(false);

  return Buffer.concat([
    cipher.update(zeroPad(Buffer.from(order, "utf8"), 8)),
    cipher.final(),
  ]);
}

export function createRedsysSignature(
  merchantParametersBase64: string,
  order: string,
  merchantKeyBase64: string
): string {
  const operationKey = createOperationKey(order, merchantKeyBase64);

  return crypto
    .createHmac("sha256", operationKey)
    .update(merchantParametersBase64)
    .digest("base64");
}

export function timingSafeSignatureEquals(
  expectedSignature: string,
  receivedSignature: string
): boolean {
  const expected = Buffer.from(normalizeBase64(expectedSignature), "base64");
  const received = Buffer.from(normalizeBase64(receivedSignature), "base64");

  return (
    expected.length === received.length &&
    crypto.timingSafeEqual(expected, received)
  );
}

function normalizeBase64(value: string): string {
  const normalized = value.trim().replace(/ /g, "+").replace(/-/g, "+")
    .replace(/_/g, "/");
  const paddingLength = (4 - (normalized.length % 4)) % 4;

  return normalized + "=".repeat(paddingLength);
}
