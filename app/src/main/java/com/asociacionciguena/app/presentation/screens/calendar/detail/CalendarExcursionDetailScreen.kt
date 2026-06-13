package com.asociacionciguena.app.presentation.screens.calendar.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.asociacionciguena.app.domain.model.RegistrationClosureReason
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.navigation.Screen  // ✅ Ruta correct// a
import com.asociacionciguena.app.util.formatDateRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarExcursionDetailScreen(
    navController: NavHostController,  // ← NUEVO: Para navegación
    viewModel: CalendarExcursionDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditExcursion: (String) -> Unit,
    onNavigateToAuthorizations: (String, String) -> Unit = { _, _ -> },
    onNavigateToPayments: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Excursión") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (uiState is CalendarExcursionDetailUiState.Success) {
                        val state = uiState as CalendarExcursionDetailUiState.Success
                        if (state.isAdmin) {
                            if (!state.excursion.authorizationPdfUrl.isNullOrBlank()) {
                                IconButton(onClick = {
                                    onNavigateToAuthorizations(state.excursion.id, state.excursion.title)
                                }) {
                                    Icon(Icons.Default.Description, "Ver Autorizaciones")
                                }
                            }
                            if (state.excursion.price != null) {
                                IconButton(onClick = {
                                    onNavigateToPayments(state.excursion.id, state.excursion.title)
                                }) {
                                    Icon(Icons.Default.ReceiptLong, "Ver Comprobantes")
                                }
                            }
                            IconButton(onClick = {
                                onNavigateToEditExcursion(state.excursion.id)
                            }) {
                                Icon(Icons.Default.Edit, "Editar")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CalendarExcursionDetailUiState.Loading -> {
                LoadingIndicator()
            }

            is CalendarExcursionDetailUiState.Success -> {
                ExcursionDetailContent(
                    excursion = state.excursion,
                    isAdmin = state.isAdmin,
                    viewModel = viewModel,
                    navController = navController,  // ← Pasar para navegación
                    onDownloadPdf = { url ->
                        try {
                            android.util.Log.d("PDF_DOWNLOAD", "Intentando descargar: $url")
                            val safeTitle = state.excursion.title
                                .replace(Regex("[^a-zA-Z0-9]"), "_")
                                .take(30)
                                .lowercase()
                            val fileName = "autorizacion_$safeTitle.pdf"
                            android.util.Log.d("PDF_DOWNLOAD", "Nombre de archivo: $fileName")

                            val request = android.app.DownloadManager.Request(Uri.parse(url))
                                .setTitle("Autorización - ${state.excursion.title}")
                                .setDescription("Descargando autorización PDF...")
                                .setMimeType("application/pdf")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(
                                    android.os.Environment.DIRECTORY_DOWNLOADS,
                                    fileName
                                )
                                .setAllowedOverMetered(true)
                                .setAllowedOverRoaming(true)

                            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            val downloadId = downloadManager.enqueue(request)
                            android.util.Log.d("PDF_DOWNLOAD", "Download ID: $downloadId")

                            android.widget.Toast.makeText(
                                context,
                                "Descargando $fileName...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                        } catch (e: Exception) {
                            android.util.Log.e("PDF_DOWNLOAD", "Error al descargar", e)
                            android.widget.Toast.makeText(
                                context,
                                "Error: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onLocationClick = { location, latitude, longitude ->
                        if (!openLocationInMaps(context, location, latitude, longitude)) {
                            Toast.makeText(
                                context,
                                "No se ha encontrado una aplicación para abrir el mapa",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is CalendarExcursionDetailUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

// ───────── FUNCIONES AUXILIARES (al final del archivo) ─────────

@Composable
private fun ExcursionDetailContent(
    excursion: com.asociacionciguena.app.domain.model.Excursion,
    isAdmin: Boolean,
    viewModel: CalendarExcursionDetailViewModel,
    navController: NavHostController,  // ← Para navegar a firma
    onDownloadPdf: (String) -> Unit,
    onLocationClick: (String, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val observedAuthorizationCount by viewModel.activeAuthorizationCount.collectAsState()
    val activeAuthorizationCount = if (isAdmin) {
        observedAuthorizationCount
    } else {
        excursion.currentParticipants
    }
    val hasParticipantLimit = excursion.maxParticipants > 0
    val isCapacityFull = hasParticipantLimit && activeAuthorizationCount >= excursion.maxParticipants
    val isRegistrationClosed = excursion.registrationClosed || isCapacityFull
    val remainingPlaces = if (hasParticipantLimit) {
        (excursion.maxParticipants - activeAuthorizationCount).coerceAtLeast(0)
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Imagen de la excursión
        if (!excursion.imageUrl.isNullOrBlank()) {
            Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                CachedSubcomposeAsyncImage(
                    imageUrl = excursion.imageUrl,
                    contentDescription = excursion.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Título
            Text(text = excursion.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            if (isRegistrationClosed) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column {
                            Text(
                                "Inscripciones cerradas",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                when (excursion.registrationClosureReason) {
                                    RegistrationClosureReason.DEADLINE_PASSED ->
                                        "El plazo de inscripción para esta excursión ha finalizado."
                                    else ->
                                        "Las plazas para esta excursión se han agotado."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Fecha
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column {
                        Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(formatDateRange(excursion.date, excursion.endDate), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // Ubicación
            Card(
                onClick = {
                    onLocationClick(
                        excursion.location,
                        excursion.latitude,
                        excursion.longitude
                    )
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ubicación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(excursion.location, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Abrir en mapas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir ubicación en mapas",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Descripción
            Text("Descripción", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(excursion.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)

            if (hasParticipantLimit) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCapacityFull) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCapacityFull) Icons.Default.Block else Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (isCapacityFull) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column {
                            Text(
                                text = if (isCapacityFull) "Plazas completas" else "Plazas disponibles",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCapacityFull) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${remainingPlaces ?: 0} plaza(s) disponibles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCapacityFull) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Sección de pago
            excursion.price?.let { price ->
                val currentUser by viewModel.currentUser.collectAsState()
                val paymentStatus by viewModel.getPaymentStatus(currentUser?.id ?: "").collectAsState(initial = null)
                val hasSignedForPayment by viewModel.hasUserSignedAuthorization(currentUser?.id ?: "").collectAsState(initial = false)
                val hasApprovedAuthorization by viewModel.hasUserApprovedAuthorization(currentUser?.id ?: "").collectAsState(initial = false)
                val approvedAuthorizationCount by viewModel.getApprovedAuthorizationCount(currentUser?.id ?: "").collectAsState(initial = 0)
                val isUploadingPaymentProof by viewModel.isUploadingPaymentProof.collectAsState()
                val isPaymentBlockedByCapacity = isCapacityFull && !hasSignedForPayment
                val isPaymentBlockedByAuthorization = currentUser != null && !hasApprovedAuthorization
                val paymentParticipantCount = approvedAuthorizationCount.coerceAtLeast(1)
                val paymentAmount = price * paymentParticipantCount
                var showPaymentSheet by remember { mutableStateOf(false) }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when {
                    isUploadingPaymentProof -> MaterialTheme.colorScheme.secondaryContainer
                    paymentStatus?.status == PaymentStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
                    paymentStatus?.status == PaymentStatus.INITIATED -> MaterialTheme.colorScheme.surfaceVariant
                    paymentStatus?.status == PaymentStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                    paymentStatus?.status == PaymentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                })) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Precio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%.2f€", paymentAmount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                if (approvedAuthorizationCount > 1) {
                                    Text("${approvedAuthorizationCount} participantes x ${String.format("%.2f€", price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isUploadingPaymentProof) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                paymentStatus?.status
                                    ?.takeUnless { it == PaymentStatus.INITIATED }
                                    ?.let { PaymentStatusBadge(it) }
                            }
                        }
                        if (isUploadingPaymentProof) {
                            Text(
                                "Subiendo comprobante...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            when(paymentStatus?.status) {
                                PaymentStatus.PAID -> Text("✓ Pago confirmado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                PaymentStatus.INITIATED -> {
                                    if (isPaymentBlockedByCapacity) {
                                        Text("No se puede pagar porque las plazas están completas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (isPaymentBlockedByAuthorization) {
                                        Text("Podrás pagar cuando tu autorización esté aprobada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Button(onClick = { showPaymentSheet = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payment, null); Spacer(Modifier.width(8.dp)); Text("Pagar excursión") }
                                    }
                                }
                                PaymentStatus.PENDING -> Text("Tu comprobante está pendiente de validación", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                PaymentStatus.REJECTED -> {
                                    Text("Comprobante rechazado. Contacta con un administrador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    if (isPaymentBlockedByCapacity) {
                                        Text("No se puede reintentar el pago porque las plazas están completas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    } else if (isPaymentBlockedByAuthorization) {
                                        Text("Podrás subir el comprobante cuando tu autorización esté aprobada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    } else {
                                        Button(onClick = { showPaymentSheet = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payment, null); Spacer(Modifier.width(8.dp)); Text("Intentar de nuevo") }
                                    }
                                }
                                null -> {
                                    if (isPaymentBlockedByCapacity) {
                                        Text("No se puede pagar porque las plazas están completas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (isPaymentBlockedByAuthorization) {
                                        Text("Podrás subir el comprobante cuando tu autorización esté aprobada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (currentUser != null) {
                                        Button(onClick = { showPaymentSheet = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payment, null); Spacer(Modifier.width(8.dp)); Text("Pagar excursión") }
                                    } else {
                                        Text("Inicia sesión para pagar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if (showPaymentSheet && currentUser != null && hasApprovedAuthorization) {
                    PaymentBottomSheet(
                        excursionTitle = excursion.title,
                        amount = paymentAmount,
                        userName = currentUser?.displayName ?: "",
                        excursionId = excursion.id,  // ← Asegúrate de pasar esto
                        onDismiss = { showPaymentSheet = false },
                        onUploadProof = { uri ->
                            viewModel.uploadPaymentProof(excursion.id, paymentAmount, uri)
                            showPaymentSheet = false
                        },
                        onNavigateToPayment = { excursionId, amount ->
                            showPaymentSheet = false
                            navController.navigate(Screen.Payment.createRoute(excursionId, amount))
                        }
                    )
                }
            }

            // Sección de firma de autorización
            if (!excursion.authorizationPdfUrl.isNullOrBlank()) {
                val currentUser by viewModel.currentUser.collectAsState()
                val hasSigned by viewModel.hasUserSignedAuthorization(currentUser?.id ?: "").collectAsState(initial = false)
                val showAuthSuccess by viewModel.showAuthSuccess.collectAsState()

                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Autorización", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                // 🔍 DEBUG: Verificar estados
                LaunchedEffect(hasSigned, showAuthSuccess) {
                    android.util.Log.d("AuthDebug", "👀 UI: hasSigned=$hasSigned, showAuthSuccess=$showAuthSuccess")
                }

                if (showAuthSuccess) {
                    android.util.Log.d("AuthDebug", "🎨 Renderizando Card temporal de éxito")  // ← Añadir esto
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("¡Autorización firmada!", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Recibirás una copia por email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.dismissAuthSuccess() }
                }

                Card(colors = CardDefaults.cardColors(containerColor = if (hasSigned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasSigned) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Column {
                                    Text("Autorización Firmada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Ya has firmado esta autorización", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Para participar en esta excursión necesitas firmar la autorización digitalmente.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isRegistrationClosed) {
                                Text(
                                    when (excursion.registrationClosureReason) {
                                        RegistrationClosureReason.DEADLINE_PASSED ->
                                            "No se puede firmar porque el plazo de inscripción ha finalizado."
                                        else ->
                                            "No se puede firmar porque las plazas están completas."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (currentUser != null) {
                                // ✅ NAVEGACIÓN: En lugar de mostrar SignatureScreen, navega a la ruta
                                Button(
                                    onClick = { navController.navigate(Screen.Signature.createRoute(excursion.id)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Draw, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Firmar Autorización")
                                }
                            } else {
                                Text("Inicia sesión para firmar la autorización", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onDownloadPdf(excursion.authorizationPdfUrl) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar PDF de Referencia")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openLocationInMaps(
    context: Context,
    location: String,
    latitude: Double?,
    longitude: Double?
): Boolean {
    val normalizedLocation = location.trim()
    if (normalizedLocation.isEmpty()) return false

    val hasCoordinates = latitude != null && longitude != null
    val query = if (hasCoordinates) {
        "$latitude,$longitude"
    } else {
        normalizedLocation
    }
    val encodedQuery = Uri.encode(query)
    val encodedLabel = Uri.encode(normalizedLocation)
    val mapIntents = listOf(
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                if (hasCoordinates) {
                    "geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)"
                } else {
                    "geo:0,0?q=$encodedQuery"
                }
            )
        ),
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
        )
    )

    return mapIntents.any { intent ->
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun PaymentStatusBadge(status: PaymentStatus) {
    val containerColor = when (status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.primary
        PaymentStatus.INITIATED -> MaterialTheme.colorScheme.surfaceVariant
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
        PaymentStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.onPrimary
        PaymentStatus.INITIATED -> MaterialTheme.colorScheme.onSurfaceVariant
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
        PaymentStatus.REJECTED -> MaterialTheme.colorScheme.onError
    }
    val label = when (status) {
        PaymentStatus.PAID -> "Pagado"
        PaymentStatus.INITIATED -> "Iniciado"
        PaymentStatus.PENDING -> "Pendiente"
        PaymentStatus.REJECTED -> "Rechazado"
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 92.dp, minHeight = 32.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
