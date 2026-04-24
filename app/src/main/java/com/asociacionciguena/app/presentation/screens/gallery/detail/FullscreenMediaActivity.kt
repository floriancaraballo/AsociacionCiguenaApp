package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.ZoomableImage
import com.asociacionciguena.app.presentation.theme.AsociacionCiguenaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FullscreenMediaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AsociacionCiguenaTheme {
                FullscreenMediaRoute(onClose = { finish() })
            }
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            excursionId: String,
            photoId: String
        ): Intent {
            return Intent(context, FullscreenMediaActivity::class.java).apply {
                putExtra("excursionId", excursionId)
                putExtra("photoId", photoId)
            }
        }
    }
}

@Composable
private fun FullscreenMediaRoute(
    viewModel: FullscreenMediaViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is FullscreenMediaUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        is FullscreenMediaUiState.Error -> {
            ErrorMessage(
                message = state.message,
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize()
            )
        }

        is FullscreenMediaUiState.Success -> {
            FullscreenMediaScreen(
                photos = state.photos,
                initialIndex = state.initialIndex,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun FullscreenMediaScreen(
    photos: List<Photo>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${photos.size}") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.72f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            val photo = photos[page]
            if (photo.mediaType == "video") {
                FullscreenVideoPlayer(
                    videoUrl = ensureVideoUrlFormat(photo.imageUrl),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ZoomableImage(
                    imageUrl = photo.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun FullscreenVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = androidx.compose.runtime.remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

private fun ensureVideoUrlFormat(url: String): String {
    if (url.contains("alt=media")) return url
    if (url.contains("firebasestorage.googleapis.com")) {
        return "${url}${if (url.contains("?")) "&" else "?"}alt=media"
    }
    return url
}
