package com.asociacionciguena.app.presentation.screens.admin.excursions

import androidx.activity.compose.BackHandler
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
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val imageUrl by viewModel.imageUrl.collectAsState()
    val date by viewModel.date.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                    imageUrl = imageUrl,
                    date = date,
                    isSaving = uiState is ExcursionFormUiState.Saving,
                    errorMessage = (uiState as? ExcursionFormUiState.Error)?.message,
                    onTitleChange = viewModel::onTitleChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onLocationChange = viewModel::onLocationChange,
                    onImageUrlChange = viewModel::onImageUrlChange,
                    onDatePickerClick = { showDatePicker = true },
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
    imageUrl: String,
    date: LocalDateTime?,
    isSaving: Boolean,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onDatePickerClick: () -> Unit,
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

        OutlinedTextField(
            value = imageUrl,
            onValueChange = onImageUrlChange,
            label = { Text("URL de Imagen (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            supportingText = {
                Text("Ejemplo: https://picsum.photos/800/600?random=1")
            }
        )

        if (imageUrl.isNotBlank()) {
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
                                    text = "URL inválida",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
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
                    date != null
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
