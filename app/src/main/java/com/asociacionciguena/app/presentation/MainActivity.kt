package com.asociacionciguena.app.presentation

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.asociacionciguena.app.presentation.navigation.AppNavigation
import com.asociacionciguena.app.presentation.screens.main.MainViewModel
import com.asociacionciguena.app.presentation.theme.AsociacionCiguenaTheme
import com.asociacionciguena.app.presentation.theme.PrimaryDark
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
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mantener compatibilidad con SplashScreen API sin retener el splash nativo.
        installSplashScreen()

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

        // Extraer datos de notificación (soportando tanto foreground como background FCM payloads)
        val notificationType = intent?.getStringExtra("notification_type") ?: intent?.getStringExtra("type")
        val itemId = intent?.getStringExtra("item_id") ?: intent?.getStringExtra("itemId")

        android.util.Log.d("DEEP_LINK", "onCreate - Type: $notificationType, ID: $itemId")

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()

            AsociacionCiguenaTheme(
                darkTheme = isDarkMode
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            mainViewModel = mainViewModel,
                            notificationType = notificationType,
                            itemId = itemId
                        )
                    }

                    // Covers transparent system-bar and display-cutout areas on OEM devices.
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.safeDrawing)
                            .background(PrimaryDark)
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

        val notificationType = intent.getStringExtra("notification_type") ?: intent.getStringExtra("type")
        val itemId = intent.getStringExtra("item_id") ?: intent.getStringExtra("itemId")

        android.util.Log.d("DEEP_LINK", "onNewIntent - Type: $notificationType, ID: $itemId")

        // Actualizar intent y recrear para procesar
        setIntent(intent)
        recreate()
    }
}
