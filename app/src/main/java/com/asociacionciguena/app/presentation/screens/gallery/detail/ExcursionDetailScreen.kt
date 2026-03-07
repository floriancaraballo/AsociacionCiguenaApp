package com.asociacionciguena.app.presentation.screens.gallery.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Snackbar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults

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
                                Text("${selectedPhotos.size} seleccionadas")
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
                                        Icon(Icons.Default.Delete, "Eliminar seleccionadas")
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
                            Icon(Icons.Default.Add, "Subir Fotos")
                        }
                    }
                }
                else -> {}
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ExcursionDetailUiState.Loading -> {
                LoadingIndicator()
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
                            val photoId = photoToDeleteCopy.id
                            val storagePath = photoToDeleteCopy.storagePath
                            photoToDelete = null

                            // Luego eliminar foto
                            viewModel.deletePhoto(
                                photoId = photoId,
                                storagePath = storagePath,
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

                            // Luego eliminar fotos
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
                        text = "Aún no hay fotos",
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
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box {
            SubcomposeAsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )

            // Indicador de selección
            if (selectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    }
                ) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = photos[page].imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp,
                                    color = Color.White
                                )
                            }
                        }
                    )
                }
            }
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
                    "¿Eliminar foto?"
                } else {
                    "¿Eliminar $photoCount fotos?"
                }
            )
        },
        text = {
            Text(
                text = if (photoCount == 1) {
                    "Esta acción no se puede deshacer."
                } else {
                    "Se eliminarán $photoCount fotos. Esta acción no se puede deshacer."
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