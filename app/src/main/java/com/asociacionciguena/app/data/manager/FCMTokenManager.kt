package com.asociacionciguena.app.data.manager

import android.os.Build
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
        val userId = auth.currentUser?.uid

        // Guardar en deviceTokens (SIEMPRE, incluso sin login)
        try {
            firestore.collection("deviceTokens")
                .document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "userId" to userId,
                        "lastUsedAt" to com.google.firebase.Timestamp.now(),
                        "deviceInfo" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FCMTokenManager", "Error guardando token: ${e.message}")
        }
    }

    /**
     * Eliminar token (al cerrar sesión)
     */
    suspend fun deleteToken() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            try {
                // En deviceTokens, solo marcar como sin usuario (no borrar)
                val token = messaging.token.await()
                firestore.collection("deviceTokens")
                    .document(token)
                    .update("userId", null)
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("FCMTokenManager", "Error eliminando token: ${e.message}")
            }
        }
    }
}