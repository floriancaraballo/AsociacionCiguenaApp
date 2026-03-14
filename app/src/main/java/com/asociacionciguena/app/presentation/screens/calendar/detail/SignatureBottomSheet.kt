package com.asociacionciguena.app.presentation.screens.calendar.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.presentation.components.SignatureCanvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureBottomSheet(
    excursionTitle: String,
    userName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String?, List<Path>) -> Unit  // ← 5 parámetros, SIN nombres
) {
    var tutorName by remember { mutableStateOf(userName) }
    var tutorDni by remember { mutableStateOf("") }
    var tutorPhone by remember { mutableStateOf("") }
    var minorName by remember { mutableStateOf("") }
    var signaturePaths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { sheetState.expand() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Firmar Autorización", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Text(excursionTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Divider()

            // Error message
            if (showError) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(errorMessage, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = { showError = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Campos del formulario
            OutlinedTextField(value = minorName, onValueChange = { minorName = it }, label = { Text("Nombre del menor *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSubmitting)
            Divider()
            Text("Datos del Tutor Legal", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = tutorName, onValueChange = { tutorName = it }, label = { Text("Nombre del tutor *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSubmitting)
            OutlinedTextField(value = tutorDni, onValueChange = { if (it.length <= 9) tutorDni = it.uppercase() }, label = { Text("DNI/NIE *") }, placeholder = { Text("12345678A") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSubmitting)
            OutlinedTextField(value = tutorPhone, onValueChange = { if (it.all { c -> c.isDigit() || c == ' ' }) tutorPhone = it }, label = { Text("Teléfono *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !isSubmitting)
            Divider()

            // Firma
            Text("Firma aquí *", style = MaterialTheme.typography.titleSmall)
            Text("Dibuja tu firma en el recuadro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SignatureCanvas(  // ← SIN onCanvasSizeChange, SIN enabled
                paths = signaturePaths,
                onPathsChange = { signaturePaths = it },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )

            if (signaturePaths.isNotEmpty() && !isSubmitting) {
                OutlinedButton(onClick = { signaturePaths = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar firma")
                }
            }

            Divider()

            // Info legal
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Al firmar aceptas:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("• Participación del menor\n• Condiciones de la actividad\n• Exención de responsabilidad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            // Botón enviar
            Button(
                onClick = {
                    when {
                        minorName.isBlank() -> { errorMessage = "Nombre del menor obligatorio"; showError = true; coroutineScope.launch { scrollState.animateScrollTo(0) } }
                        tutorName.isBlank() -> { errorMessage = "Nombre del tutor obligatorio"; showError = true; coroutineScope.launch { scrollState.animateScrollTo(0) } }
                        tutorDni.isBlank() || !isValidDni(tutorDni) -> { errorMessage = "DNI/NIE inválido (ej: 12345678A)"; showError = true; coroutineScope.launch { scrollState.animateScrollTo(0) } }
                        tutorPhone.isBlank() || !isValidPhone(tutorPhone) -> { errorMessage = "Teléfono inválido (9 dígitos)"; showError = true; coroutineScope.launch { scrollState.animateScrollTo(0) } }
                        signaturePaths.isEmpty() -> { errorMessage = "Debes firmar"; showError = true; coroutineScope.launch { scrollState.animateScrollTo(0) } }
                        else -> {
                            isSubmitting = true
                            showError = false
                            // ← LLAMADA POSICIONAL (sin nombres de parámetro)
                            onSubmit(
                                tutorName,
                                tutorDni,
                                tutorPhone,
                                minorName.ifBlank { null },
                                signaturePaths
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
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
            Spacer(Modifier.height(16.dp))
        }
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