package com.asociacionciguena.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {

    /**
     * Verificar si el permiso de notificaciones está concedido
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android < 13 no requiere permiso runtime
        }
    }

    /**
     * Solicitar permiso de notificaciones
     */
    fun requestNotificationPermission(
        activity: ComponentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    onGranted()
                } else {
                    onDenied()
                }
            }

            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onGranted() // No se requiere permiso
        }
    }
}