package com.asociacionciguena.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.asociacionciguena.app.presentation.navigation.AppNavigation
import com.asociacionciguena.app.presentation.theme.AsociacionCiguenaTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.asociacionciguena.app.presentation.screens.main.MainScreen
import com.asociacionciguena.app.presentation.theme.ThemeViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

/**
 * Activity principal de la aplicación
 * Punto de entrada de la UI
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject  // ← NUEVO
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instalar Splash Screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

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

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()

            AsociacionCiguenaTheme(
                darkTheme = isDarkMode  // ← Aplicar tema
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()  // ← IMPORTANTE: Ir a AppNavigation, NO a MainScreen
                }
            }
        }
    }
}