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
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.snapshots

@Singleton
class SignedAuthorizationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    private val activeAuthorizationStatuses = listOf(
        AuthorizationStatus.PENDING.name,
        AuthorizationStatus.APPROVED.name
    )

    /**
     * Firmar autorización
     */
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
        signaturePaths: List<androidx.compose.ui.graphics.Path>,
        isBatchEmail: Boolean = false
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuario no autenticado"))

            if (!hasAvailableCapacity(excursionId, requestedParticipants = 1)) {
                return Result.failure(Exception("No quedan plazas disponibles para esta excursión"))
            }

            android.util.Log.d("SignAuth", "🚀 Inicio firma - UserID: $userId, ExcursionID: $excursionId")

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
            android.util.Log.d("SignAuth", "📄 PDF generado: ${pdfFile.length()} bytes")

            // 2. Subir firma como imagen (ruta corregida: {userId} en lugar de {excursionId})
            val signatureBitmap = PdfGenerator.pathsToBitmap(signaturePaths, 800, 300, 20f)
            val signatureFile = File(context.cacheDir, "signature_${System.currentTimeMillis()}.png")
            signatureFile.outputStream().use { out ->
                signatureBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            val signatureId = UUID.randomUUID().toString()
            // ✅ RUTA CORREGIDA: authorizations/signatures/{userId}/{fileName}
            val signatureRef = storage.reference
                .child("authorizations")
                .child("signatures")
                .child(userId)  // ← CLAVE: userId, NO excursionId
                .child("${excursionId}_${signatureId}.png")

            android.util.Log.d("SignAuth", "📤 Subiendo firma a: ${signatureRef.path}")
            signatureRef.putFile(Uri.fromFile(signatureFile)).await()
            val signatureUrl = signatureRef.downloadUrl.await().toString()
            signatureFile.delete()
            android.util.Log.d("SignAuth", "✅ Firma subida: $signatureUrl")

            // 3. Subir PDF firmado (ruta corregida: {userId} en lugar de {excursionId})
            val pdfId = UUID.randomUUID().toString()
            // ✅ RUTA CORREGIDA: authorizations/signed/{userId}/{fileName}
            val pdfRef = storage.reference
                .child("authorizations")
                .child("signed")
                .child(userId)  // ← CLAVE: userId, NO excursionId ⭐
                .child("${excursionId}_${pdfId}.pdf")

            android.util.Log.d("SignAuth", "📤 Subiendo PDF a: ${pdfRef.path}")
            pdfRef.putFile(Uri.fromFile(pdfFile)).await()  // ← Si falla aquí, es error 403 de reglas
            val pdfUrl = pdfRef.downloadUrl.await().toString()
            pdfFile.delete()
            android.util.Log.d("SignAuth", "✅ PDF subido: $pdfUrl")

            val batchId = UUID.randomUUID().toString()  // ← Pasar desde el ViewModel

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
                "status" to AuthorizationStatus.PENDING.name,
                "batchId" to batchId,
                "isBatchEmail" to isBatchEmail
            )

            val docRef = firestore.collection("signedAuthorizations").add(authorizationData).await()
            android.util.Log.d("SignAuth", "✅ Firestore doc creado: ${docRef.id}")

            Result.success(docRef.id)

        } catch (e: Exception) {
            android.util.Log.e("SignAuth", "❌ ERROR CRÍTICO: ${e.message}", e)
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

    suspend fun hasAvailableCapacity(excursionId: String, requestedParticipants: Int): Boolean {
        if (requestedParticipants <= 0) return true

        return try {
            val excursionDoc = firestore.collection("excursions")
                .document(excursionId)
                .get()
                .await()

            val maxParticipants = excursionDoc.getLong("maxParticipants")?.toInt() ?: 0
            if (maxParticipants <= 0) return true

            val activeCount = firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .get()
                .await()
                .documents
                .count { doc -> doc.getString("status") in activeAuthorizationStatuses }

            activeCount + requestedParticipants <= maxParticipants
        } catch (e: Exception) {
            false
        }
    }

    fun getActiveAuthorizationCount(excursionId: String): Flow<Int> {
        return firestore.collection("signedAuthorizations")
            .whereEqualTo("excursionId", excursionId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.count { doc ->
                    doc.getString("status") in activeAuthorizationStatuses
                }
            }
    }

    /**
     * Obtener todas las autorizaciones de una excursión (para admin)
     */
    fun getExcursionAuthorizations(excursionId: String): Flow<List<SignedAuthorization>> {
        return firestore.collection("signedAuthorizations")
            .whereEqualTo("excursionId", excursionId)
            .orderBy("signedAt", Query.Direction.DESCENDING)
            .snapshots()  // ← Listener en tiempo real (emite cada vez que cambian los datos)
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
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
                        android.util.Log.e("AuthRepo", "❌ Error mapeando documento ${doc.id}: ${e.message}")
                        null
                    }
                }
            }
    }
}
