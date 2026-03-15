package com.asociacionciguena.app.presentation.screens.calendar.detail

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.asociacionciguena.app.presentation.navigation.Screen  // ✅ Ruta correct// a
import com.asociacionciguena.app.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarExcursionDetailScreen(
    navController: NavHostController,  // ← NUEVO: Para navegación
    viewModel: CalendarExcursionDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditExcursion: (String) -> Unit,
    onNavigateToAuthorizations: (String, String) -> Unit = { _, _ -> }
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
    viewModel: CalendarExcursionDetailViewModel,
    navController: NavHostController,  // ← Para navegar a firma
    onDownloadPdf: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Imagen de la excursión
        if (!excursion.imageUrl.isNullOrBlank()) {
            Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                SubcomposeAsyncImage(
                    model = excursion.imageUrl,
                    contentDescription = excursion.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                    error = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.BrokenImage, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Título
            Text(text = excursion.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            // Fecha
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column {
                        Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(formatDate(excursion.date), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // Ubicación
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column {
                        Text("Ubicación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(excursion.location, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // Descripción
            Text("Descripción", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(excursion.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)

            // Sección de pago
            excursion.price?.let { price ->
                val currentUser by viewModel.currentUser.collectAsState()
                val paymentStatus by viewModel.getPaymentStatus(currentUser?.id ?: "").collectAsState(initial = null)
                var showPaymentSheet by remember { mutableStateOf(false) }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when(paymentStatus?.status) {
                    PaymentStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
                    PaymentStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                    PaymentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                })) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Precio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%.2f€", price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            when(paymentStatus?.status) {
                                PaymentStatus.PAID -> AssistChip(onClick = {}, label = { Text("Pagado") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onPrimary))
                                PaymentStatus.PENDING -> AssistChip(onClick = {}, label = { Text("Pendiente") }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                                PaymentStatus.REJECTED -> AssistChip(onClick = {}, label = { Text("Rechazado") }, leadingIcon = { Icon(Icons.Default.Cancel, null) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.error, labelColor = MaterialTheme.colorScheme.onError))
                                null -> {}
                            }
                        }
                        when(paymentStatus?.status) {
                            PaymentStatus.PAID -> Text("✓ Pago confirmado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            PaymentStatus.PENDING -> Text("Tu comprobante está pendiente de validación", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            PaymentStatus.REJECTED -> {
                                Text("Comprobante rechazado. Contacta con un administrador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                Button(onClick = { showPaymentSheet = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payment, null); Spacer(Modifier.width(8.dp)); Text("Intentar de nuevo") }
                            }
                            null -> {
                                if (currentUser != null) {
                                    Button(onClick = { showPaymentSheet = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payment, null); Spacer(Modifier.width(8.dp)); Text("Pagar excursión") }
                                } else {
                                    Text("Inicia sesión para pagar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (showPaymentSheet && currentUser != null) {
                    PaymentBottomSheet(
                        excursionTitle = excursion.title, amount = price,
                        userName = currentUser?.displayName ?: "Usuario",
                        onDismiss = { showPaymentSheet = false },
                        onUploadProof = { uri -> viewModel.uploadPaymentProof(excursionId = excursion.id, amount = price, photoUri = uri) }
                    )
                }
            }

            // Sección de firma de autorización
            if (!excursion.authorizationPdfUrl.isNullOrBlank()) {
                val currentUser by viewModel.currentUser.collectAsState()
                val hasSigned by viewModel.hasUserSignedAuthorization(currentUser?.id ?: "").collectAsState(initial = false)
                var showSuccessMessage by remember { mutableStateOf(false) }
                var showErrorMessage by remember { mutableStateOf(false) }
                var errorText by remember { mutableStateOf("") }

                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Autorización", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                if (showSuccessMessage) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("¡Autorización firmada!", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Recibirás una copia por email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(5000); showSuccessMessage = false }
                }
                if (showErrorMessage) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(errorText, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showErrorMessage = false }) { Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
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
                            if (currentUser != null) {
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