package com.asociacionciguena.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

@Composable
fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onScaleChange: ((Float) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    // Usar Animatable directamente para scale y offset
    val animatedScale = remember { Animatable(1f) }
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

    LaunchedEffect(animatedScale.value) {
        onScaleChange?.invoke(animatedScale.value)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Doble tap para zoom
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (animatedScale.value > 1f) {
                                // Si hay zoom, resetear a 1x con animación gradual
                                animatedScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 400)
                                )
                                animatedOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 400)
                                )
                                animatedOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 400)
                                )
                            } else {
                                // Si no hay zoom, hacer zoom 2.5x con animación gradual
                                val newScale = 2.5f

                                // Calcular offset para centrar en el punto del tap
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val offsetX = (centerX - tapOffset.x) * (newScale - 1)
                                val offsetY = (centerY - tapOffset.y) * (newScale - 1)

                                val maxX = (size.width * (newScale - 1)) / 2
                                val maxY = (size.height * (newScale - 1)) / 2

                                // Animar zoom y offset simultáneamente
                                launch {
                                    animatedScale.animateTo(
                                        targetValue = newScale,
                                        animationSpec = tween(durationMillis = 400)
                                    )
                                }
                                launch {
                                    animatedOffsetX.animateTo(
                                        targetValue = offsetX.coerceIn(-maxX, maxX),
                                        animationSpec = tween(durationMillis = 400)
                                    )
                                }
                                launch {
                                    animatedOffsetY.animateTo(
                                        targetValue = offsetY.coerceIn(-maxY, maxY),
                                        animationSpec = tween(durationMillis = 400)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            // Pinch zoom y pan
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (zoom != 1f) {
                            // Hay gesto de zoom (pinch) - actualizar directamente sin animación
                            coroutineScope.launch {
                                val newScale = (animatedScale.value * zoom).coerceIn(1f, 5f)
                                animatedScale.snapTo(newScale)
                            }
                        }

                        // Solo permitir pan si está en zoom
                        if (animatedScale.value > 1f && pan != Offset.Zero) {
                            coroutineScope.launch {
                                val maxX = (size.width * (animatedScale.value - 1)) / 2
                                val maxY = (size.height * (animatedScale.value - 1)) / 2

                                val newOffsetX = (animatedOffsetX.value + pan.x).coerceIn(-maxX, maxX)
                                val newOffsetY = (animatedOffsetY.value + pan.y).coerceIn(-maxY, maxY)

                                animatedOffsetX.snapTo(newOffsetX)
                                animatedOffsetY.snapTo(newOffsetY)
                            }
                        } else if (animatedScale.value == 1f) {
                            coroutineScope.launch {
                                animatedOffsetX.snapTo(0f)
                                animatedOffsetY.snapTo(0f)
                            }
                        }

                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale.value,
                    scaleY = animatedScale.value,
                    translationX = animatedOffsetX.value,
                    translationY = animatedOffsetY.value
                ),
            contentScale = contentScale,
            loading = {
                ImageLoadingPlaceholderLarge()  // ← Usar versión grande
            }
        )
    }
}