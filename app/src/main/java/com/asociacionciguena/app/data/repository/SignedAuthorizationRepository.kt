package com.asociacionciguena.app.data.repository

import android.content.Context
import android.net.Uri
import com.asociacionciguena.app.domain.model.SignedAuthorization
import com.asociacionciguena.app.domain.model.AuthorizationStatus
import com.asociacionciguena.app.util.PdfGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class SignedAuthorizationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {

    /**
     * Firmar autorización
     */
    suspend fun signAuthorization(
        excursionId: String,
        excursionTitle: String,
        excursionDate: String,
        tutorName: String,
        tutorDni: String,
        tutorPhone: String,
        tutorEmail: String,
        minorName: String?,
        signaturePaths: List<androidx.compose.ui.graphics.Path>
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuario no autenticado"))

            android.util.Log.d("SignAuth", "📝 Firmando autorización - UserID: $userId")

            // 1. Generar PDF
            val pdfFile = PdfGenerator.generateSignedAuthorization(
                context = context,
                excursionTitle = excursionTitle,
                excursionDate = excursionDate,
                tutorName = tutorName,
                tutorDni = tutorDni,
                tutorPhone = tutorPhone,
                minorName = minorName,
                signaturePaths = signaturePaths
            )

            android.util.Log.d("AUTH", "📄 PDF generado: ${pdfFile.path}, tamaño: ${pdfFile.length()} bytes")

            // 2. Subir firma como imagen
            val signatureBitmap = PdfGenerator.pathsToBitmap(
                paths = signaturePaths,
                bitmapWidth = 800,    // Tamaño final deseado
                bitmapHeight = 300,
                padding = 20f
            )
            val signatureFile = File(context.cacheDir, "signature_${System.currentTimeMillis()}.png")
            signatureFile.outputStream().use { out ->
                signatureBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            val signatureId = UUID.randomUUID().toString()
            val signatureRef = storage.reference
                .child("authorizations/signatures/${excursionId}/${userId}_${signatureId}.png")

            signatureRef.putFile(Uri.fromFile(signatureFile)).await()
            val signatureUrl = signatureRef.downloadUrl.await().toString()

            // Limpiar archivo temporal
            signatureFile.delete()

            // 3. Subir PDF
            val pdfId = UUID.randomUUID().toString()
            val pdfRef = storage.reference
                .child("authorizations/signed/${excursionId}/${userId}_${pdfId}.pdf")

            pdfRef.putFile(Uri.fromFile(pdfFile)).await()
            val pdfUrl = pdfRef.downloadUrl.await().toString()

            // Limpiar archivo temporal
            pdfFile.delete()

            // 4. Guardar en Firestore
            val authorizationData = hashMapOf(
                "excursionId" to excursionId,
                "excursionTitle" to excursionTitle,
                "excursionDate" to excursionDate,
                "userId" to userId,
                "tutorName" to tutorName,
                "tutorDni" to tutorDni,
                "tutorPhone" to tutorPhone,
                "tutorEmail" to tutorEmail,
                "minorName" to minorName,
                "signatureImageUrl" to signatureUrl,
                "signedPdfUrl" to pdfUrl,
                "signedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "emailSent" to false,
                "status" to AuthorizationStatus.PENDING.name
            )

            android.util.Log.d("SignAuth", "📤 Datos a guardar: $authorizationData")

            val docRef = firestore.collection("signedAuthorizations")
                .add(authorizationData)
                .await()

            android.util.Log.d("SignAuth", "✅ Documento creado: ${docRef.id}")

            // 5. Enviar email (se hace vía Cloud Function trigger)

            Result.success(docRef.id)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtener autorizaciones de un usuario
     */
    fun getUserAuthorizations(userId: String): Flow<List<SignedAuthorization>> = flow {
        try {
            val snapshot = firestore.collection("signedAuthorizations")
                .whereEqualTo("userId", userId)
                .orderBy("signedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val authorizations = snapshot.documents.mapNotNull { doc ->
                try {
                    SignedAuthorization(
                        id = doc.id,
                        excursionId = doc.getString("excursionId") ?: "",
                        excursionTitle = doc.getString("excursionTitle") ?: "",
                        userId = doc.getString("userId") ?: "",
                        tutorName = doc.getString("tutorName") ?: "",
                        tutorDni = doc.getString("tutorDni") ?: "",
                        tutorPhone = doc.getString("tutorPhone") ?: "",
                        tutorEmail = doc.getString("tutorEmail") ?: "",
                        minorName = doc.getString("minorName"),
                        signatureImageUrl = doc.getString("signatureImageUrl") ?: "",
                        signedPdfUrl = doc.getString("signedPdfUrl") ?: "",
                        emailSent = doc.getBoolean("emailSent") ?: false,
                        status = AuthorizationStatus.valueOf(
                            doc.getString("status") ?: "PENDING"
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }

            emit(authorizations)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Verificar si un usuario ya firmó para una excursión
     */
    suspend fun hasUserSigned(excursionId: String, userId: String): Boolean {
        return try {
            val snapshot = firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            snapshot.documents.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtener todas las autorizaciones de una excursión (para admin)
     */
    fun getExcursionAuthorizations(excursionId: String): Flow<List<SignedAuthorization>> = flow {
        try {
            val snapshot = firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .orderBy("signedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val authorizations = snapshot.documents.mapNotNull { doc ->
                try {
                    SignedAuthorization(
                        id = doc.id,
                        excursionId = doc.getString("excursionId") ?: "",
                        excursionTitle = doc.getString("excursionTitle") ?: "",
                        userId = doc.getString("userId") ?: "",
                        tutorName = doc.getString("tutorName") ?: "",
                        tutorDni = doc.getString("tutorDni") ?: "",
                        tutorPhone = doc.getString("tutorPhone") ?: "",
                        tutorEmail = doc.getString("tutorEmail") ?: "",
                        minorName = doc.getString("minorName"),
                        signatureImageUrl = doc.getString("signatureImageUrl") ?: "",
                        signedPdfUrl = doc.getString("signedPdfUrl") ?: "",
                        emailSent = doc.getBoolean("emailSent") ?: false,
                        status = AuthorizationStatus.valueOf(
                            doc.getString("status") ?: "PENDING"
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }

            emit(authorizations)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}