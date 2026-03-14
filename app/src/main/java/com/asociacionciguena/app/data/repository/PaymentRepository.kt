package com.asociacionciguena.app.data.repository

import android.net.Uri
import com.asociacionciguena.app.domain.model.Payment
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) {

    /**
     * Obtener estado de pago de un usuario para una excursión
     */
    fun getPaymentStatus(excursionId: String, userId: String): Flow<Payment?> = flow {
        try {
            val snapshot = firestore.collection("payments")
                .whereEqualTo("excursionId", excursionId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            if (snapshot.documents.isNotEmpty()) {
                val doc = snapshot.documents[0]
                val payment = Payment(
                    id = doc.id,
                    excursionId = doc.getString("excursionId") ?: "",
                    userId = doc.getString("userId") ?: "",
                    userName = doc.getString("userName") ?: "",
                    amount = doc.getDouble("amount") ?: 0.0,
                    status = PaymentStatus.valueOf(
                        doc.getString("status") ?: "PENDING"
                    ),
                    paymentProofUrl = doc.getString("paymentProofUrl"),
                    validatedBy = doc.getString("validatedBy")
                )
                emit(payment)
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            emit(null)
        }
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

            // 1. Subir imagen a Storage
            val imageId = UUID.randomUUID().toString()
            val storageRef = storage.reference
                .child("payments/${excursionId}/${userId}_${imageId}.jpg")

            storageRef.putFile(photoUri).await()
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
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            if (existingPayment.documents.isNotEmpty()) {
                // Actualizar existente
                val docId = existingPayment.documents[0].id
                firestore.collection("payments")
                    .document(docId)
                    .update(paymentData)
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

    /**
     * Obtener todos los pagos pendientes (para admin)
     */
    fun getPendingPayments(): Flow<List<Payment>> = flow {
        try {
            val snapshot = firestore.collection("payments")
                .whereEqualTo("status", PaymentStatus.PENDING.name)
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
                        status = PaymentStatus.valueOf(
                            doc.getString("status") ?: "PENDING"
                        ),
                        paymentProofUrl = doc.getString("paymentProofUrl")
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
}