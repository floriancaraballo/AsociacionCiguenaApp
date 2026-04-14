package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.domain.model.Photo
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFullscreenDialog(
    photo: Photo,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    // ✅ Logs para debuggear
    android.util.Log.d("VIDEO_DEBUG", "🎬 Opening video: ${photo.id}")
    android.util.Log.d("VIDEO_DEBUG", "🔍 mediaType: ${photo.mediaType}")
    android.util.Log.d("VIDEO_DEBUG", "🔍 imageUrl: ${photo.imageUrl}")
    android.util.Log.d("VIDEO_DEBUG", "🔍 thumbnailUrl: ${photo.thumbnailUrl}")

    val context = LocalContext.current

    // Estado para zoom y pan (solo para imágenes)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val exoPlayer = remember(photo.id) {
        if (photo.mediaType == "video") {
            // ✅ Formatear URL para streaming
            val videoUrl = ensureVideoUrlFormat(photo.imageUrl)

            // ✅ Configurar factory con headers para Firebase Storage
            val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                setUserAgent("ExoPlayer/${photo.id}")  // ← User-Agent único
                setAllowCrossProtocolRedirects(true)    // ← Permitir redirecciones
                setConnectTimeoutMs(30000)              // ← Timeout de 30s
                setReadTimeoutMs(30000)                 // ← Timeout de lectura
            }

            // ✅ Crear media source con la factory configurada
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl))

            // ✅ Construir player con el media source
            ExoPlayer.Builder(context).build().apply {
                setMediaSource(mediaSource)  // ← Usar mediaSource en lugar de fromUri directo
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE

                // ✅ Listener para debuggear errores
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> android.util.Log.d("VIDEO_DEBUG", "✅ Ready: ${photo.id}")
                            Player.STATE_BUFFERING -> android.util.Log.d("VIDEO_DEBUG", "⏳ Buffering...")
                            Player.STATE_ENDED -> android.util.Log.d("VIDEO_DEBUG", "✅ Ended")
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("VIDEO_DEBUG", "❌ Error: ${error.errorCodeName} - ${error.message}")
                        android.util.Log.e("VIDEO_DEBUG", "🔍 URL: $videoUrl")
                    }
                })
            }
        } else {
            null
        }
    }

    // Liberar player al cerrar
    DisposableEffect(photo.id) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Contenido: Imagen o Vídeo
            if (photo.mediaType == "video" && exoPlayer != null) {
                // REPRODUCTOR DE VÍDEO
                AndroidView(
                    factory = { ctx ->
                        android.util.Log.d("VIDEO_DEBUG", "🎬 Creando PlayerView para: ${photo.id}")
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerShowTimeoutMs = 3000  // ✅ Mostrar controles 3s antes de ocultar
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // IMAGEN CON ZOOM
                SubcomposeAsyncImage(
                    model = photo.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)

                                if (scale > 1f) {
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(
                                            -size.width * (scale - 1) / 2,
                                            size.width * (scale - 1) / 2
                                        ),
                                        y = (offset.y + pan.y).coerceIn(
                                            -size.height * (scale - 1) / 2,
                                            size.height * (scale - 1) / 2
                                        )
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                )
            }

            // TopBar con botones
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }

                Row {
                    if (photo.mediaType == "image") {
                        // Botón reset zoom (solo para imágenes)
                        if (scale > 1f) {
                            IconButton(onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = "Reset Zoom",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Botón eliminar (solo si está permitido)
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Indicador de tipo de media
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (photo.mediaType == "video") Icons.Default.Videocam else Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (photo.mediaType == "video") "Vídeo" else "Imagen",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ✅ Función auxiliar para asegurar que la URL tiene el formato correcto para streaming
private fun ensureVideoUrlFormat(url: String): String {
    // Si ya tiene alt=media, devolver tal cual
    if (url.contains("alt=media")) return url

    // Si es URL de Firebase Storage, añadir alt=media
    if (url.contains("firebasestorage.googleapis.com")) {
        return "${url}${if (url.contains("?")) "&" else "?"}alt=media"
    }

    return url
}