package com.asociacionciguena.app.presentation.screens.admin.photos

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoUploadScreen(
    viewModel: PhotoUploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val excursions by viewModel.excursions.collectAsState()
    val users by viewModel.users.collectAsState()
    // Bloquear navegación mientras se sube
    val isUploading = uiState is PhotoUploadUiState.Uploading

    BackHandler(enabled = isUploading) {
        // No hacer nada - bloquear el botón atrás mientras se sube
    }
    // Android 13+ no necesita permiso para Photo Picker
    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    val permissionState = if (needsPermission) {
        rememberPermissionState(permission = Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        null
    }

    // NUEVO: Selector múltiple de fotos
    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosSelected(uris)
        }
    }

    // NUEVO: Selector para añadir más fotos
    val addMorePhotosLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMorePhotos(uris)
        }
    }
    // Limpiar estado al entrar a la pantalla
    DisposableEffect(Unit) {
        viewModel.reset()
        onDispose {
            viewModel.reset()
        }
    }
    LaunchedEffect(uiState) {
        if (uiState is PhotoUploadUiState.Success) {
            kotlinx.coroutines.delay(2000)
            viewModel.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subir Fotos") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isUploading  // ← Deshabilitar mientras se sube
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = if (isUploading) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
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
        PhotoUploadContent(
            uiState = uiState,
            excursions = excursions,
            users = users,
            permissionGranted = permissionState?.status?.isGranted ?: true,  // ← Android 13+ siempre true
            shouldShowRationale = permissionState?.status?.shouldShowRationale ?: false,
            onRequestPermission = { permissionState?.launchPermissionRequest() },
            onSelectPhotos = {
                multiplePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAddMorePhotos = {
                addMorePhotosLauncher.launch(
                    PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePhoto = viewModel::removePhoto,
            onExcursionSelected = viewModel::onExcursionSelected,
            onUserToggled = viewModel::onUserToggled,
            onSelectAll = viewModel::selectAllUsers,
            onDeselectAll = viewModel::deselectAllUsers,
            onUploadClick = { viewModel.uploadPhotos {} },
            onClearError = viewModel::clearError,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun PhotoUploadContent(
    uiState: PhotoUploadUiState,
    excursions: List<ExcursionOption>,
    users: List<UserOption>,
    permissionGranted: Boolean,
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onSelectPhotos: () -> Unit,  // ← Cambio: plural
    onAddMorePhotos: () -> Unit,  // ← NUEVO
    onRemovePhoto: (Uri) -> Unit,  // ← NUEVO
    onExcursionSelected: (String) -> Unit,
    onUserToggled: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onUploadClick: () -> Unit,
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
        // Error y Success (sin cambios)
        if (uiState is PhotoUploadUiState.Error) {
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
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (uiState is PhotoUploadUiState.Success) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fotos subidas correctamente",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (!permissionGranted) {
            PermissionRequestCard(
                shouldShowRationale = shouldShowRationale,
                onRequestPermission = onRequestPermission
            )
            return
        }

        // Select photos button
        if (uiState is PhotoUploadUiState.Idle) {
            OutlinedCard(
                onClick = onSelectPhotos,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Seleccionar Fotos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Hasta 20 fotos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Photos selected
        if (uiState is PhotoUploadUiState.PhotosSelected) {
            PhotosSelectedContent(
                uris = uiState.uris,
                excursions = excursions,
                users = users,
                selectedExcursionId = uiState.selectedExcursionId,
                selectedUsers = uiState.selectedUsers,
                onExcursionSelected = onExcursionSelected,
                onUserToggled = onUserToggled,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onUploadClick = onUploadClick,
                onAddMore = onAddMorePhotos,
                onRemove = onRemovePhoto
            )
        }

        // Uploading
        if (uiState is PhotoUploadUiState.Uploading) {
            UploadingContent(
                progress = uiState.progress,
                currentPhoto = uiState.currentPhotoIndex,
                totalPhotos = uiState.totalPhotos
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PermMedia,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = if (shouldShowRationale) {
                    "Necesitamos acceso a tus fotos para subir imágenes"
                } else {
                    "Permiso de fotos requerido"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "La app necesita permiso para acceder a la galería y seleccionar fotos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conceder Permiso")
            }
        }
    }
}

@Composable
private fun PhotosSelectedContent(
    uris: List<Uri>,
    excursions: List<ExcursionOption>,
    users: List<UserOption>,
    selectedExcursionId: String?,
    selectedUsers: List<String>,
    onExcursionSelected: (String) -> Unit,
    onUserToggled: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onUploadClick: () -> Unit,
    onAddMore: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    // Header con contador
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uris.size} foto${if (uris.size != 1) "s" else ""} seleccionada${if (uris.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium
            )

            TextButton(onClick = onAddMore) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Añadir más")
            }
        }
    }

    // Grid de fotos
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(300.dp)
    ) {
        items(
            items = uris,
            key = { it.toString() }
        ) { uri ->
            Box {
                Card(modifier = Modifier.aspectRatio(1f)) {
                    SubcomposeAsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Botón eliminar
                IconButton(
                    onClick = { onRemove(uri) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // Excursion selector
    ExcursionSelector(
        excursions = excursions,
        selectedId = selectedExcursionId,
        onSelected = onExcursionSelected
    )

    // Upload button
    Button(
        onClick = onUploadClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = selectedExcursionId != null
    ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Subir ${uris.size} foto${if (uris.size != 1) "s" else ""}")
    }
}

@Composable
private fun UploadingContent(
    progress: Float,
    currentPhoto: Int,
    totalPhotos: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Subiendo fotos...",
                style = MaterialTheme.typography.titleMedium
            )
            // ← NUEVO
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "No cierres esta pantalla hasta que termine la subida",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = "Foto $currentPhoto de $totalPhotos",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExcursionSelector(
    excursions: List<ExcursionOption>,
    selectedId: String?,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Excursión *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = excursions.find { it.id == selectedId }?.title ?: "Selecciona una excursión",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    excursions.forEach { excursion ->
                        DropdownMenuItem(
                            text = { Text(excursion.title) },
                            onClick = {
                                onSelected(excursion.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun UserSelector(
    users: List<UserOption>,
    selectedUsers: List<String>,
    onUserToggled: (String) -> Unit,
    onSelectAll: () -> Unit,  // NUEVO
    onDeselectAll: () -> Unit  // NUEVO
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Usuarios autorizados * (${selectedUsers.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll) {
                        Text("Todos", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onDeselectAll) {
                        Text("Ninguno", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            users.forEach { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = user.id in selectedUsers,
                        onCheckedChange = { onUserToggled(user.id) }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
