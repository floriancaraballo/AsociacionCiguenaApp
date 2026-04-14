package com.asociacionciguena.app.presentation.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale  // ← Importante para el escalado
import androidx.compose.ui.res.painterResource
import com.asociacionciguena.app.R

/**
 * Pantalla de splash (carga inicial) con imagen a tamaño completo
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.imagen_fondo_ciguena),
            contentDescription = "Logo Asociación Cigüeña",
            modifier = Modifier.fillMaxSize(),  // ← Imagen ocupa toda la pantalla
            contentScale = ContentScale.Crop     // ← Recorta la imagen para llenar sin deformar
            // Alternativas:
            // - ContentScale.FillBounds: estira la imagen (puede deformar)
            // - ContentScale.Fit: muestra la imagen completa dentro del contenedor (puede dejar espacios)
        )
    }

    LaunchedEffect(Unit) {
        onNavigateToMain()
    }
}
