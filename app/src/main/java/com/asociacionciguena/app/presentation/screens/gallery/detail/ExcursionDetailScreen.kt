package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.presentation.components.ErrorMessage
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Snackbar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import com.asociacionciguena.app.presentation.components.ImageLoadingPlaceholder
import com.asociacionciguena.app.presentation.components.ZoomableImage

@Composable
fun ExcursionDetailScreen(
    viewModel: ExcursionDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToUpload: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<Photo?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPhotos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteMultipleDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is ExcursionDetailUiState.Success -> {
                            if (selectionMode) {
                                Text("${selectedPhotos.size} elementos seleccionados")
                            } else {
                                Text(
                                    text = state.excursion.title,
                                    maxLines = 1
                                )
                            }
                        }
                        else -> Text("Detalle")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedPhotos = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    when (val state = uiState) {
                        is ExcursionDetailUiState.Success -> {
                            if (state.isAdmin && state.photos.isNotEmpty()) {
                                if (selectionMode) {
                                    // Modo selección: botón eliminar
                                    IconButton(
                                        onClick = { showDeleteMultipleDialog = true },
                                        enabled = selectedPhotos.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Delete, "Eliminar elementos seleccionados")
                                    }
                                } else {
                                    // Modo normal: botón seleccionar
                                    IconButton(onClick = { selectionMode = true }) {
                                        Icon(Icons.Default.CheckCircle, "Seleccionar")
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            when (val state = uiState) {
                is ExcursionDetailUiState.Success -> {
                    if (state.isAdmin && !selectionMode) {
                        FloatingActionButton(
                            onClick = { onNavigateToUpload(state.excursion.id) }
                        ) {
                            Icon(Icons.Default.Add, "Subir archivos multimedia")
                        }
                    }
                }
                else -> {}
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ExcursionDetailUiState.Loading -> {
                ImageLoadingPlaceholder()
            }

            is ExcursionDetailUiState.Success -> {
                // Mensaje de error
                deleteError?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { deleteError = null }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }

                ExcursionDetailContent(
                    excursion = state.excursion,
                    photos = state.photos,
                    isAdmin = state.isAdmin,
                    selectionMode = selectionMode,
                    selectedPhotos = selectedPhotos,
                    onPhotoClick = { index ->
                        if (selectionMode) {
                            val photo = state.photos[index]
                            selectedPhotos = if (photo.id in selectedPhotos) {
                                selectedPhotos - photo.id
                            } else {
                                selectedPhotos + photo.id
                            }
                        } else {
                            selectedPhotoIndex = index
                        }
                    },
                    onPhotoLongPress = { index ->
                        if (state.isAdmin) {
                            photoToDelete = state.photos[index]
                            showDeleteDialog = true
                        }
                    },
                    modifier = Modifier.padding(paddingValues)
                )

                // Dialog eliminar foto individual
                // Dialog eliminar foto individual
                if (showDeleteDialog && photoToDelete != null) {
                    val photoToDeleteCopy = photoToDelete!!  // Copiar para evitar null
                    DeletePhotoDialog(
                        photoCount = 1,
                        onConfirm = {
                            // Cerrar dialog INMEDIATAMENTE
                            showDeleteDialog = false
                            photoToDelete = null

                            // Luego eliminar elemento
                            viewModel.deletePhoto(
                                photo = photoToDeleteCopy,
                                onSuccess = {},
                                onError = { error ->
                                    deleteError = error
                                }
                            )
                        },
                        onDismiss = {
                            showDeleteDialog = false
                            photoToDelete = null
                        }
                    )
                }

                // Dialog eliminar múltiples fotos
                // Dialog eliminar múltiples fotos
                if (showDeleteMultipleDialog) {
                    val photosToDelete = state.photos.filter { it.id in selectedPhotos }
                    DeletePhotoDialog(
                        photoCount = photosToDelete.size,
                        onConfirm = {
                            // Cerrar dialog INMEDIATAMENTE
                            showDeleteMultipleDialog = false

                            // Luego eliminar elementos
                            viewModel.deleteMultiplePhotos(
                                photos = photosToDelete,
                                onSuccess = {
                                    selectionMode = false
                                    selectedPhotos = emptySet()
                                },
                                onError = { error ->
                                    deleteError = error
                                    selectionMode = false
                                    selectedPhotos = emptySet()
                                }
                            )
                        },
                        onDismiss = {
                            showDeleteMultipleDialog = false
                        }
                    )
                }

                // Fullscreen gallery
                selectedPhotoIndex?.let { index ->
                    FullscreenGalleryDialog(
                        photos = state.photos,
                        initialIndex = index,
                        onDismiss = { selectedPhotoIndex = null }
                    )
                }
            }

            is ExcursionDetailUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun ExcursionDetailContent(
    excursion: com.asociacionciguena.app.domain.model.Excursion,
    photos: List<Photo>,
    isAdmin: Boolean,
    selectionMode: Boolean,
    selectedPhotos: Set<String>,
    onPhotoClick: (Int) -> Unit,
    onPhotoLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Título y fecha
        Text(
            text = excursion.title,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDate(excursion.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Aún no hay fotos ni vídeos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Grid de fotos (3 columnas)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = photos,
                    key = { it.id }
                ) { photo ->
                    val index = photos.indexOf(photo)
                    PhotoGridItem(
                        photo = photo,
                        isSelected = photo.id in selectedPhotos,
                        selectionMode = selectionMode,
                        onClick = { onPhotoClick(index) },
                        onLongPress = { onPhotoLongPress(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: Photo,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
        ) {
            DetailMediaPreview(photo = photo, modifier = Modifier.fillMaxSize())

            if (selectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        Color.Black.copy(alpha = 0.18f)
                    }
                ) {}

                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.55f)
                    }
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Seleccionado" else "No seleccionado",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp),
                        tint = Color.White
                    )
                }
            }

            if (photo.mediaType == "video") {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Vídeo",
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .size(18.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenGalleryDialog(
    photos: List<Photo>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Text("${pagerState.currentPage + 1} / ${photos.size}")
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                "Cerrar",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
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

                // ✅ Usar PhotoFullscreenDialog para cada página
                PhotoFullscreenContent(
                    photo = photo,
                    onDismiss = {}  // No cerrar al hacer swipe
                )
            }
        }
    }
}

@Composable
private fun PhotoFullscreenContent(
    photo: Photo,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (photo.mediaType == "video") {
            AllowLandscapeOrientation()
            // ✅ Asegurar que la URL tiene el formato correcto para streaming
            val videoUrl = ensureVideoUrlFormat(photo.imageUrl)

            VideoPlayer(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ZoomableImage(
                imageUrl = photo.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun DetailMediaPreview(
    photo: Photo,
    modifier: Modifier = Modifier
) {
    if (photo.mediaType != "video") {
        SubcomposeAsyncImage(
            model = photo.imageUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            loading = { ImageLoadingPlaceholder() }
        )
        return
    }

    val videoUrl = remember(photo.imageUrl) { ensureVideoUrlFormat(photo.imageUrl) }
    val generatedThumbnail by produceState<Bitmap?>(initialValue = null, videoUrl) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(videoUrl, emptyMap())
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }

    when {
        generatedThumbnail != null -> {
            Image(
                bitmap = generatedThumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        !photo.thumbnailUrl.isNullOrBlank() -> {
            SubcomposeAsyncImage(
                model = photo.thumbnailUrl,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                loading = { ImageLoadingPlaceholder() }
            )
        }
        else -> {
            SubcomposeAsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                loading = { ImageLoadingPlaceholder() }
            )
        }
    }
}

@Composable
private fun AllowLandscapeOrientation() {
    val activity = LocalContext.current as? Activity ?: return

    DisposableEffect(activity) {
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        onDispose {
            activity.requestedOrientation = previousOrientation
        }
    }
}

@Composable
private fun DeletePhotoDialog(
    photoCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (photoCount == 1) {
                    "¿Eliminar elemento?"
                } else {
                    "¿Eliminar $photoCount elementos?"
                }
            )
        },
        text = {
            Text(
                text = if (photoCount == 1) {
                    "Esta acción no se puede deshacer."
                } else {
                    "Se eliminarán $photoCount fotos o vídeos. Esta acción no se puede deshacer."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatDate(date: kotlinx.datetime.LocalDateTime): String {
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    return "${date.dayOfMonth} ${monthNames[date.monthNumber - 1]} ${date.year}"
}

@Composable
private fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

// ✅ Función auxiliar para formatear URL de vídeo para streaming
private fun ensureVideoUrlFormat(url: String): String {
    // Si la URL ya tiene alt=media, devolverla tal cual
    if (url.contains("alt=media")) {
        return url
    }

    // Si es una URL de Firebase Storage, añadir alt=media
    if (url.contains("firebasestorage.googleapis.com")) {
        return "${url}${if (url.contains("?")) "&" else "?"}alt=media"
    }

    // Devolver URL original si no es de Firebase
    return url
}
