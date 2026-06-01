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
import com.asociacionciguena.app.AsociacionCiguenaApp
import com.asociacionciguena.app.R
import com.asociacionciguena.app.presentation.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
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
        // ✅ Usar la constante de AsociacionCiguenaApp para evitar desincronización
        private const val CHANNEL_ID = AsociacionCiguenaApp.NOTIFICATION_CHANNEL_ID
        private const val CHANNEL_NAME = AsociacionCiguenaApp.NOTIFICATION_CHANNEL_NAME
        private const val NOTIFICATION_ID = 1

        // ✅ NUEVO: Topics para segmentar notificaciones
        private const val TOPIC_PUBLIC = "public"
        private const val TOPIC_AUTHENTICATED = "authenticated"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        android.util.Log.d("FCM_TOPIC", "🔑 Nuevo token generado: ${token.take(20)}...")

        // ✅ 1. Suscribirse SIEMPRE a topic público (noticias/calendario para todos)
        subscribeToTopic(TOPIC_PUBLIC)

        // ✅ 2. Si hay usuario logueado, suscribir a topic privado (fotos)
        if (auth.currentUser != null) {
            subscribeToTopic(TOPIC_AUTHENTICATED)
            subscribeToTopic(userNotificationTopic(auth.currentUser!!.uid))
            android.util.Log.d("FCM_TOPIC", "✅ Usuario logueado: suscrito a '$TOPIC_AUTHENTICATED'")
        }

        // ✅ 3. Guardar token en Firestore
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // ✅ LOGS CRÍTICOS
        android.util.Log.d("FCM_LOCKSCREEN", "📨 onMessageReceived llamado")
        android.util.Log.d("FCM_LOCKSCREEN", "📦 message.data completo: ${message.data}")
        android.util.Log.d("FCM_LOCKSCREEN", "📝 title: '${message.data["title"]}'")
        android.util.Log.d("FCM_LOCKSCREEN", "📄 body: '${message.data["body"]}'")
        android.util.Log.d("FCM_LOCKSCREEN", "🏷️ type: '${message.data["type"]}'")
        android.util.Log.d("FCM_LOCKSCREEN", "🆔 itemId: '${message.data["itemId"]}'")

        createNotificationChannel()

        val title = message.data["title"] ?: "Nueva notificación"
        val body = message.data["body"] ?: ""
        val type = message.data["type"]
        val itemId = message.data["itemId"]
        android.util.Log.d("FCM_LOCKSCREEN", "🔍 Valores finales: title='$title', body='$body'")

        showNotification(title, body, type, itemId)
    }

    // ✅ NUEVO: Métodos para gestionar suscripción a topics
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM_TOPIC", "✅ Suscrito a topic: $topic")
                } else {
                    android.util.Log.e("FCM_TOPIC", "❌ Error al suscribir a $topic: ${task.exception?.message}")
                }
            }
    }

    private fun userNotificationTopic(userId: String): String {
        return "user_${userId.replace(Regex("[^A-Za-z0-9_\\-.~%]"), "_")}"
    }

    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM_TOPIC", "✅ Desuscrito de topic: $topic")
                } else {
                    android.util.Log.e("FCM_TOPIC", "❌ Error al desuscribir de $topic: ${task.exception?.message}")
                }
            }
    }

    // ✅ NUEVO: Llamar cuando el usuario se loguea
    fun onUserLogin() {
        android.util.Log.d("FCM_TOPIC", "🔐 Usuario logueado: suscribiendo a '$TOPIC_AUTHENTICATED'")
        subscribeToTopic(TOPIC_AUTHENTICATED)
        auth.currentUser?.uid?.let { subscribeToTopic(userNotificationTopic(it)) }

        // Actualizar token en Firestore por si acaso
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                saveTokenToFirestore(task.result)
            }
        }
    }

    // ✅ NUEVO: Llamar cuando el usuario se desloguea
    fun onUserLogout() {
        android.util.Log.d("FCM_TOPIC", "🔓 Usuario deslogueado: desuscribiendo de '$TOPIC_AUTHENTICATED'")
        unsubscribeFromTopic(TOPIC_AUTHENTICATED)
        auth.currentUser?.uid?.let { unsubscribeFromTopic(userNotificationTopic(it)) }
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or  // ✅ Reusa instancia existente si hay
                        Intent.FLAG_ACTIVITY_SINGLE_TOP    // ✅ Evita recrear si ya está en top
                putExtra("notification_type", type)
                putExtra("item_id", itemId)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )


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
                .setSmallIcon(R.drawable.ic_notification_dark)
                .setColor(android.graphics.Color.parseColor("#1976D2"))
                .setColorized(true)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // ✅ CONFIGURACIÓN EXTRA PARA LOCKSCREEN
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setUsesChronometer(false)
                // ✅ NOTIFICACIÓN COMPLETA
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                //.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                //.setVibrate(longArrayOf(0, 500, 200, 500))

            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon)
            }

            // ✅ CLAVE: Versión PÚBLICA con MISMO estilo que la principal
            val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_dark)  // ← Mismo icono
                .setContentTitle(title)                          // ← Mismo título
                .setContentText(body)                            // ← Mismo cuerpo
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))  // ← ✅ ESTO FALTABA
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)       // ← Visibilidad pública
                .build()

            // Aplicar al builder principal
            notificationBuilder.setPublicVersion(publicVersion)

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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE

                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

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
        val userId = auth.currentUser?.uid

        android.util.Log.d("FCM_TOKEN", "💾 Guardando token para userId: $userId")

        if (userId == null) {
            android.util.Log.w("FCM_TOKEN", "⚠️ No hay usuario logueado, solo se guarda para analytics")
            // ✅ No retornamos: guardamos el token aunque no haya usuario (para posibles usos futuros)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("users").document(userId ?: "anonymous_${token.take(8)}")

                // ✅ CAMBIO CLAVE: Usar set() con merge en lugar de update()
                // Esto crea el documento si no existe, o actualiza si ya existe
                userRef.set(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTopics" to listOfNotNull(
                            TOPIC_PUBLIC,
                            if (auth.currentUser != null) TOPIC_AUTHENTICATED else null,
                            userId?.let { userNotificationTopic(it) }
                        ),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                    .await()

                android.util.Log.d("FCM_TOKEN", "✅ Token guardado en Firestore")
            } catch (e: Exception) {
                android.util.Log.e("FCM_TOKEN", "❌ Error guardando token: ${e.message}", e)
            }
        }
    }
}
