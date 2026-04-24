import * as admin from "firebase-admin";
import {randomBytes} from "node:crypto";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { generateMinorsListDocx } from "./generateMinorsList";

admin.initializeApp();

// ✅ IMPORTAR funciones de pago
import { createPaymentIntent } from './payments/createPaymentIntent';
import { paymentNotification } from './payments/paymentNotification';

// ✅ EXPORTAR funciones de pago (nombres exactos para deploy)
export { createPaymentIntent, paymentNotification };

interface PendingUserDocument {
  email: string;
  displayName: string;
  role: string;
  createdAt: admin.firestore.Timestamp;
  needsRegistration: boolean;
}

interface InvitationUserData {
  email: string;
  displayName: string;
  role: string;
  createdAt?: admin.firestore.Timestamp;
}

const HOSTING_BASE_URL = "https://asociacion-ciguena-188da.web.app";
const CUSTOM_AUTH_ACTION_URL = `${HOSTING_BASE_URL}/auth/action`;

function generateTemporaryPassword(): string {
  return `${randomBytes(18).toString("base64url")}Aa1!`;
}

function buildCustomAuthActionLink(rawLink: string): string {
  try {
    const parsedUrl = new URL(rawLink);
    const customUrl = new URL(CUSTOM_AUTH_ACTION_URL);

    customUrl.search = parsedUrl.search;
    return customUrl.toString();
  } catch (error) {
    console.error("No se pudo transformar el enlace de acción:", error);
    return rawLink;
  }
}

function buildInvitationEmail(userData: InvitationUserData, actionLink: string) {
  const roleLabel = userData.role === "admin" ? "Administrador" : "Socio";
  const emailSubject = "Configura tu acceso a Asociación Cigüeña";
  const emailText = [
    `Hola ${userData.displayName},`,
    "",
    "Ya tienes acceso a Asociación Cigüeña.",
    `Email: ${userData.email}`,
    `Rol: ${roleLabel}`,
    "",
    "Para crear tu contraseña de acceso, abre este enlace seguro:",
    actionLink,
    "",
    "Si el enlace ha caducado, solicita uno nuevo al equipo administrador.",
    "",
    "Este es un correo transaccional automático. No es necesario responderlo.",
  ].join("\n");

  const emailHtml = `
    <!DOCTYPE html>
    <html lang="es">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Configura tu acceso</title>
      <style>
        body {
          margin: 0;
          padding: 0;
          background: #f4f7fb;
          color: #16324f;
          font-family: Arial, sans-serif;
        }
        .wrapper {
          width: 100%;
          padding: 24px 12px;
          box-sizing: border-box;
        }
        .card {
          max-width: 560px;
          margin: 0 auto;
          background: #ffffff;
          border: 1px solid #d7e2ee;
          border-radius: 20px;
          overflow: hidden;
          box-shadow: 0 16px 40px rgba(22, 50, 79, 0.08);
        }
        .hero {
          background: linear-gradient(135deg, #0f4c81 0%, #1976d2 100%);
          color: #ffffff;
          padding: 32px 32px 24px;
        }
        .hero h1 {
          margin: 0 0 8px;
          font-size: 26px;
          line-height: 1.2;
        }
        .hero p {
          margin: 0;
          font-size: 15px;
          line-height: 1.6;
          opacity: 0.94;
        }
        .content {
          padding: 32px;
        }
        .content h2 {
          margin: 0 0 12px;
          font-size: 22px;
          line-height: 1.3;
        }
        .content p {
          margin: 0 0 16px;
          font-size: 15px;
          line-height: 1.7;
          color: #35506b;
        }
        .details {
          background: #f7fafd;
          border: 1px solid #dce7f3;
          border-radius: 14px;
          padding: 16px 18px;
          margin: 24px 0;
        }
        .details strong {
          color: #16324f;
        }
        .button {
          display: inline-block;
          padding: 14px 24px;
          background: #1976d2;
          color: #ffffff !important;
          text-decoration: none;
          border-radius: 999px;
          font-size: 15px;
          font-weight: bold;
        }
        .support {
          margin-top: 24px;
          font-size: 13px;
          color: #5d7288;
        }
        .footer {
          padding: 0 32px 28px;
          font-size: 12px;
          line-height: 1.6;
          color: #7b8da1;
        }
      </style>
    </head>
    <body>
      <div class="wrapper">
        <div class="card">
          <div class="hero">
            <h1>Asociación Cigüeña</h1>
            <p>Acceso seguro a tu cuenta</p>
          </div>
          <div class="content">
            <h2>Hola ${userData.displayName}</h2>
            <p>Tu cuenta ya está preparada. Solo falta que crees una contraseña para poder acceder.</p>
            <div class="details">
              <strong>Email:</strong> ${userData.email}<br>
              <strong>Rol:</strong> ${roleLabel}
            </div>
            <p>Usa este enlace seguro para crear tu contraseña:</p>
            <p>
              <a href="${actionLink}" class="button">Crear contraseña</a>
            </p>
            <p class="support">
              Si el enlace ha caducado, solicita uno nuevo al equipo administrador.
            </p>
          </div>
          <div class="footer">
            Este es un correo transaccional automático de Asociación Cigüeña. No es necesario responderlo.
          </div>
        </div>
      </div>
    </body>
    </html>
  `;

  return {
    emailSubject,
    emailText,
    emailHtml,
  };
}

function buildPasswordResetEmail(email: string, actionLink: string) {
  const emailSubject = "Restablece tu contraseña de Asociación Cigüeña";
  const emailText = [
    "Hola,",
    "",
    "Hemos recibido una solicitud para restablecer la contraseña de tu cuenta.",
    "",
    "Abre este enlace seguro para definir una nueva contraseña:",
    actionLink,
    "",
    "Si no has solicitado este cambio, puedes ignorar este correo.",
    "",
    "Este es un correo transaccional automático. No es necesario responderlo.",
  ].join("\n");

  const emailHtml = `
    <!DOCTYPE html>
    <html lang="es">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Restablece tu contraseña</title>
      <style>
        body {
          margin: 0;
          padding: 0;
          background: #f4f7fb;
          color: #16324f;
          font-family: Arial, sans-serif;
        }
        .wrapper {
          width: 100%;
          padding: 24px 12px;
          box-sizing: border-box;
        }
        .card {
          max-width: 560px;
          margin: 0 auto;
          background: #ffffff;
          border: 1px solid #d7e2ee;
          border-radius: 20px;
          overflow: hidden;
          box-shadow: 0 16px 40px rgba(22, 50, 79, 0.08);
        }
        .hero {
          background: linear-gradient(135deg, #0f4c81 0%, #1976d2 100%);
          color: #ffffff;
          padding: 32px 32px 24px;
        }
        .hero h1 {
          margin: 0 0 8px;
          font-size: 26px;
          line-height: 1.2;
        }
        .hero p {
          margin: 0;
          font-size: 15px;
          line-height: 1.6;
          opacity: 0.94;
        }
        .content {
          padding: 32px;
        }
        .content h2 {
          margin: 0 0 12px;
          font-size: 22px;
          line-height: 1.3;
        }
        .content p {
          margin: 0 0 16px;
          font-size: 15px;
          line-height: 1.7;
          color: #35506b;
        }
        .details {
          background: #f7fafd;
          border: 1px solid #dce7f3;
          border-radius: 14px;
          padding: 16px 18px;
          margin: 24px 0;
        }
        .details strong {
          color: #16324f;
        }
        .button {
          display: inline-block;
          padding: 14px 24px;
          background: #1976d2;
          color: #ffffff !important;
          text-decoration: none;
          border-radius: 999px;
          font-size: 15px;
          font-weight: bold;
        }
        .support {
          margin-top: 24px;
          font-size: 13px;
          color: #5d7288;
        }
        .footer {
          padding: 0 32px 28px;
          font-size: 12px;
          line-height: 1.6;
          color: #7b8da1;
        }
      </style>
    </head>
    <body>
      <div class="wrapper">
        <div class="card">
          <div class="hero">
            <h1>Asociación Cigüeña</h1>
            <p>Restablecimiento seguro de contraseña</p>
          </div>
          <div class="content">
            <h2>Recupera el acceso a tu cuenta</h2>
            <p>Hemos recibido una solicitud para cambiar la contraseña de tu cuenta.</p>
            <div class="details">
              <strong>Cuenta:</strong> ${email}
            </div>
            <p>Usa este enlace seguro para definir una nueva contraseña:</p>
            <p>
              <a href="${actionLink}" class="button">Restablecer contraseña</a>
            </p>
            <p class="support">
              Si no has solicitado este cambio, puedes ignorar este correo sin hacer nada más.
            </p>
          </div>
          <div class="footer">
            Este es un correo transaccional automático de Asociación Cigüeña. No es necesario responderlo.
          </div>
        </div>
      </div>
    </body>
    </html>
  `;

  return {
    emailSubject,
    emailText,
    emailHtml,
  };
}

async function ensureAuthUser(userData: InvitationUserData) {
  try {
    const authUser = await admin.auth().createUser({
      email: userData.email,
      password: generateTemporaryPassword(),
      displayName: userData.displayName,
      emailVerified: false,
    });

    console.log(`Usuario creado en Auth con UID: ${authUser.uid}`);
    return authUser;
  } catch (authError: any) {
    if (authError.code === "auth/email-already-exists") {
      const existingUser = await admin.auth().getUserByEmail(userData.email);
      console.log(`Usuario ya existe en Auth con UID: ${existingUser.uid}`);
      return existingUser;
    }

    throw authError;
  }
}

async function sendInvitationEmail(authUid: string, userData: InvitationUserData) {
  const rawLink = await admin.auth().generatePasswordResetLink(userData.email);
  const actionLink = buildCustomAuthActionLink(rawLink);
  const emailTemplate = buildInvitationEmail(userData, actionLink);

  await admin.firestore().collection("mail").add({
    to: userData.email,
    message: {
      subject: emailTemplate.emailSubject,
      text: emailTemplate.emailText,
      html: emailTemplate.emailHtml,
    },
  });

  await admin.firestore()
    .collection("users")
    .doc(authUid)
    .set({
      invitationSent: true,
      invitationSentAt: admin.firestore.FieldValue.serverTimestamp(),
      invitationError: admin.firestore.FieldValue.delete(),
    }, {merge: true});
}

async function sendPasswordResetEmail(email: string) {
  const rawLink = await admin.auth().generatePasswordResetLink(email);
  const actionLink = buildCustomAuthActionLink(rawLink);
  const emailTemplate = buildPasswordResetEmail(email, actionLink);

  await admin.firestore().collection("mail").add({
    to: email,
    message: {
      subject: emailTemplate.emailSubject,
      text: emailTemplate.emailText,
      html: emailTemplate.emailHtml,
    },
  });
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
    const authUser = await ensureAuthUser(userData);

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

    await sendInvitationEmail(authUser.uid, userData);
    /*

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
    */

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

  const userData = userDoc.data() as InvitationUserData | undefined;
  if (!userData?.email || !userData.displayName || !userData.role) {
    throw new HttpsError(
      "failed-precondition",
      "El usuario no tiene datos suficientes para reenviar la invitación"
    );
  }

  const authUser = await ensureAuthUser(userData);

  await userDoc.ref.set({
    needsRegistration: true,
    invitationSent: false,
  }, {merge: true});

  await sendInvitationEmail(authUser.uid, userData);

  return {success: true, message: "Invitación reenviada"};
});

export const requestPasswordReset = onCall({
  region: "europe-west1",
}, async (request) => {
  const email = String(request.data?.email ?? "").trim().toLowerCase();

  if (!email) {
    throw new HttpsError("invalid-argument", "El email es obligatorio");
  }

  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailPattern.test(email)) {
    throw new HttpsError("invalid-argument", "El email no es válido");
  }

  try {
    await admin.auth().getUserByEmail(email);
    await sendPasswordResetEmail(email);
  } catch (error: any) {
    if (error?.code !== "auth/user-not-found") {
      console.error("Error al procesar requestPasswordReset:", error);
      throw new HttpsError("internal", "No se pudo procesar la solicitud");
    }
  }

  return {
    success: true,
    message: "Si existe una cuenta asociada, recibirás un correo con instrucciones.",
  };
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

  // Solo notificar si es pública
  if (newsData.isPublic !== true) {
    console.log(`ℹ️ Noticia ${newsId} no es pública, no se notifica`);
    return;
  }

  try {
    const title = newsData.title || "Nueva publicación";
    const shortDesc = newsData.shortDescription || "";

    console.log(`📰 Nueva publicación: ${title}`);

    // ✅ ENVIAR A TOPIC "public" (todos los dispositivos con la app)
	const message = {
	  topic: "public",
	  notification: {  // ← AÑADIR campo notification
		title: "📰 Nueva publicación",
		body: title
	  },
	  data: {
		type: "news",
		itemId: newsId,
		title: "📰 Nueva publicación",
		body: title,
		...(shortDesc && { shortDescription: shortDesc })
	  },
	  android: {
		priority: "high" as const,
		notification: {
		  icon: "ic_notification",
		  color: "#1976D2"
		}
	  }
	};

    const response = await admin.messaging().send(message);
    console.log(`✅ Notificación de noticia enviada: ${response}`);

  } catch (error) {
    console.error("❌ Error al enviar notificación de noticia:", error);
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

    // Formatear fecha para el body
    let dateStr = "";
    if (date && typeof date.toDate === 'function') {
      const d = date.toDate();
      dateStr = `${d.getDate()}/${d.getMonth() + 1}/${d.getFullYear()}`;
    }

    const bodyText = dateStr ? `${title} - ${dateStr}` : title;

	const message = {
	  topic: "public",
	  notification: {  // ← AÑADIR campo notification
		title: "🏔️ Nueva Excursión Programada",
		body: bodyText
	  },
	  data: {
		type: "excursion",
		itemId: excursionId,
		title: "Nueva Excursión Programada",
		body: bodyText
	  },
	  android: {
		priority: "high" as const,
		notification: {
		  icon: "ic_notification",
		  color: "#1976D2"
		}
	  }
	};

    const response = await admin.messaging().send(message);
    console.log(`✅ Notificación de excursión enviada: ${response}`);

  } catch (error) {
    console.error("❌ Error al enviar notificación de excursión:", error);
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

        if (!excursionId) {
          console.log("❌ No excursionId");
          return;
        }

        console.log(`📸 Upload batch completed: ${photoCount} photos for excursion ${excursionId}`);

        // Obtener título de la excursión para el mensaje
        const excursionDoc = await admin.firestore()
          .collection("excursions")
          .doc(excursionId)
          .get();

        const excursionTitle = excursionDoc.data()?.title || "una excursión";

        // Mensaje según el número de fotos
        const body = photoCount === 1
          ? `Nueva foto de "${excursionTitle}" disponible en la galería`
          : `${photoCount} nuevas fotos de "${excursionTitle}" disponibles en la galería`;

        // ✅ ENVIAR A TOPIC "authenticated" (solo usuarios logueados)
        const message = {
		  topic: "authenticated",
		  notification: {  // ← AÑADIR campo notification
			title: "📸 ¡Fotos Nuevas Disponibles!",
			body: body
		  },
		  data: {
			type: "photo",
			itemId: excursionId,
			title: "📸 ¡Fotos Nuevas Disponibles!",
			body: body
		  },
		  android: {
			priority: "high" as const,
			notification: {
			  icon: "ic_notification",
			  color: "#1976D2"
			}
		  }
		};	

        const response = await admin.messaging().send(message);
        console.log(`✅ Notificación de fotos enviada a topic "authenticated": ${response}`);
      }
    } catch (error) {
      console.error("❌ Error en onUploadBatchCompleted:", error);
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
	
	  const authDataMultiple = event.data?.data();
	
	  // ✅ NUEVO: Si es parte de un email batch, NO enviar email individual
	  if (authDataMultiple?.isBatchEmail === true) {
		console.log(`📧 Skip individual email for ${event.params.authorizationId} (batch mode)`);
		return null;
	  }
		
      const snapshot = event.data;
      if (!snapshot) {
        console.log("No data associated with the event");
        return null;  // ✅ Early return con valor
      }
      
      const authData = snapshot.data();
      const authorizationId = event.params.authorizationId;
	  
	   // ✅ NUEVO: Si es parte de un email batch, NO enviar email individual
      if (authData?.isBatchEmail === true) {
        console.log(`📧 Skip individual email for ${authorizationId} (batch mode)`);
        return null;  // ✅ Early return con valor
      }
      
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
                ${minorName ? `<strong>👶 Participante:</strong> ${minorName}<br>` : ''}
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
      
	  return null;
	  
    } catch (error) {
      console.error("❌ Error en onAuthorizationSigned:", error);
	  return null;
    }
  }
);

// ==========================================
// ENVÍO DE EMAIL PARA AUTORIZACIONES MÚLTIPLES (HERMANOS)
// ==========================================

export const sendBatchAuthorizationEmail = onCall({

  region: "europe-west1",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debes estar autenticado");
  }

  const { authorizationIds } = request.data as { authorizationIds: string[] };

  if (!authorizationIds || authorizationIds.length === 0) {
    throw new HttpsError("invalid-argument", "No se proporcionaron autorizaciones");
  }

  try {
    console.log(`📧 Enviando email batch para ${authorizationIds.length} autorizaciones`);

    // 1. Obtener todas las autorizaciones
    const authorizationsData: any[] = [];
    
    for (const authId of authorizationIds) {
      const authDoc = await admin.firestore()
        .collection("signedAuthorizations")
        .doc(authId)
        .get();

      if (authDoc.exists) {
        authorizationsData.push(authDoc.data());
      } else {
        console.warn(`⚠️ Autorización ${authId} no encontrada`);
      }
    }

    if (authorizationsData.length === 0) {
      throw new HttpsError("not-found", "No se encontraron autorizaciones válidas");
    }

    // 2. Datos comunes (todos tienen el mismo tutor)
    const firstAuth = authorizationsData[0];
    const tutorEmail = firstAuth.tutorEmail;
    const tutorName = firstAuth.tutorName;
	const tutorPhone = firstAuth.tutorPhone as string;  // ← AÑADIR ESTA LÍNEA ✅
	const tutorDni = firstAuth.tutorDni as string;      // ← AÑADIR ESTA LÍNEA ✅
    const excursionTitle = firstAuth.excursionTitle;

    // 3. Construir lista de menores y PDFs
    const minorsList = authorizationsData
      .map((auth, index) => {
        const minorName = auth.minorName || `Participante ${index + 1}`;
        const pdfUrl = auth.signedPdfUrl;
        return { minorName, pdfUrl };
      })
      .filter(item => item.pdfUrl);

    // 4. Construir email HTML con lista de menores
    const emailSubject = `✅ Autorizaciones firmadas - ${excursionTitle}`;
    const emailBody = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
          .container { max-width: 600px; margin: 0 auto; padding: 20px; }
          .header { background: #1976D2; color: white; padding: 20px; text-align: center; border-radius: 10px 10px 0 0; }
          .content { padding: 30px; background: #f9f9f9; }
          .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; background: #e9e9e9; border-radius: 0 0 10px 10px; }
          .info-box { background: #fff; padding: 15px; border-left: 4px solid #4CAF50; margin: 20px 0; }
          .minor-list { background: #fff; padding: 15px; margin: 20px 0; border-radius: 5px; }
          .minor-item { padding: 10px 0; border-bottom: 1px solid #eee; }
          .minor-item:last-child { border-bottom: none; }
          .button { display: inline-block; padding: 15px 40px; background: #1976D2; color: white !important; text-decoration: none; border-radius: 5px; margin: 10px 5px; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>Asociación Cigüeña</h1>
          </div>
          <div class="content">
            <h2>¡Autorizaciones Firmadas Correctamente!</h2>
            <p>Hola ${tutorName},</p>
            <p>Has firmado correctamente <strong>${minorsList.length} autorización(es)</strong> para:</p>
            
            <div class="info-box">
              <strong>📍 Excursión:</strong> ${excursionTitle}<br>
              <strong>✅ Estado:</strong> Firmadas y registradas
            </div>       
			
            <div class="minor-list">
              <h3>📋 Participantes autorizados:</h3>
              ${minorsList.map((item, index) => `
                <div class="minor-item">
                  <strong>${index + 1}. ${item.minorName}</strong>
                  <br><a href="${item.pdfUrl}" style="color: #1976D2;">Descargar PDF individual</a>
                </div>
              `).join('')}
            </div>

            <p>📎 <strong>Encontrarás ${minorsList.length} PDFs adjuntos</strong> a este correo (uno por participante).</p>
            
            <p style="margin-top: 30px; font-size: 14px; color: #666;">
              Si no solicitaste estas autorizaciones, contacta con nosotros inmediatamente.
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

    // 5. Construir array de adjuntos (múltiples PDFs)
    const attachments = minorsList.map((item, index) => ({
      filename: `autorizacion_${item.minorName.replace(/[^a-z0-9]/gi, '_').slice(0, 30)}.pdf`,
      path: item.pdfUrl,
      contentType: 'application/pdf'
    }));

    // 6. Enviar email al tutor CON múltiples adjuntos
    await admin.firestore().collection("mail").add({
      to: tutorEmail,
      message: {
        subject: emailSubject,
        html: emailBody,
        attachments: attachments  // ← Múltiples PDFs
      },
    });

    console.log(`✅ Email batch enviado a ${tutorEmail} con ${attachments.length} adjuntos`);

    // 7. Email a la asociación (opcional)
    const associationEmail = "asociacionciguena1993@gmail.com";
    const adminEmailSubject = `📝 ${minorsList.length} autorizaciones firmadas - ${excursionTitle}`;
    // ✅ Personaliza el cuerpo HTML aquí:
	const adminEmailBody = `
	  <!DOCTYPE html>
	  <html>
	  <head>
		<meta charset="utf-8">
		<style>
		  body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; margin: 0; padding: 0; }
		  .container { max-width: 650px; margin: 20px auto; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
		  .header { background: linear-gradient(135deg, #1976D2, #1565C0); color: white; padding: 25px 30px; text-align: center; }
		  .header h1 { margin: 0; font-size: 22px; font-weight: 600; }
		  .header p { margin: 8px 0 0; opacity: 0.95; font-size: 14px; }
		  .content { padding: 30px; }
		  .info-box { background: #f0f7ff; padding: 20px; border-left: 4px solid #1976D2; border-radius: 6px; margin: 20px 0; }
		  .info-box strong { color: #1976D2; }
		  .minor-list { background: #fafafa; padding: 20px; border-radius: 8px; margin: 20px 0; }
		  .minor-item { padding: 12px 0; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
		  .minor-item:last-child { border-bottom: none; }
		  .minor-name { font-weight: 600; color: #333; }
		  .minor-status { background: #1976D2; color: white; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 500; }
		  .footer { background: #f5f5f5; padding: 20px 30px; text-align: center; font-size: 12px; color: #666; border-top: 1px solid #eee; }
		  .button { display: inline-block; padding: 10px 24px; background: #1976D2; color: white !important; text-decoration: none; border-radius: 6px; font-weight: 500; font-size: 14px; }
		  .button:hover { background: #1565C0; }
		</style>
	  </head>
	  <body>
		<div class="container">
		  <div class="header">
			<h1>Asociación Cigüeña</h1>
			<p>Nuevas autorizaciones recibidas</p>
		  </div>
		  <div class="content">
			<h2 style="margin: 0 0 10px; color: #1976D2;">📝 Autorizaciones de Excursión</h2>
			
			<div class="info-box">
			  <strong>📍 Excursión:</strong> ${excursionTitle}<br>
			  <strong>👤 Tutor:</strong> ${tutorName}<br>
			  <strong>📧 Contacto:</strong> <a href="mailto:${tutorEmail}" style="color: #1976D2;">${tutorEmail}</a><br>
			  <strong>📞 Teléfono:</strong> ${tutorPhone}<br>
			  <strong>🆔 DNI:</strong> ${tutorDni}<br>
			  <strong>🔢 Total niños:</strong> ${minorsList.length}<br>
			  <strong>📅 Fecha de firma:</strong> ${new Date().toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
			</div>

			<div class="minor-list">
			  <h3 style="margin: 0 0 15px; color: #333;">👥 Participantes autorizados:</h3>
			  ${minorsList.map((item, index) => `
				<div class="minor-item">
				  <span class="minor-name">${index + 1}. ${item.minorName}</span>
				  <span class="minor-status">Pendiente</span>
				</div>
			  `).join('')}
			</div>

			<p style="margin: 25px 0 15px; font-weight: 500;">📎 Archivos adjuntos:</p>
			<p style="margin: 0 0 20px; color: #666;">
			  Se han adjuntado <strong>${minorsList.length} PDF(s) firmados</strong> a este correo.
			</p>

			<div style="text-align: center; margin: 30px 0;">
			  <a href="https://console.firebase.google.com/project/asociacion-ciguena-188da/firestore/data/~2FsignedAuthorizations" class="button" target="_blank">
				🔍 Ver en Firestore
			  </a>
			</div>

			<p style="margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 13px; color: #666;">
			  <strong>Nota para administradores:</strong><br>
			  Estas autorizaciones están pendientes de validación. Puedes aprobarlas o rechazarlas desde la pantalla de administración de la app.
			</p>
		  </div>
		  <div class="footer">
			<p><strong>Asociación Cigüeña</strong> © 2026</p>
			<p>Este es un email automático generado por el sistema de autorizaciones.</p>
			<p style="margin-top: 8px;">
			  <a href="mailto:asociacionciguena1993@gmail.com" style="color: #1976D2; text-decoration: none;">Contactar con soporte</a>
			</p>
		  </div>
		</div>
	  </body>
	  </html>
	`;

    await admin.firestore().collection("mail").add({
      to: associationEmail,
      message: {
        subject: adminEmailSubject,
        html: adminEmailBody,
        attachments: attachments  // ← Múltiples PDFs también para admin
      },
    });

    console.log(`✅ Email admin enviado con ${attachments.length} adjuntos`);

    // 8. Marcar autorizaciones como email enviado
    const batch = admin.firestore().batch();
    for (const authId of authorizationIds) {
      const authRef = admin.firestore().collection("signedAuthorizations").doc(authId);
      batch.update(authRef, {
        emailSent: true,
        emailSentAt: admin.firestore.FieldValue.serverTimestamp(),
        isBatchEmail: true  // ← Marcador para saber que fue email múltiple
      });
    }
    await batch.commit();

    return { 
      success: true, 
      message: `Email enviado con ${attachments.length} autorizaciones`,
      emailSentTo: tutorEmail
    };

  } catch (error) {
    console.error("❌ Error en sendBatchAuthorizationEmail:", error);
    throw new HttpsError("internal", "Error al enviar email batch");
  }
});

export { generateMinorsListDocx };
