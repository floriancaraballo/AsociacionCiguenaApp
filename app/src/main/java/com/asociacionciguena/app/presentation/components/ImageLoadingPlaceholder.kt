package com.asociacionciguena.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface

/**
 * Placeholder elegante para imágenes mientras cargan
 * Usa efecto shimmer consistente con skeleton loading
 */
@Composable
fun ImageLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ShimmerBox(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Placeholder para imágenes grandes (fullscreen)
 * Spinner moderno con fondo difuminado
 */
@Composable
fun ImageLoadingPlaceholderLarge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Fondo oscuro sutil
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // Círculo de fondo difuminado
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 4.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}