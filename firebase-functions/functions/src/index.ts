import * as admin from "firebase-admin";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
admin.initializeApp();

interface PendingUserDocument {
  email: string;
  displayName: string;
  role: string;
  createdAt: admin.firestore.Timestamp;
  needsRegistration: boolean;
}

export const onPendingUserCreated = onDocumentCreated({
  document: "pendingUsers/{pendingUserId}",
  region: "europe-southwest1",
}, async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    console.log("No data associated with the event");
    return;
  }

  const pendingUserId = event.params.pendingUserId;
  const userData = snapshot.data() as PendingUserDocument;

  if (!userData.needsRegistration) {
    console.log(`Usuario ${pendingUserId} ya procesado`);
    return;
  }

  try {
    const tempPassword = Math.random().toString(36).slice(-12) + "Aa1!";

    let authUser;
    try {
      authUser = await admin.auth().createUser({
        email: userData.email,
        password: tempPassword,
        displayName: userData.displayName,
        emailVerified: false,
      });

      console.log(`Usuario creado en Auth con UID: ${authUser.uid}`);
    } catch (authError: any) {
      if (authError.code === "auth/email-already-exists") {
        const existingUser = await admin.auth()
          .getUserByEmail(userData.email);
        authUser = existingUser;
        console.log(`Usuario ya existe en Auth con UID: ${authUser.uid}`);
      } else {
        throw authError;
      }
    }

    const userDocData = {
      email: userData.email,
      displayName: userData.displayName,
      role: userData.role,
      createdAt: userData.createdAt,
      photoConsents: [],
      needsRegistration: true,
      invitationSent: false,
    };

    await admin.firestore()
      .collection("users")
      .doc(authUser.uid)
      .set(userDocData);

    console.log(`Documento creado en users/${authUser.uid}`);

    const link = await admin.auth().generatePasswordResetLink(userData.email);

    const emailSubject = "Bienvenido a Asociación Cigüeña";
    const emailBody = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            color: #333;
          }
          .container {
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
          }
          .header {
            background: #1976D2;
            color: white;
            padding: 20px;
            text-align: center;
            border-radius: 10px 10px 0 0;
          }
          .content {
            padding: 30px;
            background: #f9f9f9;
          }
          .button {
            display: inline-block;
            padding: 15px 40px;
            background: #1976D2;
            color: white !important;
            text-decoration: none;
            border-radius: 5px;
            margin: 20px 0;
            font-weight: bold;
          }
          .footer {
            padding: 20px;
            text-align: center;
            font-size: 12px;
            color: #666;
            background: #e9e9e9;
            border-radius: 0 0 10px 10px;
          }
          .info-box {
            background: #fff;
            padding: 15px;
            border-left: 4px solid #1976D2;
            margin: 20px 0;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>Asociación Cigüeña</h1>
          </div>
          <div class="content">
            <h2>Bienvenido/a, ${userData.displayName}</h2>
            <p>Has sido invitado/a a la Asociación Cigüeña.</p>
            <div class="info-box">
              <strong>Email:</strong> ${userData.email}<br>
              <strong>Rol:</strong> ${userData.role === "admin" ?
    "Administrador" : "Socio"}
            </div>
            <p>Para establecer tu contraseña, haz click aquí:</p>
            <center>
              <a href="${link}" class="button">Establecer Contraseña</a>
            </center>
            <p style="margin-top: 30px; font-size: 14px; color: #666;">
              Si el botón no funciona, copia este enlace:
            </p>
            <div style="background:#fff;padding:10px;border:1px solid #ddd;">
              <code style="font-size:12px;">${link}</code>
            </div>
            <div class="info-box" style="margin-top:30px;">
              <strong>Importante:</strong><br>
              Este enlace expira en 24 horas
            </div>
          </div>
          <div class="footer">
            <p><strong>Asociación Cigüeña</strong> 2026</p>
            <p>Email automático, no respondas.</p>
          </div>
        </div>
      </body>
      </html>
    `;

    await admin.firestore().collection("mail").add({
      to: userData.email,
      message: {
        subject: emailSubject,
        html: emailBody,
      },
    });

    await admin.firestore()
      .collection("users")
      .doc(authUser.uid)
      .update({
        invitationSent: true,
        invitationSentAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    await snapshot.ref.delete();

    console.log(`Email enviado y usuario migrado correctamente`);

  } catch (error) {
    console.error("Error:", error);

    const errorMessage = error instanceof Error ?
      error.message : "Error desconocido";

    await snapshot.ref.update({
      invitationError: errorMessage,
      invitationSent: false,
    });

    throw error;
  }
});

export const resendInvitation = onCall({
  region: "europe-west1",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debes estar autenticado");
  }

  const callerUid = request.auth.uid;
  const callerDoc = await admin.firestore()
    .collection("users")
    .doc(callerUid)
    .get();

  const callerRole = callerDoc.data()?.role;
  if (callerRole !== "admin" && callerRole !== "superadmin") {
    throw new HttpsError(
      "permission-denied",
      "Solo administradores pueden reenviar invitaciones"
    );
  }

  const userId = request.data.userId;

  const userDoc = await admin.firestore()
    .collection("users")
    .doc(userId)
    .get();

  if (!userDoc.exists) {
    throw new HttpsError("not-found", "Usuario no encontrado");
  }

  await userDoc.ref.update({
    needsRegistration: true,
    invitationSent: false,
  });

  return {success: true, message: "Invitación reenviada"};
});

/**
 * ========================================
 * NOTIFICACIONES PUSH
 * ========================================
 */

/**
 * Enviar notificación cuando se crea una noticia
 */
export const onNewsCreated = onDocumentCreated({
  document: "news/{newsId}",
  region: "europe-west1",
}, async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    console.log("No data associated with the event");
    return;
  }

  const newsId = event.params.newsId;
  const newsData = snapshot.data();

  try {
    const title = newsData.title || "Nueva publicación";

    console.log(`📰 Nueva noticia creada: ${title}`);

    // Obtener tokens de dispositivos (incluye usuarios no logueados)
    const deviceTokensSnapshot = await admin.firestore()
      .collection("deviceTokens")
      .get();

    console.log(`📱 Total dispositivos registrados: ${deviceTokensSnapshot.size}`);

    const tokens: string[] = [];
    deviceTokensSnapshot.docs.forEach((doc) => {
      const deviceData = doc.data();
      const token = deviceData.token;
      
      if (token && typeof token === 'string' && token.length > 0) {
        tokens.push(token);
      }
    });

    console.log(`📧 Tokens válidos encontrados: ${tokens.length}`);

    if (tokens.length === 0) {
      console.log("❌ No hay tokens para notificar");
      return;
    }

    // Enviar notificación
    const message = {
      data: {
	  type: "news",
        itemId: newsId,
        title: "📰 Nueva publicación",
        body: `${title}`,
      },
      android: {
        priority: "high" as const,
      },
      tokens: tokens,
    };

    console.log(`📤 Enviando notificaciones a ${tokens.length} dispositivos...`);

    const response = await admin.messaging().sendEachForMulticast(message);
    
    console.log(`✅ Enviadas: ${response.successCount} exitosas, ${response.failureCount} fallidas`);

    // Limpiar tokens inválidos
    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          console.log(`❌ Token fallido: ${tokens[idx].substring(0, 20)}...`);
          tokensToRemove.push(tokens[idx]);
        }
      });

      // Eliminar tokens inválidos de deviceTokens
      const batch = admin.firestore().batch();
      for (const token of tokensToRemove) {
        batch.delete(admin.firestore().collection("deviceTokens").doc(token));
        console.log(`🧹 Eliminando token inválido: ${token.substring(0, 20)}...`);
      }
      await batch.commit();
    }
  } catch (error) {
    console.error("❌ Error al enviar notificaciones:", error);
  }
});

export const onExcursionCreated = onDocumentCreated({
  document: "excursions/{excursionId}",
  region: "europe-west1",
}, async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    console.log("No data associated with the event");
    return;
  }

  const excursionId = event.params.excursionId;
  const excursionData = snapshot.data();

  try {
    const title = excursionData.title || "Nueva excursión";
    const date = excursionData.date;

    console.log(`🏔️ Nueva excursión creada: ${title}`);

    // Formatear fecha
    let dateStr = "";
    if (date) {
      const d = date.toDate();
      dateStr = `${d.getDate()}/${d.getMonth() + 1}/${d.getFullYear()}`;
    }

    // Obtener tokens de dispositivos
    const deviceTokensSnapshot = await admin.firestore()
      .collection("deviceTokens")
      .get();

    console.log(`📱 Total dispositivos registrados: ${deviceTokensSnapshot.size}`);

    const tokens: string[] = [];
    deviceTokensSnapshot.docs.forEach((doc) => {
      const deviceData = doc.data();
      const token = deviceData.token;
      
      if (token && typeof token === 'string' && token.length > 0) {
        tokens.push(token);
      }
    });

    console.log(`📧 Tokens válidos encontrados: ${tokens.length}`);

    if (tokens.length === 0) {
      console.log("❌ No hay tokens para notificar");
      return;
    }

    // Crear mensaje
    const message = {
      data: {
		type: "excursion",
        itemId: excursionId,
        title: "Nueva Excursión Programada",
        body: dateStr ? `${title} - ${dateStr}` : `${title}`,
      },
      android: {
        priority: "high" as const,
      },
      tokens: tokens,
    };

    console.log(`📤 Enviando notificaciones a ${tokens.length} dispositivos...`);

    // Enviar notificación
    const response = await admin.messaging().sendEachForMulticast(message);
    
    console.log(`✅ Enviadas: ${response.successCount} exitosas, ${response.failureCount} fallidas`);

    // Limpiar tokens inválidos
    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          console.log(`❌ Token fallido: ${tokens[idx].substring(0, 20)}...`);
          tokensToRemove.push(tokens[idx]);
        }
      });

      const batch = admin.firestore().batch();
      for (const token of tokensToRemove) {
        batch.delete(admin.firestore().collection("deviceTokens").doc(token));
        console.log(`🧹 Eliminando token inválido: ${token.substring(0, 20)}...`);
      }
      await batch.commit();
    }
  } catch (error) {
    console.error("❌ Error al enviar notificaciones:", error);
  }
});

// ==========================================
// NOTIFICACIÓN AL COMPLETAR BATCH DE FOTOS
// ==========================================

export const onUploadBatchCompleted = onDocumentUpdated(
  {
    document: "uploadBatches/{batchId}",
    region: "europe-west1"
  },
  async (event) => {
    try {
      const beforeData = event.data?.before.data();
      const afterData = event.data?.after.data();

      if (!afterData) return;

      // Solo actuar cuando cambia de "uploading" a "completed"
      if (beforeData?.status === "uploading" && afterData.status === "completed") {
        const excursionId = afterData.excursionId as string;
        const photoCount = afterData.photoCount as number;
        const authorizedUsers = afterData.authorizedUsers as string[] || [];

        if (!excursionId) {
          console.log("❌ No excursionId");
          return;
        }

        console.log(`📸 Upload batch completed: ${photoCount} photos for excursion ${excursionId}`);

        // Obtener título de la excursión
        const excursionDoc = await admin.firestore()
          .collection("excursions")
          .doc(excursionId)
          .get();

        const excursionTitle = excursionDoc.data()?.title || "una excursión";

        // Si NO hay usuarios autorizados, enviar a TODOS
        let tokens: string[] = [];

        if (authorizedUsers.length === 0) {
          console.log("📢 No hay usuarios autorizados específicos, enviando a TODOS");
          
          const deviceTokensSnapshot = await admin.firestore()
            .collection("deviceTokens")
            .get();

          deviceTokensSnapshot.forEach((doc) => {
            const deviceData = doc.data();
            const token = deviceData.token;
            if (token && typeof token === 'string' && token.length > 0) {
              tokens.push(token);
            }
          });
        } else {
          // Obtener tokens de usuarios autorizados (en lotes de 10)
          console.log(`👥 Usuarios autorizados: ${authorizedUsers.length}`);

          for (let i = 0; i < authorizedUsers.length; i += 10) {
            const batch = authorizedUsers.slice(i, i + 10);

            const usersSnapshot = await admin.firestore()
              .collection("users")
              .where(admin.firestore.FieldPath.documentId(), "in", batch)
              .get();

            // Obtener tokens de deviceTokens por userId
            for (const userDoc of usersSnapshot.docs) {
              const deviceTokensQuery = await admin.firestore()
                .collection("deviceTokens")
                .where("userId", "==", userDoc.id)
                .get();

              deviceTokensQuery.forEach((deviceDoc) => {
                const token = deviceDoc.data().token;
                if (token && typeof token === 'string' && token.length > 0) {
                  tokens.push(token);
                }
              });
            }
          }
        }

        console.log(`📧 Tokens encontrados: ${tokens.length}`);

        if (tokens.length === 0) {
          console.log("✅ No FCM tokens found");
          return;
        }

        // Mensaje según el número de fotos
        const body = photoCount === 1
          ? `Nueva foto de "${excursionTitle}" disponible en la galería`
          : `${photoCount} nuevas fotos de "${excursionTitle}" disponibles en la galería`;

        // Enviar notificación
        const response = await admin.messaging().sendEachForMulticast({
          tokens: tokens,
          data: {
			type: "photo",
            itemId: excursionId,
            title: "📸 ¡Fotos Nuevas Disponibles!",
            body: body,
          },
          android: {
            priority: "high" as const,
          },
        });

        console.log(`✅ Sent notification: ${response.successCount} success, ${response.failureCount} failed`);

        // Limpiar tokens inválidos
        if (response.failureCount > 0) {
          const invalidTokens: string[] = [];
          response.responses.forEach((resp, idx) => {
            if (!resp.success) {
              invalidTokens.push(tokens[idx]);
            }
          });

          if (invalidTokens.length > 0) {
            const batch = admin.firestore().batch();
            for (const token of invalidTokens.slice(0, 10)) {
              batch.delete(admin.firestore().collection("deviceTokens").doc(token));
            }
            await batch.commit();
            console.log(`🧹 Cleaned ${Math.min(invalidTokens.length, 10)} invalid tokens`);
          }
        }
      }
    } catch (error) {
      console.error("❌ Error in onUploadBatchCompleted:", error);
    }
  }
);

// ==========================================
// ENVÍO DE EMAIL AL FIRMAR AUTORIZACIÓN (FINAL)
// ==========================================

export const onAuthorizationSigned = onDocumentCreated(
  {
    document: "signedAuthorizations/{authorizationId}",
    region: "europe-west1"
  },
  async (event) => {
    try {
      const snapshot = event.data;
      if (!snapshot) return;
      
      const authData = snapshot.data();
      const authorizationId = event.params.authorizationId;
      
      const excursionTitle = authData.excursionTitle as string;
      const tutorName = authData.tutorName as string;
      const tutorEmail = authData.tutorEmail as string;
      const tutorPhone = authData.tutorPhone as string;
      const tutorDni = authData.tutorDni as string;
      const minorName = authData.minorName as string | undefined;
      const signedPdfUrl = authData.signedPdfUrl as string;
      
      console.log(`📝 Nueva autorización: ${authorizationId}`);
      
      // Preparar adjunto (usando path con URL HTTPS - funciona con Trigger Email)
      const pdfAttachment = {
        filename: `autorizacion_${excursionTitle.replace(/[^a-z0-9]/gi, '_').slice(0, 50)}.pdf`,
        path: signedPdfUrl,  // ← URL HTTPS con token, NO gs://
        contentType: 'application/pdf'
      };
      
      // ───────── EMAIL AL USUARIO (TUTOR) ─────────
      const userEmailSubject = `✅ Autorización firmada - ${excursionTitle}`;
      const userEmailBody = `
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8">
          <style>
            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background: #1976D2; color: white; padding: 20px; text-align: center; border-radius: 10px 10px 0 0; }
            .content { padding: 30px; background: #f9f9f9; }
            .button { display: inline-block; padding: 15px 40px; background: #1976D2; color: white !important; text-decoration: none; border-radius: 5px; margin: 20px 0; font-weight: bold; }
            .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; background: #e9e9e9; border-radius: 0 0 10px 10px; }
            .info-box { background: #fff; padding: 15px; border-left: 4px solid #4CAF50; margin: 20px 0; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header"><h1>Asociación Cigüeña</h1></div>
            <div class="content">
              <h2>¡Autorización Firmada Correctamente!</h2>
              <p>Hola ${tutorName},</p>
              <p>Has firmado correctamente la autorización para:</p>
              <div class="info-box">
                <strong>📍 Excursión:</strong> ${excursionTitle}<br>
                ${minorName ? `<strong>👤 Participante:</strong> ${minorName}<br>` : ''}
                <strong>✅ Estado:</strong> Firmada y registrada
              </div>
              <p>📎 <strong>PDF firmado adjunto</strong> a este correo.</p>
              <p>También puedes descargarlo desde este enlace:</p>
              <center><a href="${signedPdfUrl}" class="button">Descargar Autorización</a></center>
              <p style="margin-top: 30px; font-size: 14px; color: #666;">
                Si no solicitaste esta autorización, contacta con nosotros inmediatamente.
              </p>
            </div>
            <div class="footer">
              <p><strong>Asociación Cigüeña</strong> 2026</p>
              <p>Email automático, no respondas.</p>
            </div>
          </div>
        </body>
        </html>
      `;
      
      // Enviar email al tutor CON adjunto
      await admin.firestore().collection("mail").add({
        to: tutorEmail,  // ← Email del tutor
        message: {
          subject: userEmailSubject,
          html: userEmailBody,
          attachments: [pdfAttachment]  // ← Adjunto con path HTTPS
        },
      });
      console.log(`✅ Email enviado a tutor: ${tutorEmail}`);
      
      // ───────── EMAIL A LA ASOCIACIÓN (CORREGIDO) ─────────
      const associationEmail = "asociacionciguena1993@gmail.com"; // ← Configura aquí
      
      const adminEmailSubject = `📝 Nueva autorización firmada - ${excursionTitle}`;
      const adminEmailBody = `
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8">
          <style>
            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background: #1976D2; color: white; padding: 20px; text-align: center; }
            .content { padding: 30px; background: #f9f9f9; }
            .info-box { background: #fff; padding: 15px; border-left: 4px solid #1976D2; margin: 20px 0; }
            .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; background: #e9e9e9; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header"><h1>Asociación Cigüeña</h1></div>
            <div class="content">
              <h2>📋 Nueva Autorización Recibida</h2>
              <div class="info-box">
                <strong>👤 Tutor:</strong> ${tutorName}<br>
                <strong>📧 Email:</strong> ${tutorEmail}<br>
                <strong>📞 Teléfono:</strong> ${tutorPhone}<br>
                <strong>🆔 DNI:</strong> ${tutorDni}<br>
                ${minorName ? `<strong>👶 Menor:</strong> ${minorName}<br>` : ''}
                <strong>📍 Excursión:</strong> ${excursionTitle}<br>
                <strong>📅 Firmado:</strong> ${new Date().toLocaleString('es-ES')}
              </div>
              <p>📎 <strong>PDF firmado adjunto</strong></p>
              <p>Enlace de descarga: <a href="${signedPdfUrl}">${signedPdfUrl}</a></p>
            </div>
            <div class="footer">
              <p>Generado automáticamente por el sistema de autorizaciones</p>
            </div>
          </div>
        </body>
        </html>
      `;
      
      // ✅ CORREGIDO: Enviar a associationEmail con subject/body correctos
      await admin.firestore().collection("mail").add({
        to: associationEmail,  // ← ✅ associationEmail (NO tutorEmail)
        message: {
          subject: adminEmailSubject,  // ← ✅ adminEmailSubject
          html: adminEmailBody,        // ← ✅ adminEmailBody
          attachments: [pdfAttachment] // ← Adjunto con path HTTPS
        },
      });
      console.log(`✅ Email enviado a asociación: ${associationEmail}`);
      
      // Marcar como enviado
      await snapshot.ref.update({
        emailSent: true,
        emailSentAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      
    } catch (error) {
      console.error("❌ Error en onAuthorizationSigned:", error);
    }
  }
);