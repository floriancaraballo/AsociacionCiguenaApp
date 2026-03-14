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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureBottomSheet(
    excursionTitle: String,
    userName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onSubmit: (
        tutorName: String,
        tutorDni: String,
        tutorPhone: String,
        minorName: String?,
        signaturePaths: List<Path>
    ) -> Unit
) {
    var tutorName by remember { mutableStateOf(userName) }
    var tutorDni by remember { mutableStateOf("") }
    var tutorPhone by remember { mutableStateOf("") }
    var minorName by remember { mutableStateOf("") }
    var signaturePaths by remember { mutableStateOf<List<Path>>(emptyList()) }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }  // ← NUEVO

    val scrollState = rememberScrollState()  // ← NUEVO
    val coroutineScope = rememberCoroutineScope()  // ← NUEVO

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Prevenir que se cierre
    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,  // ← Permitir cerrar pero solo con dismiss explícito
        sheetState = sheetState,
        dragHandle = null  // ← Sin handle de arrastre
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header con botón cerrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Firmar Autorización",
                    style = MaterialTheme.typography.headlineSmall
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Text(
                text = excursionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Divider()

            // Error
            if (showError) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = { showError = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Nombre del menor (siempre visible)
            OutlinedTextField(
                value = minorName,
                onValueChange = { minorName = it },
                label = { Text("Nombre completo del menor *") },
                placeholder = { Text("Juan García López") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !showError
            )

            Divider()

            Text(
                text = "Datos del Tutor Legal",
                style = MaterialTheme.typography.titleSmall
            )

            // Campos del tutor
            OutlinedTextField(
                value = tutorName,
                onValueChange = { tutorName = it },
                label = { Text("Nombre completo del tutor *") },
                placeholder = { Text("María López García") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = tutorDni,
                onValueChange = {
                    // Solo permitir letras y números, max 9 caracteres
                    if (it.length <= 9) {
                        tutorDni = it.uppercase()
                    }
                },
                label = { Text("DNI/NIE del tutor *") },
                placeholder = { Text("12345678A") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Formato: 12345678A")
                }
            )

            OutlinedTextField(
                value = tutorPhone,
                onValueChange = {
                    // Solo permitir números y espacios
                    if (it.all { char -> char.isDigit() || char == ' ' }) {
                        tutorPhone = it
                    }
                },
                label = { Text("Teléfono del tutor *") },
                placeholder = { Text("666 123 456 o 666123456") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("9 dígitos con o sin espacios")
                }
            )

            Divider()

            // Canvas de firma
            Text(
                text = "Firma aquí *",
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = "Dibuja tu firma con el dedo en el recuadro blanco",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SignatureCanvas(
                paths = signaturePaths,
                modifier = Modifier.fillMaxWidth(),
                onPathsChange = { paths ->
                    signaturePaths = paths
                }
            )

            // Botón limpiar firma
            if (signaturePaths.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        signaturePaths = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar firma")
                }
            }

            Divider()

            // Info legal
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = "Al firmar aceptas:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "• La participación del menor en la excursión\n• Las condiciones de la actividad\n• Que la asociación no se hace responsable de accidentes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Botón guardar
            Button(
                onClick = {
                    // Validar
                    when {
                        minorName.isBlank() -> {
                            errorMessage = "El nombre del menor es obligatorio"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        tutorName.isBlank() -> {
                            errorMessage = "El nombre del tutor es obligatorio"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        tutorDni.isBlank() -> {
                            errorMessage = "El DNI/NIE es obligatorio"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        !isValidDni(tutorDni) -> {
                            errorMessage = "DNI/NIE inválido. Formato: 12345678A"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        tutorPhone.isBlank() -> {
                            errorMessage = "El teléfono es obligatorio"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        !isValidPhone(tutorPhone) -> {
                            errorMessage = "Teléfono inválido. Debe tener 9 dígitos"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        signaturePaths.isEmpty() -> {
                            errorMessage = "Debes firmar en el recuadro"
                            showError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                        else -> {
                            // Todo OK - Mostrar loading
                            isSubmitting = true
                            showError = false

                            onSubmit(
                                tutorName,
                                tutorDni,
                                tutorPhone,
                                minorName,
                                signaturePaths
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting  // ← Deshabilitar mientras procesa
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Firmando...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Firmar y Enviar")
                }
            }
        }
    }
}

/**
 * Validar DNI/NIE español
 */
private fun isValidDni(dni: String): Boolean {
    // Formato: 8 números + 1 letra
    val dniRegex = Regex("^[0-9]{8}[A-Z]$")
    val nieRegex = Regex("^[XYZ][0-9]{7}[A-Z]$")

    if (!dni.matches(dniRegex) && !dni.matches(nieRegex)) {
        return false
    }

    // Validar letra del DNI
    if (dni.matches(dniRegex)) {
        val letters = "TRWAGMYFPDXBNJZSQVHLCKE"
        val number = dni.substring(0, 8).toInt()
        val letter = dni[8]
        val expectedLetter = letters[number % 23]

        return letter == expectedLetter
    }

    // Para NIE, convertir X=0, Y=1, Z=2 y validar igual
    if (dni.matches(nieRegex)) {
        val letters = "TRWAGMYFPDXBNJZSQVHLCKE"
        var number = dni.substring(1, 8).toInt()

        when (dni[0]) {
            'X' -> number += 0
            'Y' -> number += 10000000
            'Z' -> number += 20000000
        }

        val letter = dni[8]
        val expectedLetter = letters[number % 23]

        return letter == expectedLetter
    }

    return true
}

/**
 * Validar teléfono español (9 dígitos)
 */
private fun isValidPhone(phone: String): Boolean {
    // Quitar espacios
    val cleanPhone = phone.replace(" ", "")

    // Debe tener exactamente 9 dígitos
    return cleanPhone.matches(Regex("^[6-9][0-9]{8}$"))
}