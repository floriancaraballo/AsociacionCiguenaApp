package com.asociacionciguena.app.presentation.screens.admin.excursions

import android.net.Uri
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcursionFormScreen(
    viewModel: ExcursionFormViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val title by viewModel.title.collectAsState()
    val description by viewModel.description.collectAsState()
    val location by viewModel.location.collectAsState()
    val price by viewModel.price.collectAsState()
    val maxParticipants by viewModel.maxParticipants.collectAsState()
    val imageUrl by viewModel.imageUrl.collectAsState()
    val imageUploadState by viewModel.imageUploadState.collectAsState()
    val date by viewModel.date.collectAsState()
    val authorizationPdfUrl by viewModel.authorizationPdfUrl.collectAsState()
    val pdfUploadState by viewModel.pdfUploadState.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Launcher para seleccionar PDF
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAuthorizationPdf(it) }
    }
    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadExcursionImage(it) }
    }


    val hasUnsavedChanges = (title.isNotBlank() ||
            description.isNotBlank() ||
            location.isNotBlank()) &&
            uiState !is ExcursionFormUiState.Saved

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    LaunchedEffect(uiState) {
        if (uiState is ExcursionFormUiState.Saved) {
            onNavigateBack()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditMode) "Editar Excursión" else "Nueva Excursión")
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
            is ExcursionFormUiState.Loading -> {
                LoadingIndicator()
            }

            else -> {
                ExcursionFormContent(
                    title = title,
                    description = description,
                    location = location,
                    price = price,  // ← NUEVO
                    maxParticipants = maxParticipants,
                    imageUrl = imageUrl,
                    date = date,
                    authorizationPdfUrl = authorizationPdfUrl,
                    pdfUploadState = pdfUploadState,
                    imageUploadState = imageUploadState,  // ← NUEVO
                    isSaving = uiState is ExcursionFormUiState.Saving,
                    errorMessage = (uiState as? ExcursionFormUiState.Error)?.message,
                    onTitleChange = viewModel::onTitleChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onLocationChange = viewModel::onLocationChange,
                    onPriceChange = viewModel::onPriceChange,
                    onMaxParticipantsChange = viewModel::onMaxParticipantsChange,
                    onImageUrlChange = viewModel::onImageUrlChange,
                    onDatePickerClick = { showDatePicker = true },
                    onUploadImageClick = { imagePickerLauncher.launch("image/*") },  // ← NUEVO
                    onRemoveImageClick = viewModel::removeExcursionImage,  // ← NUEVO
                    onClearImageError = viewModel::clearImageUploadError,  // ← NUEVO
                    onUploadPdfClick = { pdfPickerLauncher.launch("application/pdf") },
                    onRemovePdfClick = viewModel::removeAuthorizationPdf,
                    onClearPdfError = viewModel::clearPdfUploadError,
                    onSaveClick = { viewModel.saveExcursion(onNavigateBack) },
                    onClearError = viewModel::clearError,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val instant = Instant.fromEpochMilliseconds(millis)
                                val localDate = instant.toLocalDateTime(TimeZone.UTC)
                                viewModel.onDateChange(localDate)
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancelar")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("¿Salir sin guardar?") },
                text = { Text("Tienes cambios sin guardar. ¿Estás seguro?") },
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
private fun ExcursionFormContent(
    title: String,
    description: String,
    location: String,
    price: String,  // ← AÑADIR
    maxParticipants: String,
    imageUrl: String,
    date: LocalDateTime?,
    authorizationPdfUrl: String?,
    pdfUploadState: PdfUploadState,
    imageUploadState: ImageUploadState,  // ← NUEVO
    isSaving: Boolean,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,  // ← NUEVO
    onMaxParticipantsChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onDatePickerClick: () -> Unit,
    onUploadImageClick: () -> Unit,  // ← NUEVO
    onRemoveImageClick: () -> Unit,  // ← NUEVO
    onClearImageError: () -> Unit,  // ← NUEVO
    onUploadPdfClick: () -> Unit,
    onRemovePdfClick: () -> Unit,
    onClearPdfError: () -> Unit,
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
                ),
                modifier = Modifier.fillMaxWidth()
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
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción *") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            enabled = !isSaving,
            maxLines = 7
        )

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Ubicación *") },
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            singleLine = true
        )

        // AÑADIR AQUÍ ↓
        // Campo de precio
        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            label = { Text("Precio (opcional)") },
            placeholder = { Text("0.00") },
            leadingIcon = {
                Text("€", style = MaterialTheme.typography.bodyLarge)
            },
            supportingText = {
                Text("Déjalo vacío si la excursión es gratuita")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = maxParticipants,
            onValueChange = onMaxParticipantsChange,
            label = { Text("Máximo de participantes *") },
            leadingIcon = {
                Icon(Icons.Default.Groups, contentDescription = null)
            },
            supportingText = {
                Text("Cuenta autorizaciones pendientes y aprobadas")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedCard(
            onClick = { if (!isSaving) onDatePickerClick() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fecha *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = date?.let { formatDateForDisplay(it) } ?: "Selecciona una fecha",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (date != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }


        Text(
            text = "Imagen de la Excursión (opcional)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

// Mostrar error de subida de imagen
        if (imageUploadState is ImageUploadState.Error) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = (imageUploadState as ImageUploadState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearImageError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        when {
            // Imagen subiendo
            imageUploadState is ImageUploadState.Uploading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Subiendo imagen...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${((imageUploadState as ImageUploadState.Uploading).progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = (imageUploadState as ImageUploadState.Uploading).progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Imagen ya subida
            imageUrl.isNotBlank() -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box {
                        SubcomposeAsyncImage(
                            model = imageUrl,
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
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )

                        // Botón eliminar imagen
                        IconButton(
                            onClick = onRemoveImageClick,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar imagen",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Sin imagen
            else -> {
                OutlinedButton(
                    onClick = onUploadImageClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && imageUploadState !is ImageUploadState.Uploading
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subir Imagen desde Dispositivo")
                }
            }
        }
        // ← NUEVO: Sección de PDF de Autorización
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Autorización PDF (opcional)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Sube un PDF con la autorización que los usuarios podrán descargar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Mostrar error de subida de PDF
        if (pdfUploadState is PdfUploadState.Error) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = pdfUploadState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearPdfError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        when {
            // PDF subiendo
            pdfUploadState is PdfUploadState.Uploading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Subiendo PDF...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(pdfUploadState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = pdfUploadState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // PDF ya subido
            authorizationPdfUrl != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "PDF Subido",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Autorización disponible",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        IconButton(
                            onClick = onRemovePdfClick,
                            enabled = !isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar PDF",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Sin PDF
            else -> {
                OutlinedButton(
                    onClick = onUploadPdfClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && pdfUploadState !is PdfUploadState.Uploading
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subir Autorización PDF")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isSaving &&
                    title.isNotBlank() &&
                    description.isNotBlank() &&
                    location.isNotBlank() &&
                    maxParticipants.toIntOrNull()?.let { it > 0 } == true &&
                    date != null &&
                    pdfUploadState !is PdfUploadState.Uploading &&
                    imageUploadState !is ImageUploadState.Uploading  // ← AÑADIDO
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Guardar Excursión")
            }
        }

        Text(
            text = "* Campos obligatorios",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDateForDisplay(date: LocalDateTime): String {
    val javaDate = date.toJavaLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    return javaDate.format(formatter).replaceFirstChar { it.uppercase() }
}
