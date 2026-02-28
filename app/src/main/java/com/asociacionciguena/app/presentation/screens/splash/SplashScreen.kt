package com.asociacionciguena.app.presentation.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
/**
 * Pantalla de splash (carga inicial)
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit  // ← Cambiado de onNavigateToNews
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Asociación\nCigüeña",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    LaunchedEffect(Unit) {
        delay(2000)
        onNavigateToMain()  // ← Cambiado
    }
}