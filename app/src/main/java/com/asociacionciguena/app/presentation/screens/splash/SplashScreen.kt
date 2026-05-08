package com.asociacionciguena.app.presentation.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.asociacionciguena.app.R
import kotlinx.coroutines.delay

/**
 * Pantalla de splash inicial con dos fases: arte de marca y carga de cache.
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit = {},
    initialArtDurationMillis: Long = 3_000L
) {
    var showLoadingSplash by remember { mutableStateOf(false) }
    val transitionSpec = tween<Float>(
        durationMillis = 900,
        easing = FastOutSlowInEasing
    )
    val splashArtAlpha by animateFloatAsState(
        targetValue = if (showLoadingSplash) 0f else 1f,
        animationSpec = transitionSpec,
        label = "SplashArtAlpha"
    )
    val loadingSplashAlpha by animateFloatAsState(
        targetValue = if (showLoadingSplash) 1f else 0f,
        animationSpec = transitionSpec,
        label = "LoadingSplashAlpha"
    )

    LaunchedEffect(initialArtDurationMillis) {
        delay(initialArtDurationMillis)
        showLoadingSplash = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LoadingSplashScreen(
            modifier = Modifier.graphicsLayer {
                alpha = loadingSplashAlpha
            }
        )

        SplashArtScreen(
            modifier = Modifier.graphicsLayer {
                alpha = splashArtAlpha
            }
        )
    }
}

@Composable
private fun SplashArtScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.imagen_fondo_ciguena),
            contentDescription = "Logo Asociacion Ciguena",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LoadingSplashScreen(
    modifier: Modifier = Modifier
) {
    var visibleDots by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            visibleDots = (visibleDots + 1) % 4
            delay(420L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        LoadingSplashVideoBackground()

        LoadingText(
            visibleDots = visibleDots,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }
}

@Composable
private fun LoadingSplashVideoBackground() {
    val context = LocalContext.current
    val videoResourceId = remember(context) {
        context.resources.getIdentifier(
            "splash_loading_video",
            "raw",
            context.packageName
        )
    }

    if (videoResourceId == 0) return

    val player = remember(context, videoResourceId) {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = "android.resource://${context.packageName}/$videoResourceId"
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
        }
    )
}

@Composable
private fun LoadingText(
    visibleDots: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Cargando",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        repeat(3) { index ->
            Text(
                text = ".",
                color = if (index < visibleDots) Color.White else Color.Transparent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
