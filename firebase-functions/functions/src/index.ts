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
  region: "europe-west1",
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
    const isPublic = newsData.isPublic || false;

    // Obtener tokens de usuarios
    let usersSnapshot;
    if (isPublic) {
      // Noticia pública: notificar a TODOS los usuarios
      usersSnapshot = await admin.firestore()
        .collection("users")
        .where("fcmToken", "!=", null)
        .get();
    } else {
      // Noticia privada: solo notificar a usuarios autenticados (socios, admins)
      usersSnapshot = await admin.firestore()
        .collection("users")
        .where("fcmToken", "!=", null)
        .get();
    }

    const tokens: string[] = [];
    usersSnapshot.docs.forEach((doc) => {
      const token = doc.data().fcmToken;
      if (token) {
        tokens.push(token);
      }
    });

    if (tokens.length === 0) {
      console.log("No hay tokens para notificar");
      return;
    }

    // Crear mensaje de notificación
    const message = {
      notification: {
        title: "📰 Nueva noticia",
        body: title,
      },
      data: {
        type: "news",
        itemId: newsId,
      },
      tokens: tokens,
    };

    // Enviar notificación
    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(`Notificaciones enviadas: ${response.successCount} exitosas, ${response.failureCount} fallidas`);

    // Limpiar tokens inválidos
    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          tokensToRemove.push(tokens[idx]);
        }
      });

      // Eliminar tokens inválidos de Firestore
      const batch = admin.firestore().batch();
      for (const token of tokensToRemove) {
        const userQuery = await admin.firestore()
          .collection("users")
          .where("fcmToken", "==", token)
          .limit(1)
          .get();

        if (!userQuery.empty) {
          batch.update(userQuery.docs[0].ref, {fcmToken: admin.firestore.FieldValue.delete()});
        }
      }
      await batch.commit();
    }
  } catch (error) {
    console.error("Error al enviar notificaciones:", error);
  }
});

/**
 * Enviar notificación cuando se crea una excursión
 */
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

    // Formatear fecha
    let dateStr = "";
    if (date) {
      const d = date.toDate();
      dateStr = `${d.getDate()}/${d.getMonth() + 1}/${d.getFullYear()}`;
    }

    // Obtener tokens de TODOS los usuarios autenticados
    const usersSnapshot = await admin.firestore()
      .collection("users")
      .where("fcmToken", "!=", null)
      .get();

    const tokens: string[] = [];
    usersSnapshot.docs.forEach((doc) => {
      const token = doc.data().fcmToken;
      if (token) {
        tokens.push(token);
      }
    });

    if (tokens.length === 0) {
      console.log("No hay tokens para notificar");
      return;
    }

    // Crear mensaje
    const message = {
      notification: {
        title: "📅 Nueva excursión",
        body: dateStr ? `${title} - ${dateStr}` : title,
      },
      data: {
        type: "excursion",
        itemId: excursionId,
      },
      tokens: tokens,
    };

    // Enviar notificación
    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(`Notificaciones enviadas: ${response.successCount} exitosas`);

    // Limpiar tokens inválidos (mismo código que antes)
    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          tokensToRemove.push(tokens[idx]);
        }
      });

      const batch = admin.firestore().batch();
      for (const token of tokensToRemove) {
        const userQuery = await admin.firestore()
          .collection("users")
          .where("fcmToken", "==", token)
          .limit(1)
          .get();

        if (!userQuery.empty) {
          batch.update(userQuery.docs[0].ref, {fcmToken: admin.firestore.FieldValue.delete()});
        }
      }
      await batch.commit();
    }
  } catch (error) {
    console.error("Error al enviar notificaciones:", error);
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

                if (!excursionId || authorizedUsers.length === 0) {
                    console.log("No excursionId or authorizedUsers");
                    return;
                }

                console.log(`📸 Upload batch completed: ${photoCount} photos for excursion ${excursionId}`);

                // Obtener título de la excursión
                const excursionDoc = await admin.firestore()
                    .collection("excursions")
                    .doc(excursionId)
                    .get();

                const excursionTitle = excursionDoc.data()?.title || "una excursión";

                // Obtener tokens de usuarios autorizados (en lotes de 10)
                const tokens: string[] = [];

                for (let i = 0; i < authorizedUsers.length; i += 10) {
                    const batch = authorizedUsers.slice(i, i + 10);

                    const usersSnapshot = await admin.firestore()
                        .collection("users")
                        .where(admin.firestore.FieldPath.documentId(), "in", batch)
                        .get();

                    usersSnapshot.forEach((doc) => {
                        const userToken = doc.data().fcmToken;
                        if (userToken) {
                            tokens.push(userToken as string);
                        }
                    });
                }

                if (tokens.length === 0) {
                    console.log("✅ No FCM tokens found for authorized users");
                    return;
                }

                // Mensaje según el número de fotos
                const body = photoCount === 1
                    ? `Nueva foto de ${excursionTitle}`
                    : `${photoCount} nuevas fotos de ${excursionTitle}`;

                // Enviar notificación
                const response = await admin.messaging().sendEachForMulticast({
                    tokens: tokens,
                    notification: {
                        title: "📸 Nuevas fotos",
                        body: body
                    },
                    data: {
                        type: "photo",
                        itemId: excursionId
                    }
                });

                console.log(`✅ Sent notification for ${photoCount} photos: ${response.successCount} success, ${response.failureCount} failed`);

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
                        const usersToClean = await admin.firestore()
                            .collection("users")
                            .where("fcmToken", "in", invalidTokens.slice(0, 10))
                            .get();

                        usersToClean.forEach((doc) => {
                            batch.update(doc.ref, { fcmToken: null });
                        });

                        await batch.commit();
                        console.log(`🧹 Cleaned ${usersToClean.size} invalid tokens`);
                    }
                }
            }

        } catch (error) {
            console.error("❌ Error in onUploadBatchCompleted:", error);
        }
    }
);