package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.ImageLoadingPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExcursionDetailScreen(
    viewModel: ExcursionDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToUpload: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    if (selectionMode) {
                        Text("${selectedPhotos.size} seleccionados")
                    } else {
                        Text("Álbum")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectedPhotos = emptySet()
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    when (val state = uiState) {
                        is ExcursionDetailUiState.Success -> {
                            if (state.isAdmin && state.photos.isNotEmpty()) {
                                if (selectionMode) {
                                    IconButton(
                                        onClick = { showDeleteMultipleDialog = true },
                                        enabled = selectedPhotos.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Delete, "Eliminar elementos seleccionados")
                                    }
                                } else {
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            when (val state = uiState) {
                is ExcursionDetailUiState.Success -> {
                    if (state.isAdmin && !selectionMode) {
                        ExtendedFloatingActionButton(
                            onClick = { onNavigateToUpload(state.excursion.id) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text("Añadir archivos") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                else -> {}
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ExcursionDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    ImageLoadingPlaceholder()
                }
            }

            is ExcursionDetailUiState.Success -> {
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
                    selectionMode = selectionMode,
                    selectedPhotos = selectedPhotos,
                    hasUploadAction = state.isAdmin && !selectionMode,
                    onPhotoClick = { index ->
                        if (selectionMode) {
                            val photo = state.photos[index]
                            selectedPhotos = if (photo.id in selectedPhotos) {
                                selectedPhotos - photo.id
                            } else {
                                selectedPhotos + photo.id
                            }
                        } else {
                            val photo = state.photos[index]
                            openFullscreenMedia(
                                context = context,
                                excursionId = state.excursion.id,
                                photoId = photo.id
                            )
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

                if (showDeleteDialog) {
                    photoToDelete?.let { selectedPhoto ->
                        DeletePhotoDialog(
                            photoCount = 1,
                            onConfirm = {
                                showDeleteDialog = false
                                photoToDelete = null
                                viewModel.deletePhoto(
                                    photo = selectedPhoto,
                                    onSuccess = {},
                                    onError = { error -> deleteError = error }
                                )
                            },
                            onDismiss = {
                                showDeleteDialog = false
                                photoToDelete = null
                            }
                        )
                    }
                }

                if (showDeleteMultipleDialog) {
                    val photosToDelete = state.photos.filter { it.id in selectedPhotos }
                    DeletePhotoDialog(
                        photoCount = photosToDelete.size,
                        onConfirm = {
                            showDeleteMultipleDialog = false
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
                        onDismiss = { showDeleteMultipleDialog = false }
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
    selectionMode: Boolean,
    selectedPhotos: Set<String>,
    hasUploadAction: Boolean,
    onPhotoClick: (Int) -> Unit,
    onPhotoLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoCount = photos.count { it.mediaType == "video" }
    val imageCount = photos.size - videoCount

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (hasUploadAction) 96.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "album_summary", span = { GridItemSpan(maxLineSpan) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        excursion.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(formatDate(excursion.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            "$imageCount ${if (imageCount == 1) "foto" else "fotos"} · $videoCount ${if (videoCount == 1) "vídeo" else "vídeos"}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        if (photos.isEmpty()) {
            item(key = "album_empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Aún no hay fotos ni vídeos", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(items = photos, key = { _, photo -> photo.id }) { index, photo ->
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
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
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
                    modifier = Modifier.fillMaxSize(),
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
                    shape = CircleShape,
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
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
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

private fun openFullscreenMedia(
    context: Context,
    excursionId: String,
    photoId: String
) {
    context.startActivity(
        FullscreenMediaActivity.createIntent(
            context = context,
            excursionId = excursionId,
            photoId = photoId
        )
    )
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
private fun DetailMediaPreview(
    photo: Photo,
    modifier: Modifier = Modifier
) {
    if (photo.mediaType != "video") {
        CachedSubcomposeAsyncImage(
            imageUrl = photo.imageUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
        return
    }

    val videoUrl = remember(photo.imageUrl) { ensureVideoUrlFormat(photo.imageUrl) }
    val generatedThumbnail by produceState<Bitmap?>(initialValue = null, videoUrl) {
        val thumbnail = withContext(Dispatchers.IO) {
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
        value = thumbnail
    }

    val thumbnail = generatedThumbnail
    when {
        thumbnail != null -> {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }

        !photo.thumbnailUrl.isNullOrBlank() -> {
            CachedSubcomposeAsyncImage(
                imageUrl = photo.thumbnailUrl,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }

        else -> {
            CachedSubcomposeAsyncImage(
                imageUrl = photo.imageUrl,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
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

private fun ensureVideoUrlFormat(url: String): String {
    if (url.contains("alt=media")) return url
    if (url.contains("firebasestorage.googleapis.com")) {
        return "${url}${if (url.contains("?")) "&" else "?"}alt=media"
    }
    return url
}
