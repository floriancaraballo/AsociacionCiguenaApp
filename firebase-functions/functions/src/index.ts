import * as admin from "firebase-admin";
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