package com.asociacionciguena.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.asociacionciguena.app.di.CoilImageLoaderFactory
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp
import android.app.Notification

/**
 * Application class principal de la app
 * Anotada con @HiltAndroidApp para habilitar Hilt
 */
@HiltAndroidApp
class AsociacionCiguenaApp : Application(), ImageLoaderFactory {

    companion object {
        // ✅ Constantes compartidas para notificaciones (deben coincidir en todo el proyecto)
        const val NOTIFICATION_CHANNEL_ID = "asociacion_ciguena_notifications_v4"
        const val NOTIFICATION_CHANNEL_NAME = "Notificaciones Generales"
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.PLACES_API_KEY)
        }
        // ✅ Crear canal de notificaciones al inicio de la app
        // Esto garantiza que exista antes de que Firebase intente usarlo
        createNotificationChannel()
    }

    override fun newImageLoader(): ImageLoader {
        return CoilImageLoaderFactory.create(this)
    }

    /**
     * Crea el canal de notificaciones con configuración correcta para lockscreen
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de publicaciones, excursiones y fotos"
                // ✅ CLAVE: Visibilidad pública para mostrar contenido en lockscreen
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
