package com.asociacionciguena.app.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import com.asociacionciguena.app.R
import com.asociacionciguena.app.presentation.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseMessagingService : FirebaseMessagingService() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    companion object {
        private const val CHANNEL_ID = "asociacion_ciguena_notifications"
        private const val CHANNEL_NAME = "Notificaciones Generales"
        private const val NOTIFICATION_ID = 1
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        android.util.Log.d("FCM_NOTIF", "📨 Mensaje recibido")
        android.util.Log.d("FCM_NOTIF", "Data: ${message.data}")
        android.util.Log.d("FCM_NOTIF", "Notification: ${message.notification}")

        // Crear canal de notificación
        createNotificationChannel()

        // IMPORTANTE: Ahora SOLO usamos data (no notification)
        val title = message.data["title"] ?: "Nueva notificación"
        val body = message.data["body"] ?: ""
        val type = message.data["type"]
        val itemId = message.data["itemId"]

        android.util.Log.d("FCM_NOTIF", "Title: $title, Body: $body, Type: $type")

        // Mostrar notificación
        showNotification(title, body, type, itemId)
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        itemId: String?
    ) {
        try {
            android.util.Log.d("FCM_NOTIF", "📱 Mostrando notificación: $title")

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notification_type", type)
                putExtra("item_id", itemId)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Cargar large icon - usar launcher como backup seguro
            val largeIcon = try {
                val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_notificacion_ciguena)
                if (bitmap != null) {
                    android.util.Log.d("FCM_NOTIF", "✅ Icono cigüeña cargado")
                    bitmap
                } else {
                    android.util.Log.w("FCM_NOTIF", "⚠️ Icono cigüeña es null, usando launcher")
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                }
            } catch (e: Exception) {
                android.util.Log.e("FCM_NOTIF", "❌ Error cargando icono: ${e.message}")
                BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            }

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(android.graphics.Color.parseColor("#FFFFFF"))// ← Fondo blanco (opcional, depende del launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 500, 200, 500))

            // Solo añadir large icon si no es null
            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon)
            }

            val notification = notificationBuilder.build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

            android.util.Log.d("FCM_NOTIF", "✅ Notificación mostrada")

        } catch (e: Exception) {
            android.util.Log.e("FCM_NOTIF", "❌ Error total en showNotification: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de publicaciones, excursiones y fotos"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Recortar bitmap en círculo perfecto
     */
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val rect = Rect(0, 0, size, size)
        val rectF = android.graphics.RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, paint)

        return output
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .await()
            } catch (e: Exception) {
                // Log error
            }
        }
    }
}