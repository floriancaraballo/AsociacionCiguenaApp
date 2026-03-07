package com.asociacionciguena.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Modificador que añade efecto shimmer (brillo animado)
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,  // ← Más lento
                easing = FastOutSlowInEasing  // ← Easing más suave
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),  // ← Más transparente
        Color.LightGray.copy(alpha = 0.5f),  // ← Pico más sutil
        Color.LightGray.copy(alpha = 0.3f),  // ← Más transparente
    )

    val brush = Brush.horizontalGradient(  // ← Horizontal en lugar de diagonal
        colors = shimmerColors,
        startX = translateAnim.value - 300f,
        endX = translateAnim.value + 300f  // ← Gradiente más amplio
    )

    background(brush)
}

/**
 * Box con efecto shimmer
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

/**
 * Espaciador con efecto shimmer (para texto)
 */
@Composable
fun ShimmerText(
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp)
    )
}

/**
 * Círculo con efecto shimmer (para avatares)
 */
@Composable
fun ShimmerCircle(
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier,
        shape = CircleShape
    )
}