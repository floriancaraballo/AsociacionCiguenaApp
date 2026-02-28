package com.asociacionciguena.app.presentation.screens.admin.photos

import android.Manifest
import android.net.Uri
import android.os.Build
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
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoUploadScreen(
    viewModel: PhotoUploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val excursions by viewModel.excursions.collectAsState()
    val users by viewModel.users.collectAsState()

    // Permission state
    val permissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }

    // Handle success
    LaunchedEffect(uiState) {
        if (uiState is PhotoUploadUiState.Success) {
            kotlinx.coroutines.delay(2000)
            viewModel.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subir Foto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        PhotoUploadContent(
            uiState = uiState,
            excursions = excursions,
            users = users,
            permissionGranted = permissionState.status.isGranted,
            shouldShowRationale = permissionState.status.shouldShowRationale,
            onRequestPermission = { permissionState.launchPermissionRequest() },
            onSelectPhoto = { imagePickerLauncher.launch("image/*") },
            onExcursionSelected = viewModel::onExcursionSelected,
            onUserToggled = viewModel::onUserToggled,
            onUploadClick = { uri -> viewModel.uploadPhoto(uri) {} },
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
    onSelectPhoto: () -> Unit,
    onExcursionSelected: (String) -> Unit,
    onUserToggled: (String) -> Unit,
    onUploadClick: (Uri) -> Unit,
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
        // Error message
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

        // Success message
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
                        text = "Foto subida correctamente",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Permission request
        if (!permissionGranted) {
            PermissionRequestCard(
                shouldShowRationale = shouldShowRationale,
                onRequestPermission = onRequestPermission
            )
            return
        }

        // Select photo button
        if (uiState is PhotoUploadUiState.Idle) {
            OutlinedCard(
                onClick = onSelectPhoto,
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
                        text = "Seleccionar Foto",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Photo selected
        if (uiState is PhotoUploadUiState.PhotoSelected) {
            PhotoSelectedContent(
                uri = uiState.uri,
                excursions = excursions,
                users = users,
                selectedExcursionId = uiState.selectedExcursionId,
                selectedUsers = uiState.selectedUsers,
                onExcursionSelected = onExcursionSelected,
                onUserToggled = onUserToggled,
                onUploadClick = { onUploadClick(uiState.uri) },
                onChangePhoto = onSelectPhoto
            )
        }

        // Uploading
        if (uiState is PhotoUploadUiState.Uploading) {
            UploadingContent(progress = uiState.progress)
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
private fun PhotoSelectedContent(
    uri: Uri,
    excursions: List<ExcursionOption>,
    users: List<UserOption>,
    selectedExcursionId: String?,
    selectedUsers: List<String>,
    onExcursionSelected: (String) -> Unit,
    onUserToggled: (String) -> Unit,
    onUploadClick: () -> Unit,
    onChangePhoto: () -> Unit
) {
    // Preview
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box {
            SubcomposeAsyncImage(
                model = uri,
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
                }
            )

            IconButton(
                onClick = onChangePhoto,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Cambiar foto",
                        modifier = Modifier.padding(8.dp)
                    )
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

    // User selector
    UserSelector(
        users = users,
        selectedUsers = selectedUsers,
        onUserToggled = onUserToggled
    )

    // Upload button
    Button(
        onClick = onUploadClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = selectedExcursionId != null && selectedUsers.isNotEmpty()
    ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Subir Foto")
    }
}

@Composable
private fun UploadingContent(progress: Float) {
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
                text = "Subiendo foto...",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${(progress * 100).toInt()}%",
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
    onUserToggled: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Usuarios autorizados * (${selectedUsers.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
