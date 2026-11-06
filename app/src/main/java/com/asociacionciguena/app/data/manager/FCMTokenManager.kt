package com.asociacionciguena.app.data.manager

import android.os.Build
import com.asociacionciguena.app.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FCMTokenManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val topicEnvironment = BuildConfig.NOTIFICATION_ENVIRONMENT
    private val publicTopic = "public_$topicEnvironment"
    private val authenticatedTopic = "authenticated_$topicEnvironment"

    /**
     * Synchronize the FCM token without tying network work to a screen lifecycle.
     */
    fun refreshTokenInBackground() {
        backgroundScope.launch {
            refreshToken().onFailure { error ->
                android.util.Log.w(
                    "FCMTokenManager",
                    "No se pudo sincronizar el token FCM en segundo plano: ${error.message}"
                )
            }
        }
    }

    /**
     * Obtener y guardar el token FCM del dispositivo
     */
    suspend fun refreshToken(): Result<String> {
        return try {
            val userId = auth.currentUser?.uid

            if (userId != null && shouldRotateInvalidatedToken(userId)) {
                messaging.deleteToken().await()
            }

            val token = messaging.token.await()
            saveTokenToFirestore(token)
            subscribeToNotificationTopics(userId)
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

        try {
            saveDeviceToken(token, userId)

            if (userId != null) {
                saveUserNotificationToken(userId, token)
            }
        } catch (e: Exception) {
            android.util.Log.e("FCMTokenManager", "Error guardando token: ${e.message}")
        }
    }

    private suspend fun saveDeviceToken(token: String, userId: String?) {
        val tokenData = mapOf(
            "token" to token,
            "userId" to userId,
            "invalid" to false,
            "lastUsedAt" to FieldValue.serverTimestamp(),
            "deviceInfo" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("deviceTokens")
            .document(token)
            .set(tokenData, SetOptions.merge())
            .await()

    }

    private suspend fun shouldRotateInvalidatedToken(userId: String): Boolean {
        return try {
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            val invalidatedAt = userDoc.getTimestamp("fcmTokenInvalidatedAt")
            val updatedAt = userDoc.getTimestamp("fcmTokenUpdatedAt")

            invalidatedAt != null && isSameOrAfter(invalidatedAt, updatedAt)
        } catch (e: Exception) {
            android.util.Log.e("FCMTokenManager", "Error comprobando token invalidado: ${e.message}")
            false
        }
    }

    private fun isSameOrAfter(timestamp: Timestamp, reference: Timestamp?): Boolean {
        return reference == null ||
            timestamp.seconds > reference.seconds ||
            (timestamp.seconds == reference.seconds && timestamp.nanoseconds >= reference.nanoseconds)
    }

    private suspend fun saveUserNotificationToken(userId: String, token: String) {
        val tokenData = mapOf(
            "fcmToken" to token,
            "fcmTopics" to listOf(publicTopic, authenticatedTopic, userNotificationTopic(userId)),
            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
            "fcmTokenInvalidatedAt" to FieldValue.delete()
        )

        firestore.collection("users")
            .document(userId)
            .set(tokenData, SetOptions.merge())
            .await()

    }

    /**
     * Eliminar token (al cerrar sesión)
     */
    private suspend fun subscribeToNotificationTopics(userId: String?) {
        // Quita la suscripción compartida anterior, que mezclaba instalaciones debug/release.
        messaging.unsubscribeFromTopic("public").await()
        messaging.unsubscribeFromTopic("authenticated").await()
        messaging.subscribeToTopic(publicTopic).await()

        if (userId != null) {
            messaging.subscribeToTopic(authenticatedTopic).await()
            messaging.subscribeToTopic(userNotificationTopic(userId)).await()
        }
    }

    private fun userNotificationTopic(userId: String): String {
        return "user_${topicEnvironment}_${userId.replace(Regex("[^A-Za-z0-9_\\-.~%]"), "_")}"
    }

    suspend fun deleteToken() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            try {
                // En deviceTokens, solo marcar como sin usuario (no borrar)
                val token = messaging.token.await()
                clearDeviceTokenUser(token)

                clearUserNotificationToken(userId)
                messaging.unsubscribeFromTopic(userNotificationTopic(userId)).await()
                messaging.unsubscribeFromTopic(authenticatedTopic).await()
            } catch (e: Exception) {
                android.util.Log.e("FCMTokenManager", "Error eliminando token: ${e.message}")
            }
        }
    }

    private suspend fun clearDeviceTokenUser(token: String) {
        val clearData = mapOf(
            "userId" to null,
            "lastUsedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("deviceTokens")
            .document(token)
            .set(clearData, SetOptions.merge())
            .await()

    }

    private suspend fun clearUserNotificationToken(userId: String) {
        val clearData = mapOf(
            "fcmToken" to FieldValue.delete(),
            "fcmTopics" to FieldValue.delete(),
            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
            "fcmTokenInvalidatedAt" to FieldValue.delete()
        )

        firestore.collection("users")
            .document(userId)
            .update(clearData)
            .await()

    }
}
