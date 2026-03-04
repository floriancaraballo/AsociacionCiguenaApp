package com.asociacionciguena.app.data.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FCMTokenManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {

    /**
     * Obtener y guardar el token FCM del dispositivo
     */
    suspend fun refreshToken(): Result<String> {
        return try {
            val token = messaging.token.await()
            saveTokenToFirestore(token)
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Guardar token en Firestore
     */
    private suspend fun saveTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return

        try {
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()
        } catch (e: Exception) {
            // Si el documento no existe, intentar set con merge
            firestore.collection("users")
                .document(userId)
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }
    }

    /**
     * Eliminar token (al cerrar sesión)
     */
    suspend fun deleteToken() {
        val userId = auth.currentUser?.uid ?: return

        try {
            messaging.deleteToken().await()

            firestore.collection("users")
                .document(userId)
                .update("fcmToken", null)
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}