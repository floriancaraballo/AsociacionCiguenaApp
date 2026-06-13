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
import com.asociacionciguena.app.domain.model.RegistrationClosureReason
import com.asociacionciguena.app.util.formatDateRange
import kotlinx.coroutines.launch

// Modelo para cada menor en autorización múltiple
data class MinorAuth(
    val minorName: String = "",
    val signaturePaths: List<Path> = emptyList(),
    val tutorName: String = "",
    val tutorDni: String = "",
    val tutorPhone: String = ""
)

@Composable
fun SignatureScreen(
    excursionId: String,
    viewModel: CalendarExcursionDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Estados del formulario
    var isMultiAuth by remember { mutableStateOf(false) }
    var minors by remember { mutableStateOf(listOf(MinorAuth())) }
    var currentMinorIndex by remember { mutableStateOf(0) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    fun showValidationError(message: String) {
        showError = true
        errorMessage = message
        coroutineScope.launch { scrollState.animateScrollToItem(0) }
    }

    // Datos de la excursión
    val excursion = (uiState as? CalendarExcursionDetailUiState.Success)?.excursion
    val isAdmin = (uiState as? CalendarExcursionDetailUiState.Success)?.isAdmin ?: false
    val excursionTitle = excursion?.title ?: "Cargando..."
    val excursionDate = excursion?.let { formatDateRange(it.date, it.endDate) } ?: ""
    val observedAuthorizationCount by viewModel.activeAuthorizationCount.collectAsState()
    val activeAuthorizationCount = if (isAdmin) {
        observedAuthorizationCount
    } else {
        excursion?.currentParticipants ?: 0
    }
    val isCapacityFull = excursion?.let {
        it.maxParticipants > 0 && activeAuthorizationCount >= it.maxParticipants
    } ?: false
    val isRegistrationClosed = excursion?.registrationClosed == true || isCapacityFull

    if (isRegistrationClosed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Column {
                        Text(
                            if (excursion?.registrationClosureReason ==
                                RegistrationClosureReason.DEADLINE_PASSED
                            ) "Plazo finalizado" else "Plazas completas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            if (excursion?.registrationClosureReason ==
                                RegistrationClosureReason.DEADLINE_PASSED
                            ) "El plazo de inscripción para esta excursión ha finalizado."
                            else "No se pueden rellenar más autorizaciones para esta excursión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        return
    }

    // Pre-rellenar datos del tutor cuando se cargue el usuario
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            minors = minors.map { minor ->
                minor.copy(
                    tutorName = user.displayName ?: "",
                    tutorPhone = ""
                )
            }
        }
    }

    // ✅ FUNCIÓN: Actualizar tutor para TODOS los menores (campos compartidos)
    fun updateTutorForAll(fieldUpdater: (MinorAuth) -> MinorAuth) {
        minors = minors.map { minor -> fieldUpdater(minor) }
    }

    // ✅ FUNCIÓN: Actualizar solo el menor actual (campos únicos)
    fun updateCurrentMinor(fieldUpdater: (MinorAuth) -> MinorAuth) {
        minors = minors.mapIndexed { index, minor ->
            if (index == currentMinorIndex) fieldUpdater(minor) else minor
        }
    }

    // ✅ FUNCIÓN: Validar un menor específico
    // ✅ FUNCIÓN: Validar un menor específico CON LOGS DETALLADOS
    fun validationErrorForMinor(minor: MinorAuth, participantNumber: Int? = null): String? {
        val participantPrefix = participantNumber?.let { "Participante $it: " } ?: ""

        return when {
            minor.minorName.isBlank() -> "${participantPrefix}introduce el nombre del participante."
            minor.tutorName.isBlank() -> "${participantPrefix}introduce el nombre del tutor."
            minor.tutorDni.isBlank() -> "${participantPrefix}introduce el DNI/NIE del tutor."
            !isValidDni(minor.tutorDni) -> "${participantPrefix}el DNI/NIE no es valido."
            minor.tutorPhone.isBlank() -> "${participantPrefix}introduce el telefono del tutor."
            !isValidPhone(minor.tutorPhone) -> "${participantPrefix}el telefono debe tener 9 digitos y empezar por 6, 7, 8 o 9."
            minor.signaturePaths.isEmpty() -> "${participantPrefix}falta la firma."
            else -> null
        }
    }

    fun validateMinor(minor: MinorAuth, minorLabel: String = "Participante"): Boolean {
        val checks = listOf(
            "minorName" to minor.minorName.isNotBlank(),
            "tutorName" to minor.tutorName.isNotBlank(),
            "tutorDni" to (minor.tutorDni.isNotBlank() && isValidDni(minor.tutorDni)),
            "tutorPhone" to (minor.tutorPhone.isNotBlank() && isValidPhone(minor.tutorPhone)),
            "signaturePaths" to minor.signaturePaths.isNotEmpty()
        )

        val allValid = checks.all { it.second }

        if (!allValid) {
            val failed = checks.filter { !it.second }.map { it.first }.joinToString(", ")
            android.util.Log.d("AuthValidate", "❌ '$minorLabel' falló en: [$failed]")
            checks.forEach { (field, valid) ->
                android.util.Log.d("AuthValidate", "   • $field: ${if (valid) "✅" else "❌"}")
            }
        }

        return allValid
    }

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

        // ───────── TOGGLE MULTI-AUTORIZACIÓN (hasta 3 menores) ─────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Autorización múltiple hermanos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Firma hasta 3 autorizaciones a la vez",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Switch(
                        checked = isMultiAuth,
                        onCheckedChange = {
                            isMultiAuth = it
                            if (it && minors.size == 1) {
                                // Al activar: crear 3 menores total (el primero + 2 más)
                                val firstTutor = minors.firstOrNull()
                                minors = listOf(
                                    minors.first(),
                                    MinorAuth(tutorName = firstTutor?.tutorName ?: "", tutorPhone = firstTutor?.tutorPhone ?: ""),
                                    MinorAuth(tutorName = firstTutor?.tutorName ?: "", tutorPhone = firstTutor?.tutorPhone ?: "")
                                )
                            } else if (!it && minors.size > 1) {
                                // Al desactivar: mantener solo el primero
                                minors = listOf(minors.first())
                                currentMinorIndex = 0
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ───────── INDICADOR DE PROGRESO (solo multi-auth) ─────────
        if (isMultiAuth) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Autorización ${currentMinorIndex + 1} de ${minors.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { if (currentMinorIndex > 0) currentMinorIndex-- },
                            enabled = currentMinorIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Anterior")
                        }
                        IconButton(
                            onClick = { if (currentMinorIndex < minors.size - 1) currentMinorIndex++ },
                            enabled = currentMinorIndex < minors.size - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, "Siguiente")
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = (currentMinorIndex + 1).toFloat() / minors.size,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
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

        // ───────── NOMBRE DEL MENOR (único por menor) ─────────
        item {
            OutlinedTextField(
                value = minors[currentMinorIndex].minorName,
                onValueChange = { newValue ->
                    updateCurrentMinor { minor -> minor.copy(minorName = newValue) }
                },
                label = { Text("Nombre del participante ${if (isMultiAuth) "${currentMinorIndex + 1}" else ""} *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting
            )
        }

        // ───────── SEPARADOR Y TÍTULO DE TUTOR ─────────
        item { Divider() }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Datos del Tutor Legal", style = MaterialTheme.typography.titleSmall)
                if (isMultiAuth && currentMinorIndex > 0) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Compartidos", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // ───────── CAMPOS DEL TUTOR (compartidos - solo editables en el primer menor) ─────────
        item {
            OutlinedTextField(
                value = minors[currentMinorIndex].tutorName,
                onValueChange = { newValue ->
                    updateTutorForAll { minor -> minor.copy(tutorName = newValue) }
                },
                label = { Text("Nombre del tutor *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting && (!isMultiAuth || currentMinorIndex == 0),
                readOnly = isMultiAuth && currentMinorIndex > 0
            )
        }
        item {
            OutlinedTextField(
                value = minors[currentMinorIndex].tutorDni,
                onValueChange = { newValue ->
                    val newDni = newValue.uppercase().take(9)
                    updateTutorForAll { minor -> minor.copy(tutorDni = newDni) }
                },
                label = { Text("DNI/NIE *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting && (!isMultiAuth || currentMinorIndex == 0),
                readOnly = isMultiAuth && currentMinorIndex > 0
            )
        }
        item {
            OutlinedTextField(
                value = minors[currentMinorIndex].tutorPhone,
                onValueChange = { newValue ->
                    val newPhone = newValue.filter { c -> c.isDigit() || c == ' ' }.take(15)
                    updateTutorForAll { minor -> minor.copy(tutorPhone = newPhone) }
                },
                label = { Text("Teléfono *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting && (!isMultiAuth || currentMinorIndex == 0),
                readOnly = isMultiAuth && currentMinorIndex > 0
            )
        }
        item { Divider() }

        // ───────── FIRMA ─────────
        item {
            Text("Firma del padre/madre/tutor para ${minors[currentMinorIndex].minorName.ifBlank { "el participante" }} *",
                style = MaterialTheme.typography.titleSmall)

            if (isMultiAuth && currentMinorIndex > 0) {
                // ✅ Menores 2 y 3: mostrar firma del primer menor como read-only
                Text(
                    "✓ Firma compartida con el primer hermano/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                // Preview simple de la firma (texto o icono)
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Firma aplicada",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // ✅ Primer menor o modo único: canvas editable
                Text("Dibuja tu firma en el recuadro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SignatureCanvas(
                    paths = minors[currentMinorIndex].signaturePaths,
                    onPathsChange = { newPaths ->
                        if (isMultiAuth) {
                            // Si es multi-auth, la firma del primer menor se copia a todos
                            minors = minors.map { it.copy(signaturePaths = newPaths) }
                        } else {
                            updateCurrentMinor { it.copy(signaturePaths = newPaths) }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }

        if (minors[currentMinorIndex].signaturePaths.isNotEmpty() && !isSubmitting && (!isMultiAuth || currentMinorIndex == 0)) {
            item {
                OutlinedButton(
                    onClick = {
                        if (isMultiAuth) {
                            updateTutorForAll { it.copy(signaturePaths = emptyList()) }
                        } else {
                            updateCurrentMinor { it.copy(signaturePaths = emptyList()) }
                        }
                    },
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

        // ───────── BOTONES DE ACCIÓN ─────────
        item {
            if (isMultiAuth) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botón Siguiente (solo si no es el último menor)
                    if (currentMinorIndex < minors.size - 1) {
                        Button(
                            onClick = {
                                android.util.Log.d("MultiAuth", "🔘 Pulsado: Siguiente (participante ${currentMinorIndex + 1})")

                                // Validar solo el menor actual antes de avanzar
                                val validationError = validationErrorForMinor(
                                    minors[currentMinorIndex],
                                    currentMinorIndex + 1
                                )
                                if (validationError == null) {
                                    android.util.Log.d("MultiAuth", "✅ Participante ${currentMinorIndex + 1} válido, avanzando")
                                    currentMinorIndex++
                                    coroutineScope.launch { scrollState.animateScrollToItem(0) }
                                } else {
                                    android.util.Log.d("MultiAuth", "❌ Validación fallida para participante ${currentMinorIndex + 1}")
                                    showValidationError(validationError)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Siguiente")
                        }
                    }

                    // Botón Firmar y Terminar (siempre visible desde el segundo menor)
                    Button(
                        onClick = {
                            android.util.Log.d("MultiAuth", "🔘 Pulsado: Firmar y terminar")
                            android.util.Log.d("MultiAuth", "📊 isSubmitting: $isSubmitting")
                            android.util.Log.d("MultiAuth", "📊 minors.size: ${minors.size}")

                            // ✅ CORREGIDO: Filtrar solo menores con nombre rellenado
                            val filledMinors = minors.filter { it.minorName.isNotBlank() }

                            android.util.Log.d("MultiAuth", "📋 Participantes a procesar: ${filledMinors.map { it.minorName }}")

                            // Validar SOLO los menores rellenados
                            val validationErrors = filledMinors.mapIndexedNotNull { index, minor ->
                                validationErrorForMinor(minor, index + 1)
                            }

                            val allValid = filledMinors.all { minor ->
                                val valid = validateMinor(minor)
                                android.util.Log.d("MultiAuth", "🔍 '${minor.minorName}' válido: $valid")
                                valid
                            }

                            android.util.Log.d("MultiAuth", "✅ Validación final: $allValid (filled: ${filledMinors.size})")

                            if (allValid && filledMinors.isNotEmpty()) {
                                android.util.Log.d("MultiAuth", "🚀 Iniciando firma para ${filledMinors.size} participante(s)...")
                                isSubmitting = true
                                showError = false

                                // ✅ Pasar SOLO los menores rellenados al ViewModel
                                viewModel.signMultipleAuthorizations(
                                    excursionId = excursionId,
                                    excursionTitle = excursionTitle,
                                    excursionDate = excursionDate,
                                    minors = filledMinors,  // ← Solo los que tienen nombre
                                    tutorEmail = currentUser?.email ?: "",
                                    onSuccess = {
                                        android.util.Log.d("MultiAuth", "✅ onSuccess llamado")
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        android.util.Log.e("MultiAuth", "❌ onError: $error")
                                        showValidationError(error)
                                        isSubmitting = false
                                    }
                                )
                            } else {
                                android.util.Log.e("MultiAuth", "❌ Validación fallida")
                                showValidationError(if (filledMinors.isEmpty()) {
                                    "Rellena al menos un participante para firmar"
                                } else {
                                    validationErrors.firstOrNull()
                                        ?: "Revisa los datos de los participantes rellenados"
                                })
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Firmando...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Firmar y terminar")
                        }
                    }
                }
            } else {
                // ✅ Autorización única: botón normal
                Button(
                    onClick = {
                        val validationError = validationErrorForMinor(minors.first())
                        if (validationError == null) {
                            isSubmitting = true
                            showError = false
                            viewModel.signAuthorization(
                                excursionTitle = excursionTitle,
                                excursionDate = excursionDate,
                                tutorName = minors.first().tutorName,
                                tutorDni = minors.first().tutorDni,
                                tutorPhone = minors.first().tutorPhone,
                                tutorEmail = currentUser?.email ?: "",
                                minorName = minors.first().minorName,
                                signaturePaths = minors.first().signaturePaths,
                                onSuccess = { onNavigateBack() },
                                onError = { error ->
                                    showValidationError(error)
                                    isSubmitting = false
                                }
                            )
                        } else {
                            showValidationError(validationError)
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
            }
        }

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
