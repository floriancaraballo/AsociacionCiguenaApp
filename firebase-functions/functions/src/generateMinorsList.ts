import * as admin from "firebase-admin";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";
import {randomUUID} from "node:crypto";
import {readFile} from "node:fs/promises";
import {join} from "node:path";
import JSZip from "jszip";

const docxContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const dateFormatter = new Intl.DateTimeFormat("es-ES", {
  dateStyle: "long",
  timeZone: "Europe/Madrid",
});

function escapeWordText(value: string): string {
  return value
    .replace(/[^\u0020-\uD7FF\uE000-\uFFFD\u{10000}-\u{10FFFF}]/gu, " ")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export async function renderParticipantsDocx(
  title: string,
  date: string,
  names: readonly string[]
): Promise<Buffer> {
  const template = await readFile(join(__dirname, "../templates/participants.docx"));
  const archive = await JSZip.loadAsync(template);
  const documentPath = "word/document.xml";
  const documentXml = await archive.file(documentPath)?.async("string");
  if (!documentXml) {
    throw new Error("Falta el contenido de la plantilla de participantes");
  }

  const slots = new Map([
    ["{{EXCURSION_TITLE}}", escapeWordText(title)],
    ["{{EXCURSION_DATE}}", escapeWordText(date)],
  ]);
  const documentWithParticipants = documentXml.replace(
    /<w:p(?:\s[^>]*)?>[\s\S]*?<\/w:p>/g,
    (paragraph) => {
      if (paragraph.includes("{{PARTICIPANT}}")) {
        return names.map((name) =>
          paragraph.replace("{{PARTICIPANT}}", () => escapeWordText(name))
        ).join("");
      }
      return paragraph.replace(
        /\{\{EXCURSION_(?:TITLE|DATE)\}\}/g,
        (slot) => slots.get(slot)!
      );
    }
  );

  archive.file(documentPath, documentWithParticipants);
  return archive.generateAsync({type: "nodebuffer", compression: "DEFLATE"});
}

export const generateMinorsListDocx = onCall({
  region: "europe-west1",
}, async (request) => {
  const userId = request.auth?.uid;
  if (!userId) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión");
  }

  const {excursionId, databaseId} = request.data ?? {};
  if (typeof excursionId !== "string" || !excursionId.trim() || excursionId.includes("/")) {
    throw new HttpsError("invalid-argument", "Excursión no válida");
  }

  const firestore = databaseId === "debug" ? getFirestore("debug") : admin.firestore();
  try {
    const user = await firestore.collection("users").doc(userId).get();
    if (!["admin", "superadmin"].includes(user.get("role"))) {
      throw new HttpsError("permission-denied", "No tienes permiso para exportar participantes");
    }

    const [excursion, authorizations] = await Promise.all([
      firestore.collection("excursions").doc(excursionId).get(),
      firestore.collection("signedAuthorizations")
        .where("excursionId", "==", excursionId)
        .select("status", "minorName", "signedAt")
        .get(),
    ]);
    if (!excursion.exists) {
      throw new HttpsError("not-found", "Excursión no encontrada");
    }

    const participants = authorizations.docs.flatMap((authorization) => {
      const name = authorization.get("minorName");
      if (authorization.get("status") !== "APPROVED" ||
          typeof name !== "string" || !name.trim()) {
        return [];
      }
      const signedAt = authorization.get("signedAt");
      if (!(signedAt instanceof Timestamp)) {
        throw new HttpsError("failed-precondition", "Una autorización aprobada no tiene fecha de firma válida");
      }
      return [{id: authorization.id, name: name.trim(), signedAt}];
    });
    if (participants.length === 0) {
      throw new HttpsError("failed-precondition", "No hay autorizaciones de menores aprobadas para esta excursión");
    }
    participants.sort((first, second) =>
      first.signedAt.seconds - second.signedAt.seconds ||
      first.signedAt.nanoseconds - second.signedAt.nanoseconds ||
      first.id.localeCompare(second.id, "en")
    );

    const title = excursion.get("title");
    const start = excursion.get("date");
    const end = excursion.get("endDate");
    if (typeof title !== "string" || !title.trim() || !(start instanceof Timestamp)) {
      throw new HttpsError("failed-precondition", "La excursión debe tener título y fecha");
    }
    const startDate = dateFormatter.format(start.toDate());
    const endDate = end instanceof Timestamp && end.toMillis() > start.toMillis() ?
      dateFormatter.format(end.toDate()) :
      startDate;
    const date = startDate === endDate ? startDate : `${startDate} – ${endDate}`;
    const buffer = await renderParticipantsDocx(
      title.trim().toLocaleUpperCase("es-ES"),
      date,
      participants.map(({name}) => name)
    );

    const fileName = `lista_${title.replace(/[^a-zA-Z0-9]/g, "_").slice(0, 80)}.docx`;
    const file = admin.storage().bucket().file(`lists/${randomUUID()}.docx`);
    await file.save(buffer, {
      metadata: {contentType: docxContentType, cacheControl: "private, no-store"},
      predefinedAcl: "private",
    });
    const [downloadUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 60 * 60 * 1000,
    });

    return {success: true, downloadUrl, fileName, minorsCount: participants.length};
  } catch (error) {
    if (error instanceof HttpsError) {
      throw error;
    }
    console.error("Error al exportar la lista de participantes", error);
    throw new HttpsError("internal", "No se pudo generar la lista Word");
  }
});
