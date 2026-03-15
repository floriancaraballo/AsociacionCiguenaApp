package com.asociacionciguena.app.presentation.screens.calendar.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.SignatureCanvas
import kotlinx.coroutines.launch
import com.asociacionciguena.app.util.formatDate
@Composable
fun SignatureScreen(
    excursionId: String,  // ← CAMBIO: Recibir ID en lugar de título
    onNavigateBack: () -> Unit,
    viewModel: CalendarExcursionDetailViewModel = hiltViewModel()
) {
    // Obtener estado desde el ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Estados del formulario
    var tutorName by remember { mutableStateOf("") }
    var tutorDni by remember { mutableStateOf("") }
    var tutorPhone by remember { mutableStateOf("") }
    var minorName by remember { mutableStateOf("") }
    var signaturePaths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // ✅ CORREGIDO: Actualizar tutorName cuando currentUser esté disponible
    LaunchedEffect(currentUser) {
        currentUser?.displayName?.let { name ->
            if (tutorName.isBlank()) {  // Solo pre-rellenar si el usuario no ha escrito nada
                tutorName = name
            }
        }
        currentUser?.email?.let { email ->
            // Opcional: también pre-rellenar email si lo necesitas en el futuro
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // Obtener datos de la excursión (si ya cargaron)
    val excursion = (uiState as? CalendarExcursionDetailUiState.Success)?.excursion
    val excursionTitle = excursion?.title ?: "Cargando..."
    val excursionDate = excursion?.date?.let { formatDate(it) } ?: ""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        // ───────── BARRA SUPERIOR ─────────
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Firmar Autorización", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ───────── TÍTULO EXCURSIÓN ─────────
        item {
            Text(excursionTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Divider()
        }

        // ───────── ERROR MESSAGE ─────────
        if (showError) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(errorMessage, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = { showError = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // ───────── CAMPOS DEL FORMULARIO ─────────
        item {
            OutlinedTextField(
                value = minorName,
                onValueChange = { minorName = it },
                label = { Text("Nombre del menor *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )
        }
        item { Divider() }
        item { Text("Datos del Tutor Legal", style = MaterialTheme.typography.titleSmall) }
        item {
            OutlinedTextField(
                value = tutorName,
                onValueChange = { tutorName = it },
                label = { Text("Nombre del tutor *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )
        }
        item {
            OutlinedTextField(
                value = tutorDni,
                onValueChange = { if (it.length <= 9) tutorDni = it.uppercase() },
                label = { Text("DNI/NIE *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )
        }
        item {
            OutlinedTextField(
                value = tutorPhone,
                onValueChange = { if (it.all { c -> c.isDigit() || c == ' ' }) tutorPhone = it },
                label = { Text("Teléfono *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )
        }
        item { Divider() }

        // ───────── FIRMA ─────────
        item {
            Text("Firma aquí *", style = MaterialTheme.typography.titleSmall)
            Text("Dibuja tu firma en el recuadro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SignatureCanvas(
                paths = signaturePaths,
                onPathsChange = { signaturePaths = it },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
        if (signaturePaths.isNotEmpty() && !isSubmitting) {
            item {
                OutlinedButton(
                    onClick = { signaturePaths = emptyList() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar firma")
                }
            }
        }
        item { Divider() }

        // ───────── INFO LEGAL CENTRADA ─────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Al firmar aceptas:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("• Participación del menor\n• Condiciones de la actividad\n• Exención de responsabilidad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.9f))
                }
            }
        }

        // ───────── BOTÓN ENVIAR ─────────
        item {
            Button(
                onClick = {
                    when {
                        minorName.isBlank() -> { errorMessage = "Nombre del menor obligatorio"; showError = true; coroutineScope.launch { scrollState.animateScrollToItem(0) } }
                        tutorName.isBlank() -> { errorMessage = "Nombre del tutor obligatorio"; showError = true; coroutineScope.launch { scrollState.animateScrollToItem(0) } }
                        tutorDni.isBlank() || !isValidDni(tutorDni) -> { errorMessage = "DNI/NIE inválido"; showError = true; coroutineScope.launch { scrollState.animateScrollToItem(0) } }
                        tutorPhone.isBlank() || !isValidPhone(tutorPhone) -> { errorMessage = "Teléfono inválido"; showError = true; coroutineScope.launch { scrollState.animateScrollToItem(0) } }
                        signaturePaths.isEmpty() -> { errorMessage = "Debes firmar"; showError = true; coroutineScope.launch { scrollState.animateScrollToItem(0) } }
                        else -> {
                            isSubmitting = true
                            showError = false
                            // Llamar al ViewModel para firmar
                            viewModel.signAuthorization(
                                excursionTitle = excursionTitle,
                                excursionDate = excursionDate,
                                tutorName = tutorName,
                                tutorDni = tutorDni,
                                tutorPhone = tutorPhone,
                                tutorEmail = currentUser?.email ?: "",
                                minorName = minorName,
                                signaturePaths = signaturePaths,
                                onSuccess = { onNavigateBack() },  // ← Volver atrás al éxito
                                onError = { error -> showError = true; errorMessage = error }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting && uiState is CalendarExcursionDetailUiState.Success
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Firmando...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Firmar y Enviar")
                }
            }
        }

        // Espacio final
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// Validadores
private fun isValidDni(dni: String): Boolean {
    if (!dni.matches(Regex("^[0-9]{8}[A-Z]$")) && !dni.matches(Regex("^[XYZ][0-9]{7}[A-Z]$"))) return false
    val letters = "TRWAGMYFPDXBNJZSQVHLCKE"
    return if (dni.matches(Regex("^[0-9]{8}[A-Z]$"))) {
        val number = dni.substring(0, 8).toIntOrNull() ?: return false
        dni[8] == letters[number % 23]
    } else {
        var number = dni.substring(1, 8).toIntOrNull() ?: return false
        number += when (dni[0]) { 'X' -> 0; 'Y' -> 10000000; 'Z' -> 20000000; else -> return false }
        dni[8] == letters[number % 23]
    }
}

private fun isValidPhone(phone: String): Boolean {
    return phone.replace(" ", "").matches(Regex("^[6-9][0-9]{8}$"))
}