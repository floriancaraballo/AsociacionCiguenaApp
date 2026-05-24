package com.asociacionciguena.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.tasks.await  // ← Ya deberías tenerlo

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
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val doc = snapshot?.documents?.firstOrNull()
                if (doc == null) {
                    trySend(null)
                } else {
                    trySend(
                        Payment(
                            id = doc.id,
                            excursionId = doc.getString("excursionId") ?: "",
                            userId = doc.getString("userId") ?: "",
                            userName = doc.getString("userName") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            status = PaymentStatus.fromFirestoreValue(doc.getString("status")),
                            paymentProofUrl = doc.getString("paymentProofUrl"),
                            paymentProofContentType = doc.getString("paymentProofContentType"),
                            paymentProofFileName = doc.getString("paymentProofFileName"),
                            validatedBy = doc.getString("validatedBy")
                        )
                    )
                }
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

            if (!canUserStartPayment(excursionId, userId)) {
                return Result.failure(Exception("No quedan plazas disponibles para esta excursión"))
            }

            val contentType = context.contentResolver.getType(photoUri)
                ?: return Result.failure(Exception("No se pudo identificar el tipo de archivo"))

            if (!isAllowedPaymentProofType(contentType)) {
                return Result.failure(Exception("Formato no permitido. Sube una imagen, PDF o documento Word"))
            }

            val fileName = getDisplayName(photoUri)
                ?: "comprobante_${UUID.randomUUID()}.${extensionForContentType(contentType)}"

            // 1. Subir comprobante a Storage
            val proofId = UUID.randomUUID().toString()
            val storageRef = storage.reference
                .child("payments/${excursionId}/${userId}_${proofId}.${extensionForContentType(contentType)}")

            val metadata = StorageMetadata.Builder()
                .setContentType(contentType)
                .build()

            storageRef.putFile(photoUri, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 2. Verificar si ya existe un pago
            val existingPayment = firestore.collection("payments")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            val paymentData = hashMapOf(
                "excursionId" to excursionId,
                "userId" to userId,
                "userName" to userName,
                "amount" to amount,
                "status" to PaymentStatus.PENDING.name,
                "paymentProofUrl" to downloadUrl,
                "paymentProofContentType" to contentType,
                "paymentProofFileName" to fileName,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            if (existingPayment.documents.isNotEmpty()) {
                val paymentUpdateData = hashMapOf(
                    "status" to PaymentStatus.PENDING.name,
                    "paymentProofUrl" to downloadUrl,
                    "paymentProofContentType" to contentType,
                    "paymentProofFileName" to fileName,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                val docId = existingPayment.documents[0].id
                firestore.collection("payments")
                    .document(docId)
                    .update(paymentUpdateData)
                    .await()
            } else {
                // Crear nuevo
                firestore.collection("payments")
                    .add(paymentData)
                    .await()
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

    private suspend fun canUserStartPayment(excursionId: String, userId: String): Boolean {
        val excursionDoc = firestore.collection("excursions")
            .document(excursionId)
            .get()
            .await()

        if (!excursionDoc.exists()) return false

        val activeStatuses = listOf("PENDING", "APPROVED")
        val userActiveAuthorization = firestore.collection("signedAuthorizations")
            .whereEqualTo("excursionId", excursionId)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        if (userActiveAuthorization.documents.any { doc -> doc.getString("status") in activeStatuses }) {
            return true
        }

        return true
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
                        paymentProofFileName = doc.getString("paymentProofFileName"),
                        validatedBy = doc.getString("validatedBy")
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { payment ->
                when (payment.status) {
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
            "userEmail" to userEmail
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
}

/**
 * Resultado de crear una intención de pago con Redsys
 */
data class PaymentIntentResult(
    val orderId: String,
    val tpvUrl: String,
    val params: Map<String, String>
)
