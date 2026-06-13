package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FullscreenMediaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        setContent {
            AsociacionCiguenaTheme {
                FullscreenMediaRoute(onClose = { finish() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val downloadingPhotoId by viewModel.downloadingPhotoId.collectAsState()
    var pendingPermissionPhoto by remember { mutableStateOf<Photo?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingPhoto = pendingPermissionPhoto
        pendingPermissionPhoto = null
        if (granted && pendingPhoto != null) {
            downloadMedia(context, pendingPhoto, viewModel)
        } else if (!granted) {
            Toast.makeText(
                context,
                "Se necesita permiso para guardar el archivo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FullscreenMediaEvent.PhotoDownloaded -> {
                    val saved = withContext(Dispatchers.IO) {
                        savePhotoToGallery(context, event.photo, event.bytes)
                    }
                    Toast.makeText(
                        context,
                        if (saved) {
                            "Foto guardada en la galeria"
                        } else {
                            "No se pudo guardar la foto"
                        },
                        if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }

                is FullscreenMediaEvent.DownloadError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val onDownloadMedia: (Photo) -> Unit = { photo ->
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED

        if (needsLegacyPermission) {
            pendingPermissionPhoto = photo
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            downloadMedia(context, photo, viewModel)
        }
    }

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
                downloadingPhotoId = downloadingPhotoId,
                onDownloadMedia = onDownloadMedia,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun FullscreenMediaScreen(
    photos: List<Photo>,
    initialIndex: Int,
    downloadingPhotoId: String?,
    onDownloadMedia: (Photo) -> Unit,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )
    var controlsVisible by remember { mutableStateOf(true) }
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, pagerState.currentPage) {
        if (controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        controlsVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
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
                    contentScale = ContentScale.Fit,
                    onTap = { controlsVisible = !controlsVisible },
                    onScaleChange = { scale -> isZoomed = scale > 1.01f }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size}",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                val currentPhoto = photos[pagerState.currentPage]
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    IconButton(
                        onClick = { onDownloadMedia(currentPhoto) },
                        enabled = downloadingPhotoId == null
                    ) {
                        if (downloadingPhotoId == currentPhoto.id) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = if (currentPhoto.mediaType == "video") {
                                    "Descargar video"
                                } else {
                                    "Descargar foto"
                                },
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L
private const val PHOTO_DIRECTORY = "Asociacion Ciguena"

private fun downloadMedia(
    context: Context,
    media: Photo,
    viewModel: FullscreenMediaViewModel
) {
    if (media.mediaType == "video") {
        enqueueVideoDownload(context, media)
    } else {
        viewModel.downloadPhoto(media)
    }
}

private fun enqueueVideoDownload(
    context: Context,
    video: Photo
) {
    val fileName = "asociacion_ciguena_${video.id}.mp4"
    val request = DownloadManager.Request(Uri.parse(ensureVideoUrlFormat(video.imageUrl)))
        .setTitle("Descargando video")
        .setDescription("Asociacion Ciguena")
        .setMimeType("video/mp4")
        .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DCIM,
            "$PHOTO_DIRECTORY/$fileName"
        )
        .apply {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                allowScanningByMediaScanner()
            }
        }

    val enqueued = runCatching {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }.isSuccess

    Toast.makeText(
        context,
        if (enqueued) {
            "Descarga del video iniciada"
        } else {
            "No se pudo iniciar la descarga del video"
        },
        if (enqueued) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
    ).show()
}

private fun savePhotoToGallery(
    context: Context,
    photo: Photo,
    bytes: ByteArray
): Boolean {
    if (bytes.isEmpty()) return false

    val mimeType = ByteArrayInputStream(bytes).use {
        URLConnection.guessContentTypeFromStream(it)
    }?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
    val fileName = "asociacion_ciguena_${photo.id}.$extension"

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$PHOTO_DIRECTORY"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = checkNotNull(
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            )
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    output.write(bytes)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (exception: Exception) {
                resolver.delete(uri, null, null)
                throw exception
            }
        } else {
            @Suppress("DEPRECATION")
            val picturesDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val targetDirectory = File(picturesDirectory, PHOTO_DIRECTORY).apply {
                check(exists() || mkdirs())
            }
            val targetFile = File(targetDirectory, fileName)
            FileOutputStream(targetFile).use { output -> output.write(bytes) }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
        }
        true
    }.getOrDefault(false)
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
