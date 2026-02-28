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
import com.asociacionciguena.app.presentation.theme.AsociacionCigueñaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity principal de la aplicación
 * Punto de entrada de la UI
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instalar Splash Screen
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Habilitar edge-to-edge (pantalla completa moderna)
        enableEdgeToEdge()

        setContent {
            AsociacionCigueñaTheme {
                Surface(
                    modifier = Modifier.Companion.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}