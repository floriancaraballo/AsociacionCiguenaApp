package com.asociacionciguena.app.presentation

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.asociacionciguena.app.presentation.navigation.AppNavigation
import com.asociacionciguena.app.presentation.theme.AsociacionCiguenaTheme
import com.asociacionciguena.app.presentation.theme.ThemeViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Activity principal de la aplicación
 * Punto de entrada de la UI
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), ImageLoaderFactory {

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instalar Splash Screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // La app se mantiene en vertical por defecto.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Habilitar edge-to-edge (pantalla completa moderna)
        enableEdgeToEdge()

        // Refrescar token de Firebase Auth al abrir app
        lifecycleScope.launch {
            try {
                auth.currentUser?.reload()?.await()
            } catch (e: Exception) {
                // Ignorar si falla
            }
        }

        // Extraer datos de notificación
        val notificationType = intent?.getStringExtra("notification_type")
        val itemId = intent?.getStringExtra("item_id")

        android.util.Log.d("DEEP_LINK", "onCreate - Type: $notificationType, ID: $itemId")

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()

            AsociacionCiguenaTheme(
                darkTheme = isDarkMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        notificationType = notificationType,
                        itemId = itemId
                    )
                }
            }
        }
    }

    /**
     * Manejar intent cuando la app ya está abierta
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val notificationType = intent.getStringExtra("notification_type")
        val itemId = intent.getStringExtra("item_id")

        android.util.Log.d("DEEP_LINK", "onNewIntent - Type: $notificationType, ID: $itemId")

        // Actualizar intent y recrear para procesar
        setIntent(intent)
        recreate()
    }
}
