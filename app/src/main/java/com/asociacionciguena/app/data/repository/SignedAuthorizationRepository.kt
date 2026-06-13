package com.asociacionciguena.app.data.repository

import android.content.Context
import android.net.Uri
import com.asociacionciguena.app.BuildConfig
import com.asociacionciguena.app.domain.model.SignedAuthorization
import com.asociacionciguena.app.domain.model.AuthorizationStatus
import com.asociacionciguena.app.util.PdfGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.snapshots

@Singleton
class SignedAuthorizationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    @ApplicationContext private val context: Context
) {
    private val activeAuthorizationStatuses = listOf(
        AuthorizationStatus.PENDING.name,
        AuthorizationStatus.APPROVED.name
    )

    data class AuthorizationRequest(
        val excursionTitle: String,
        val excursionDate: String,
        val tutorName: String,
        val tutorDni: String,
        val tutorPhone: String,
        val tutorEmail: String,
        val minorName: String?,
        val signaturePaths: List<androidx.compose.ui.graphics.Path>,
        val isBatchEmail: Boolean = false
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
        return signAuthorizations(
            excursionId = excursionId,
            requests = listOf(
                AuthorizationRequest(
                    excursionTitle = excursionTitle,
                    excursionDate = excursionDate,
                    tutorName = tutorName,
                    tutorDni = tutorDni,
                    tutorPhone = tutorPhone,
                    tutorEmail = tutorEmail,
                    minorName = minorName,
                    signaturePaths = signaturePaths,
                    isBatchEmail = isBatchEmail
                )
            )
        ).mapCatching { ids -> ids.single() }
    }

    suspend fun signAuthorizations(
        excursionId: String,
        requests: List<AuthorizationRequest>
    ): Result<List<String>> {
        if (requests.isEmpty() || requests.size > 3) {
            return Result.failure(IllegalArgumentException("Número de autorizaciones no válido"))
        }

        val userId = auth.currentUser?.uid
            ?: return Result.failure(Exception("Usuario no autenticado"))
        val uploadedReferences = mutableListOf<StorageReference>()
        val localFiles = mutableListOf<File>()

        return try {
            val batchId = UUID.randomUUID().toString()
            val payloads = requests.map { request ->
                val pdfFile = PdfGenerator.generateSignedAuthorization(
                    context = context,
                    excursionTitle = request.excursionTitle,
                    excursionDate = request.excursionDate,
                    tutorName = request.tutorName,
                    tutorDni = request.tutorDni,
                    tutorPhone = request.tutorPhone,
                    minorName = request.minorName,
                    signaturePaths = request.signaturePaths
                )
                localFiles += pdfFile

                val signatureBitmap = PdfGenerator.pathsToBitmap(
                    request.signaturePaths, 800, 300, 20f
                )
                val signatureFile = File(
                    context.cacheDir,
                    "signature_${UUID.randomUUID()}.png"
                )
                localFiles += signatureFile
                signatureFile.outputStream().use { output ->
                    signatureBitmap.compress(
                        android.graphics.Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )
                }

                val signatureRef = storage.reference
                    .child("authorizations/signatures/$userId")
                    .child("${excursionId}_${UUID.randomUUID()}.png")
                signatureRef.putFile(Uri.fromFile(signatureFile)).await()
                uploadedReferences += signatureRef
                val signatureUrl = signatureRef.downloadUrl.await().toString()

                val pdfRef = storage.reference
                    .child("authorizations/signed/$userId")
                    .child("${excursionId}_${UUID.randomUUID()}.pdf")
                pdfRef.putFile(Uri.fromFile(pdfFile)).await()
                uploadedReferences += pdfRef
                val pdfUrl = pdfRef.downloadUrl.await().toString()

                mapOf(
                    "excursionTitle" to request.excursionTitle,
                    "excursionDate" to request.excursionDate,
                    "tutorName" to request.tutorName,
                    "tutorDni" to request.tutorDni,
                    "tutorPhone" to request.tutorPhone,
                    "tutorEmail" to request.tutorEmail,
                    "minorName" to request.minorName,
                    "signatureImageUrl" to signatureUrl,
                    "signedPdfUrl" to pdfUrl,
                    "batchId" to batchId,
                    "isBatchEmail" to request.isBatchEmail
                )
            }

            val callableResult = functions
                .getHttpsCallable("reserveSignedAuthorizations")
                .call(
                    mapOf(
                        "excursionId" to excursionId,
                        "authorizations" to payloads,
                        "databaseId" to BuildConfig.FIRESTORE_DATABASE_ID
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val response = callableResult.getData() as? Map<String, Any?>
            val ids = (response?.get("authorizationIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()

            if (ids.size != requests.size) {
                throw IllegalStateException("Respuesta incompleta al reservar las plazas")
            }
            Result.success(ids)
        } catch (e: Exception) {
            uploadedReferences.forEach { reference ->
                runCatching { reference.delete().await() }
            }
            android.util.Log.e("SignAuth", "Error reservando autorizaciones", e)
            Result.failure(e)
        } finally {
            localFiles.forEach { file -> runCatching { file.delete() } }
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

    suspend fun hasUserApproved(excursionId: String, userId: String): Boolean {
        return try {
            val snapshot = firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", AuthorizationStatus.APPROVED.name)
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

            if (excursionDoc.getBoolean("registrationClosed") == true) {
                return false
            }

            val maxParticipants = excursionDoc.getLong("maxParticipants")?.toInt() ?: 0
            if (maxParticipants <= 0) return true

            val activeCount = getActiveAuthorizationCountOnce(excursionId)
            if (activeCount == null) {
                android.util.Log.w(
                    "AuthRepo",
                    "No se pudo verificar el cupo de $excursionId; se bloquea la firma"
                )
                return false
            }

            activeCount + requestedParticipants <= maxParticipants
        } catch (e: Exception) {
            android.util.Log.w("AuthRepo", "Error verificando cupo de $excursionId", e)
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
            .catch { e ->
                android.util.Log.w("AuthRepo", "No se pudo observar el conteo de autorizaciones", e)
                emit(getPublishedAuthorizationCount(excursionId) ?: 0)
            }
    }

    private suspend fun getActiveAuthorizationCountOnce(excursionId: String): Int? {
        return try {
            firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .get()
                .await()
                .documents
                .count { doc -> doc.getString("status") in activeAuthorizationStatuses }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepo", "No se pudo contar autorizaciones activas", e)
            getPublishedAuthorizationCount(excursionId)
        }
    }

    private suspend fun getPublishedAuthorizationCount(excursionId: String): Int? {
        return try {
            firestore.collection("excursions")
                .document(excursionId)
                .get()
                .await()
                .getLong("currentParticipants")
                ?.toInt()
        } catch (e: Exception) {
            android.util.Log.w("AuthRepo", "No se pudo leer currentParticipants", e)
            null
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
