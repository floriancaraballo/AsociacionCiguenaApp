package com.asociacionciguena.app.presentation.screens.admin.news

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewsFormScreen(
    viewModel: NewsFormViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val imageUrl by viewModel.imageUrl.collectAsState()
    val isPublic by viewModel.isPublic.collectAsState()
    val selectedPhotoUri by viewModel.selectedPhotoUri.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val additionalPhotoUris by viewModel.additionalPhotoUris.collectAsState()
    val additionalPhotoUrls by viewModel.additionalPhotoUrls.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    val hasUnsavedChanges = (title.isNotBlank() ||
            content.isNotBlank() ||
            imageUrl.isNotBlank() ||
            selectedPhotoUri != null) &&
            uiState !is NewsFormUiState.Saved

    // Permission state
    val permissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    // Image picker launcher
    // Launcher para foto principal
    val mainPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }

// Launcher para fotos adicionales
    val additionalPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAdditionalPhotoSelected(it) }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    LaunchedEffect(uiState) {
        if (uiState is NewsFormUiState.Saved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditMode) "Editar Noticia" else "Nueva Noticia")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showExitDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is NewsFormUiState.Loading -> {
                LoadingIndicator()
            }

            else -> {
                NewsFormContent(
                    title = title,
                    content = content,
                    imageUrl = imageUrl,
                    isPublic = isPublic,
                    selectedPhotoUri = selectedPhotoUri,
                    uploadProgress = uploadProgress,
                    additionalPhotoUris = additionalPhotoUris,
                    additionalPhotoUrls = additionalPhotoUrls,
                    isSaving = uiState is NewsFormUiState.Saving,
                    errorMessage = (uiState as? NewsFormUiState.Error)?.message,
                    permissionGranted = permissionState.status.isGranted,
                    onRequestPermission = { permissionState.launchPermissionRequest() },
                    onSelectPhoto = { mainPhotoLauncher.launch("image/*") },
                    onClearPhoto = { viewModel.clearSelectedPhoto() },
                    onSelectAdditionalPhoto = { additionalPhotoLauncher.launch("image/*") },
                    onRemoveAdditionalPhotoUri = { viewModel.removeAdditionalPhoto(it) },
                    onRemoveAdditionalPhotoUrl = { viewModel.removeAdditionalPhotoUrl(it) },
                    onTitleChange = viewModel::onTitleChange,
                    onContentChange = viewModel::onContentChange,
                    onImageUrlChange = viewModel::onImageUrlChange,
                    onIsPublicChange = viewModel::onIsPublicChange,
                    onSaveClick = { viewModel.saveNews(onNavigateBack) },
                    onClearError = viewModel::clearError,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("¿Salir sin guardar?") },
                text = { Text("Tienes cambios sin guardar. ¿Estás seguro de que quieres salir?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Salir sin guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Continuar editando")
                    }
                }
            )
        }
    }
}

@Composable
private fun NewsFormContent(
    title: String,
    content: String,
    imageUrl: String,
    isPublic: Boolean,
    selectedPhotoUri: Uri?,
    uploadProgress: Float,
    additionalPhotoUris: List<Uri>,
    additionalPhotoUrls: List<String>,
    isSaving: Boolean,
    errorMessage: String?,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSelectPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onSelectAdditionalPhoto: () -> Unit,
    onRemoveAdditionalPhotoUri: (Int) -> Unit,
    onRemoveAdditionalPhotoUrl: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onIsPublicChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Título *") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            singleLine = true
        )

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("Contenido *") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            enabled = !isSaving,
            maxLines = 10
        )

        // NUEVO: Sección de imagen
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Imagen",
                    style = MaterialTheme.typography.titleMedium
                )

                // Botón para subir desde dispositivo
                if (selectedPhotoUri == null) {
                    OutlinedButton(
                        onClick = {
                            if (permissionGranted) {
                                onSelectPhoto()
                            } else {
                                onRequestPermission()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Subir desde dispositivo")
                    }

                    Text(
                        text = "O",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Campo URL (solo si no hay foto seleccionada)
                if (selectedPhotoUri == null) {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = onImageUrlChange,
                        label = { Text("URL de Imagen") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        supportingText = {
                            Text("Ejemplo: https://picsum.photos/800/600?random=1")
                        }
                    )
                }
            }
        }

        // Preview de imagen
        val previewUri = selectedPhotoUri?.toString() ?: imageUrl
        if (previewUri.isNotBlank()) {
            Text(
                text = "Preview:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box {
                    SubcomposeAsyncImage(
                        model = previewUri,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Error al cargar imagen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )

                    // Botón para quitar imagen
                    if (selectedPhotoUri != null) {
                        IconButton(
                            onClick = onClearPhoto,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar imagen",
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fotos Adicionales
        val maxPhotos = 5
        val canAddMore = (additionalPhotoUris.size + additionalPhotoUrls.size) < maxPhotos

        if (additionalPhotoUrls.isNotEmpty() || additionalPhotoUris.isNotEmpty() || canAddMore) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Fotos Adicionales (${additionalPhotoUris.size + additionalPhotoUrls.size}/$maxPhotos)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Grid de fotos
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Fotos existentes (URLs)
                        items(additionalPhotoUrls.size) { index ->
                            PhotoGridItem(
                                imageUri = additionalPhotoUrls[index],
                                onRemove = { onRemoveAdditionalPhotoUrl(additionalPhotoUrls[index]) },
                                enabled = !isSaving
                            )
                        }

                        // Fotos nuevas (URIs)
                        items(additionalPhotoUris.size) { index ->
                            PhotoGridItem(
                                imageUri = additionalPhotoUris[index].toString(),
                                onRemove = { onRemoveAdditionalPhotoUri(index) },
                                enabled = !isSaving
                            )
                        }

                        // Botón añadir más
                        if (canAddMore) {
                            item {
                                OutlinedCard(
                                    onClick = {
                                        if (permissionGranted) {
                                            onSelectAdditionalPhoto()
                                        } else {
                                            onRequestPermission()
                                        }
                                    },
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .fillMaxWidth(),
                                    enabled = !isSaving
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Añadir",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Progress bar durante upload
        if (isSaving && selectedPhotoUri != null && uploadProgress > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Subiendo imagen... ${(uploadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = uploadProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Visibilidad",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isPublic) "Visible para todos" else "Solo administradores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isPublic,
                    onCheckedChange = onIsPublicChange,
                    enabled = !isSaving
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isSaving && title.isNotBlank() && content.isNotBlank()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Guardar")
            }
        }

        Text(
            text = "* Campos obligatorios",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoGridItem(
    imageUri: String,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth()
    ) {
        Box {
            SubcomposeAsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            )

            // Botón eliminar
            if (enabled) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Eliminar",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }
}