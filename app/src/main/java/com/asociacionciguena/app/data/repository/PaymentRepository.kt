package com.asociacionciguena.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.asociacionciguena.app.BuildConfig
import com.asociacionciguena.app.domain.model.Payment
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.functions.FirebaseFunctions  // ← AÑADIR

@Singleton
class PaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
   // private val functions: FirebaseFunctions  // ← AÑADIR ESTE PARÁMETRO
) {

    // ✅ NUEVO: Forzar región europe-west1
    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("europe-west1")  // ← ¡CLAVE!
    }
    /**
     * Obtener estado de pago de un usuario para una excursión
     */
    fun getPaymentStatus(excursionId: String, userId: String): Flow<Payment?> {
        return observePaymentStatus(excursionId, userId)
    }

    fun observePaymentStatus(excursionId: String, userId: String): Flow<Payment?> = callbackFlow {
        val listener = firestore.collection("payments")
            .whereEqualTo("excursionId", excursionId)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents.orEmpty()
                val paidPaymentDoc = documents
                    .filter { doc -> PaymentStatus.fromFirestoreValue(doc.getString("status")) == PaymentStatus.PAID }
                    .maxByOrNull { doc -> doc.paymentSortMillis() }
                val latestPaymentDoc = documents.maxByOrNull { doc -> doc.paymentSortMillis() }
                val payment = (paidPaymentDoc ?: latestPaymentDoc)?.toPayment()

                trySend(payment)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Subir comprobante de pago
     */
    suspend fun uploadPaymentProof(
        excursionId: String,
        amount: Double,
        photoUri: Uri
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuario no autenticado"))

            val userName = auth.currentUser?.displayName ?: "Usuario"

            val approvedAuthorizationId = getApprovedAuthorizationId(excursionId, userId)
                ?: return Result.failure(Exception("La autorización debe estar aprobada antes de subir el comprobante"))

            val contentType = context.contentResolver.getType(photoUri)
                ?: return Result.failure(Exception("No se pudo identificar el tipo de archivo"))

            if (!isAllowedPaymentProofType(contentType)) {
                return Result.failure(Exception("Formato no permitido. Sube una imagen, PDF o documento Word"))
            }

            val fileName = getDisplayName(photoUri)
                ?: "comprobante_${UUID.randomUUID()}.${extensionForContentType(contentType)}"

            val existingPayment = firestore.collection("payments")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val paidPaymentDoc = existingPayment.documents
                .filter { doc -> PaymentStatus.fromFirestoreValue(doc.getString("status")) == PaymentStatus.PAID }
                .maxByOrNull { doc -> doc.paymentSortMillis() }
            if (paidPaymentDoc != null) {
                return Result.failure(Exception("El pago ya está confirmado"))
            }

            val existingBankTransferDoc = existingPayment.documents
                .filter { doc -> doc.getString("paymentMethod") != "redsys" }
                .maxByOrNull { doc -> doc.paymentSortMillis() }

            val uploadAuthorizationId = createPaymentProofUploadAuthorization(
                excursionId = excursionId,
                authorizationId = approvedAuthorizationId
            )

            // 1. Subir comprobante a Storage
            val storageRef = storage.reference
                .child("payments/${excursionId}/${userId}_${uploadAuthorizationId}")

            val metadata = StorageMetadata.Builder()
                .setContentType(contentType)
                .setCustomMetadata("authorizationId", approvedAuthorizationId)
                .setCustomMetadata("databaseId", BuildConfig.FIRESTORE_DATABASE_ID)
                .setCustomMetadata("uploadAuthorizationId", uploadAuthorizationId)
                .build()

            try {
                storageRef.putFile(photoUri, metadata).await()
            } catch (e: Exception) {
                throw Exception(
                    "Storage rechazo la subida del archivo: ${e.message}",
                    e
                )
            }

            val downloadUrl = try {
                storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                throw Exception(
                    "El archivo se subio, pero Storage rechazo obtener su URL: ${e.message}",
                    e
                )
            }

            val paymentData = hashMapOf(
                "excursionId" to excursionId,
                "userId" to userId,
                "userName" to userName,
                "amount" to amount,
                "status" to PaymentStatus.PENDING.name,
                "paymentMethod" to "bank_transfer",
                "authorizationId" to approvedAuthorizationId,
                "paymentProofUrl" to downloadUrl,
                "paymentProofContentType" to contentType,
                "paymentProofFileName" to fileName,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            if (existingBankTransferDoc != null) {
                val paymentUpdateData = hashMapOf(
                    "status" to PaymentStatus.PENDING.name,
                    "paymentMethod" to "bank_transfer",
                    "authorizationId" to approvedAuthorizationId,
                    "paymentProofUrl" to downloadUrl,
                    "paymentProofContentType" to contentType,
                    "paymentProofFileName" to fileName,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                try {
                    firestore.collection("payments")
                        .document(existingBankTransferDoc.id)
                        .update(paymentUpdateData)
                        .await()
                } catch (e: Exception) {
                    throw Exception(
                        "El archivo se subio, pero Firestore rechazo actualizar el pago: ${e.message}",
                        e
                    )
                }
            } else {
                // Crear nuevo
                try {
                    firestore.collection("payments")
                        .add(paymentData)
                        .await()
                } catch (e: Exception) {
                    throw Exception(
                        "El archivo se subio, pero Firestore rechazo crear el pago: ${e.message}",
                        e
                    )
                }
            }

            Result.success(downloadUrl)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isAllowedPaymentProofType(contentType: String): Boolean {
        return contentType.startsWith("image/") ||
                contentType == "application/pdf" ||
                contentType == "application/msword" ||
                contentType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    private fun extensionForContentType(contentType: String): String {
        return when {
            contentType == "application/pdf" -> "pdf"
            contentType == "application/msword" -> "doc"
            contentType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            contentType == "image/png" -> "png"
            contentType == "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    private suspend fun getApprovedAuthorizationId(excursionId: String, userId: String): String? {
        val userApprovedAuthorization = firestore.collection("signedAuthorizations")
            .whereEqualTo("excursionId", excursionId)
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "APPROVED")
            .limit(1)
            .get()
            .await()

        return userApprovedAuthorization.documents.firstOrNull()?.id
    }

    private suspend fun createPaymentProofUploadAuthorization(
        excursionId: String,
        authorizationId: String
    ): String {
        val currentUser = auth.currentUser
            ?: throw Exception("La sesion ha caducado. Inicia sesion de nuevo")

        try {
            currentUser.getIdToken(true).await()
        } catch (e: Exception) {
            throw Exception(
                "No se pudo renovar la sesion. Inicia sesion de nuevo",
                e
            )
        }

        val result = functions
            .getHttpsCallable("createPaymentProofUploadAuthorization")
            .call(
                mapOf(
                    "excursionId" to excursionId,
                    "authorizationId" to authorizationId,
                    "databaseId" to BuildConfig.FIRESTORE_DATABASE_ID
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val responseData = result.getData() as? Map<String, Any>
            ?: throw Exception("El servidor no autorizo la subida del comprobante")
        val uploadAuthorizationId = responseData["uploadAuthorizationId"] as? String

        if (uploadAuthorizationId.isNullOrBlank()) {
            throw Exception("El servidor no devolvio un permiso de subida valido")
        }

        return uploadAuthorizationId
    }

    /**
     * Obtener todos los pagos pendientes (para admin)
     */
    fun getPendingPayments(): Flow<List<Payment>> = flow {
        try {
            val snapshot = firestore.collection("payments")
                .whereIn("status", listOf(PaymentStatus.PENDING.name, PaymentStatus.PENDING.name.lowercase()))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val payments = snapshot.documents.mapNotNull { doc ->
                try {
                    Payment(
                        id = doc.id,
                        excursionId = doc.getString("excursionId") ?: "",
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        status = PaymentStatus.fromFirestoreValue(doc.getString("status")),
                        paymentProofUrl = doc.getString("paymentProofUrl"),
                        paymentProofContentType = doc.getString("paymentProofContentType"),
                        paymentProofFileName = doc.getString("paymentProofFileName")
                    )
                } catch (e: Exception) {
                    null
                }
            }

            emit(payments)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Obtener los pagos de una excursion (para admin)
     */
    fun getExcursionPayments(excursionId: String): Flow<List<Payment>> = flow {
        try {
            val snapshot = firestore.collection("payments")
                .whereEqualTo("excursionId", excursionId)
                .get()
                .await()

            val authorizationSnapshot = firestore.collection("signedAuthorizations")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            val participantNamesByUser = authorizationSnapshot.documents
                .groupBy { doc -> doc.getString("userId").orEmpty() }
                .mapValues { (_, authorizations) ->
                    authorizations.mapNotNull { authorization ->
                        authorization.getString("minorName")
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    }
                }

            val payments = snapshot.documents
                .filter { doc ->
                    PaymentStatus.fromFirestoreValue(doc.getString("status")) == PaymentStatus.PAID ||
                            !doc.getString("paymentProofUrl").isNullOrBlank()
                }
                .mapNotNull { doc ->
                    try {
                        val userId = doc.getString("userId") ?: ""
                        val storedParticipantNames = (doc.get("participantNames") as? List<*>)
                            .orEmpty()
                            .mapNotNull { name -> (name as? String)?.trim() }
                            .filter { name -> name.isNotEmpty() }

                        Payment(
                            id = doc.id,
                            excursionId = doc.getString("excursionId") ?: "",
                            userId = userId,
                            userName = doc.getString("userName") ?: "",
                            participantNames = storedParticipantNames.ifEmpty {
                                participantNamesByUser[userId].orEmpty()
                            },
                            amount = doc.getDouble("amount") ?: 0.0,
                            status = PaymentStatus.fromFirestoreValue(doc.getString("status")),
                            paymentProofUrl = doc.getString("paymentProofUrl"),
                            paymentProofContentType = doc.getString("paymentProofContentType"),
                            paymentProofFileName = doc.getString("paymentProofFileName"),
                            validatedBy = doc.getString("validatedBy")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .sortedBy { payment ->
                    when (payment.status) {
                        PaymentStatus.INITIATED -> 0
                        PaymentStatus.PENDING -> 0
                        PaymentStatus.REJECTED -> 1
                        PaymentStatus.PAID -> 2
                    }
                }

            emit(payments)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Validar o rechazar un pago (admin)
     */
    suspend fun updatePaymentStatus(
        paymentId: String,
        status: PaymentStatus
    ): Result<Unit> {
        return try {
            val adminId = auth.currentUser?.uid
                ?: return Result.failure(Exception("Admin no autenticado"))

            val updateData = hashMapOf<String, Any>(
                "status" to status.name,
                "validatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "validatedBy" to adminId
            )

            firestore.collection("payments")
                .document(paymentId)
                .update(updateData)
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NUEVO: Crear intención de pago con Redsys (TPV Virtual Cajasur)
     */
    suspend fun createPaymentIntent(
        excursionId: String,
        amount: Double,
        userName: String,
        userEmail: String
    ): PaymentIntentResult {
        val data = mapOf(
            "excursionId" to excursionId,
            "amount" to amount,
            "userName" to userName,
            "userEmail" to userEmail,
            "databaseId" to BuildConfig.FIRESTORE_DATABASE_ID
        )

        try {
            val callable = functions.getHttpsCallable("createPaymentIntent")
            val result = callable.call(data).await()

            // ✅ FIX: Usar getData() en lugar de data (propiedad privada en versiones recientes)
            @Suppress("UNCHECKED_CAST")
            val responseData = result.getData() as? Map<String, Any> ?: emptyMap()

            // Extraer campos con fallback seguro
            val orderId = responseData["orderId"] as? String ?: ""
            val tpvUrl = responseData["tpvUrl"] as? String ?: ""

            // Extraer params: puede ser Map<String, Any> o Map<String, String>
            val paramsRaw = responseData["params"]
            val params = when (paramsRaw) {
                is Map<*, *> -> paramsRaw.mapKeys { it.key as? String ?: "" }
                    .mapValues { it.value as? String ?: "" }
                else -> emptyMap()
            }

            if (orderId.isBlank() || tpvUrl.isBlank()) {
                throw Exception("La pasarela no devolvio una orden de pago valida")
            }

            if (
                params["Ds_MerchantParameters"].isNullOrBlank() ||
                params["Ds_Signature"].isNullOrBlank() ||
                params["Ds_SignatureVersion"].isNullOrBlank()
            ) {
                throw Exception("La pasarela no devolvio parametros de firma validos")
            }

            return PaymentIntentResult(
                orderId = orderId,
                tpvUrl = tpvUrl,
                params = params
            )

        } catch (e: Exception) {
            android.util.Log.e("PaymentRepo", "❌ Error en createPaymentIntent: ${e.message}", e)
            throw Exception("Error al crear intención de pago: ${e.message}")
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPayment(): Payment {
        return Payment(
            id = id,
            excursionId = getString("excursionId") ?: "",
            userId = getString("userId") ?: "",
            userName = getString("userName") ?: "",
            amount = getDouble("amount") ?: 0.0,
            status = PaymentStatus.fromFirestoreValue(getString("status")),
            paymentProofUrl = getString("paymentProofUrl"),
            paymentProofContentType = getString("paymentProofContentType"),
            paymentProofFileName = getString("paymentProofFileName"),
            validatedBy = getString("validatedBy")
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.paymentSortMillis(): Long {
        return getTimestamp("updatedAt")?.toDate()?.time
            ?: getTimestamp("processedAt")?.toDate()?.time
            ?: getTimestamp("createdAt")?.toDate()?.time
            ?: id.take(8).toLongOrNull()
            ?: 0L
    }
}

/**
 * Resultado de crear una intención de pago con Redsys
 */
data class PaymentIntentResult(
    val orderId: String,
    val tpvUrl: String,
    val params: Map<String, String>
)
