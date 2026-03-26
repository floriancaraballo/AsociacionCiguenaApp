// ───────── IMPORTS NECESARIOS ─────────
import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Document, Packer, Paragraph, TextRun, AlignmentType } from "docx";

// Inicializar Firebase Admin
admin.initializeApp();

// ───────── FUNCIÓN PRINCIPAL ─────────
export const generateMinorsListDocx = onCall({
  region: "europe-west1",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debes estar autenticado");
  }

  const { excursionId, excursionTitle, excursionDate } = request.data as {
    excursionId: string;
    excursionTitle: string;
    excursionDate: string;
  };

  if (!excursionId) {
    throw new HttpsError("invalid-argument", "Falta excursionId");
  }

  try {
    console.log(`📄 Generando lista Word para: ${excursionTitle}`);

    // 1. Obtener autorizaciones de Firestore (ordenadas por fecha de firma)
    const authorizationsSnapshot = await admin.firestore()
      .collection("signedAuthorizations")
      .where("excursionId", "==", excursionId)
      .orderBy("signedAt", "asc")
      .get();

    const minors = authorizationsSnapshot.docs
      .map((doc: any) => doc.data())
      .filter((auth: any) => auth.minorName)
      .map((auth: any) => ({
        name: auth.minorName as string,
      }));

    console.log(`📋 ${minors.length} participantes encontrados`);

    // 2. Crear documento Word SOLO con la lista numerada
    const doc = new Document({
      sections: [{
        properties: {},
        children: [
          // Lista simple de niños (sin títulos, sin cabeceras)
          ...minors.map((minor, index) => 
            new Paragraph({
              children: [
                new TextRun({
                  text: `${index + 1}.  ${minor.name}`,
                  size: 24,  // 12pt
                  font: "Arial",
                }),
              ],
              spacing: { 
                after: 50,  // Espacio entre líneas
                line: 360,  // Interlineado
              },
            })
          ),
        ],
      }],
    });

    console.log(`📝 Documento creado, generando buffer...`);
    const buffer = await Packer.toBuffer(doc);
    console.log(`✅ Buffer generado: ${buffer.length} bytes`);
    
    // 3. Subir a Firebase Storage
    const fileName = `lists/lista_${excursionTitle.replace(/[^a-zA-Z0-9]/g, "_").slice(0, 50).toLowerCase()}_${Date.now()}.docx`;
    const bucket = admin.storage().bucket();
    const fileRef = bucket.file(fileName);
    
    console.log(`📤 Subiendo a Storage: ${fileName}`);
    
    await fileRef.save(buffer, {
      metadata: { 
        contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        cacheControl: "private, max-age=3600"
      },
      predefinedAcl: "private",
    });
    
    console.log(`✅ Archivo subido, generando URL firmada...`);
    
    // 4. Generar URL firmada
    const [url] = await fileRef.getSignedUrl({
      action: "read",
      expires: Date.now() + 60 * 60 * 1000, // 1 hora
    });
    
    console.log(`✅ Documento generado: ${fileName}`);
    
    return {
      success: true,
      downloadUrl: url,
      fileName: `lista_${excursionTitle.replace(/[^a-zA-Z0-9]/g, "_")}.docx`,
      minorsCount: minors.length,
    };

  } catch (error: any) {
    console.error("❌ Error generando documento:", error);
    throw new HttpsError("internal", `Error al generar el documento Word: ${error.message}`);
  }
});