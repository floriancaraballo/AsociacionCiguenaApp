package com.asociacionciguena.app.presentation.screens.gallery.detail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.FloatingActionButton
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
                        Icon(Icons.Default.ArrowBack, "Volver")
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

                if (showDeleteDialog && photoToDelete != null) {
                    val photoToDeleteCopy = photoToDelete!!
                    DeletePhotoDialog(
                        photoCount = 1,
                        onConfirm = {
                            showDeleteDialog = false
                            photoToDelete = null
                            viewModel.deletePhoto(
                                photo = photoToDeleteCopy,
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
    onPhotoClick: (Int) -> Unit,
    onPhotoLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = photos, key = { it.id }) { photo ->
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
        modifier = modifier.aspectRatio(1f),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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
        value = withContext(Dispatchers.IO) {
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
