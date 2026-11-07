package com.asociacionciguena.app.presentation.screens.admin.authorizations

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.SignedAuthorization
import com.asociacionciguena.app.domain.model.AuthorizationStatus
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.asociacionciguena.app.presentation.screens.admin.payments.PaymentsListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizationsListScreen(
    excursionId: String,
    excursionTitle: String,
    viewModel: AuthorizationsListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(excursionId) {
        android.util.Log.d("AuthListDebug", "🚀 Screen iniciado - excursionId: $excursionId")
    }
    val authorizations by remember(viewModel, excursionId) {
        viewModel.getAuthorizations(excursionId)
    }.collectAsState(initial = emptyList())
    val paymentsViewModel: PaymentsListViewModel = hiltViewModel()
    val payments by paymentsViewModel.payments.collectAsState()
    val paymentsLoading by paymentsViewModel.isLoading.collectAsState()
    var isGeneratingDoc by remember { mutableStateOf(false) }
    var showDocPreview by remember { mutableStateOf(false) }
    var docDownloadUrl by remember { mutableStateOf<String?>(null) }
    var docFileName by remember { mutableStateOf<String?>(null) }
    var authorizationToReopen by remember(excursionId) { mutableStateOf<SignedAuthorization?>(null) }
    var isReopening by remember(excursionId) { mutableStateOf(false) }
    val usersWithActiveAuthorization = remember(authorizations) {
        authorizations.filter { it.status != AuthorizationStatus.REJECTED }.map { it.userId }.toSet()
    }
    val usersWithResubmission = remember(authorizations) {
        authorizations.filter {
            it.status == AuthorizationStatus.REJECTED && it.resubmissionGranted && !it.resubmissionUsed
        }.map { it.userId }.toSet()
    }
    val context = LocalContext.current

    LaunchedEffect(excursionId) {
        paymentsViewModel.loadPayments(excursionId)
    }

    LaunchedEffect(authorizations) {
        android.util.Log.d("AuthListDebug", "📋 Autorizaciones cargadas: ${authorizations.size}")
        authorizations.forEach { auth ->
            android.util.Log.d("AuthListDebug", "   • ${auth.tutorName} - Status: ${auth.status} - ID: ${auth.id}")
        }
    }

    val generateDocument: () -> Unit = {
        isGeneratingDoc = true
        viewModel.generateMinorsListDocx(
            excursionId = excursionId,
            onSuccess = { url, fileName ->
                docDownloadUrl = url
                docFileName = fileName
                isGeneratingDoc = false
                showDocPreview = true
            },
            onError = { error ->
                isGeneratingDoc = false
                Toast.makeText(context, "❌ $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autorizaciones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AuthorizationSummary(
                excursionTitle = excursionTitle,
                authorizations = authorizations,
                isGeneratingDoc = isGeneratingDoc,
                onGenerateDocument = generateDocument
            )

            // Lista de autorizaciones
            if (authorizations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No hay autorizaciones firmadas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(authorizations, key = { it.id }) { authorization ->
                        android.util.Log.d("AuthCard", "✅ Mostrando botones de aprobar/rechazar")
                        AuthorizationCard(
                            authorization = authorization,
                            paymentStatus = payments
                                .filter { it.userId == authorization.userId }
                                .let { userPayments ->
                                    userPayments.firstOrNull { it.status == PaymentStatus.PAID }?.status
                                        ?: userPayments.firstOrNull()?.status
                                },
                            isPaymentLoading = paymentsLoading,
                            onDownloadPdf = { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onApprove = { authId ->
                                viewModel.approveAuthorization(authId)
                            },
                            onReject = { authId ->
                                viewModel.rejectAuthorization(authId)
                            },
                            onAllowResubmission = if (
                                authorization.status == AuthorizationStatus.REJECTED &&
                                !authorization.resubmissionUsed &&
                                authorization.userId !in usersWithActiveAuthorization &&
                                authorization.userId !in usersWithResubmission
                            ) {
                                { authorizationToReopen = authorization }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
    authorizationToReopen?.let { authorization ->
        AlertDialog(
            onDismissRequest = { if (!isReopening) authorizationToReopen = null },
            title = { Text("Permitir una nueva firma") },
            text = {
                Text(
                    "${authorization.tutorName} podrá enviar una nueva autorización para " +
                        "$excursionTitle si la inscripción está abierta y hay plazas."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isReopening,
                    onClick = {
                        isReopening = true
                        viewModel.allowResubmission(authorization.id) { message ->
                            isReopening = false
                            authorizationToReopen = null
                            android.widget.Toast.makeText(
                                context, message, android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) { Text("Permitir nueva firma") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isReopening,
                    onClick = { authorizationToReopen = null }
                ) { Text("Cancelar") }
            }
        )
    }

    // ───────── DIALOG: Preview del documento generado ─────────
    if (showDocPreview && docDownloadUrl != null) {
        AlertDialog(
            onDismissRequest = { showDocPreview = false },
            title = { Text("📄 Lista generada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("El documento \"$docFileName\" se ha generado correctamente.")
                    Text(
                        "Contiene ${authorizations.size} participantes en formato Word editable.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Puedes descargarlo o enviarlo directamente a la asociación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = docDownloadUrl ?: return@Button
                        try {
                            val uri = Uri.parse(url)
                            require(uri.scheme == "https" && !uri.host.isNullOrBlank())

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val baseName = docFileName.orEmpty()
                                    .removeSuffix(".docx")
                                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                    .take(80)
                                    .ifBlank { "lista" }
                                val fileName = "${baseName}_${System.currentTimeMillis()}.docx"
                                val request = DownloadManager.Request(uri)
                                    .setTitle("Lista de participantes")
                                    .setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                val downloadManager = checkNotNull(context.getSystemService(DownloadManager::class.java))
                                check(downloadManager.enqueue(request) > 0)
                                Toast.makeText(context, "Descarga iniciada", Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            }
                            showDocPreview = false
                        } catch (_: RuntimeException) {
                            Toast.makeText(
                                context,
                                "No se pudo iniciar la descarga de la lista",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Descargar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        // 📧 Enviar email con enlace de descarga (sin adjunto)
                        val emailIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"

                            // Destinatario
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("asociacionciguena1993@gmail.com"))

                            // Asunto
                            putExtra(Intent.EXTRA_SUBJECT, "📋 Lista de participantes - $excursionTitle")

                            // Cuerpo del email con el enlace
                            val emailBody = """
                    Hola,
                    
                    Adjunto el enlace para descargar la lista de participantes de la excursión:
                    
                    🔗 $docDownloadUrl
                    
                    📄 Nombre del archivo: $docFileName
                    👥 Total participantes: ${authorizations.size}
                    
                    ⏰ Este enlace expira en 1 hora por seguridad.
                    
                    ---
                    Generado automáticamente desde la app de Asociación Cigüeña
                    ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES")).format(java.util.Date())}
                """.trimIndent()

                            putExtra(Intent.EXTRA_TEXT, emailBody)

                            // Para que funcione con Gmail y otros clientes
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // Abrir selector de apps de email
                        val chooser = Intent.createChooser(emailIntent, "Enviar por email")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)

                        // Cerrar el dialog
                        showDocPreview = false

                        // Confirmación visual
                        android.widget.Toast.makeText(
                            context,
                            "✅ Abriendo cliente de email...",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    enabled = docDownloadUrl != null
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Enviar a Asociación")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorizationSummary(
    excursionTitle: String,
    authorizations: List<SignedAuthorization>,
    isGeneratingDoc: Boolean,
    onGenerateDocument: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                excursionTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuthorizationStatusLabel(
                    "${authorizations.count { it.status == AuthorizationStatus.PENDING }} pendientes",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
                AuthorizationStatusLabel(
                    "${authorizations.count { it.status == AuthorizationStatus.APPROVED }} aprobadas",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                AuthorizationStatusLabel(
                    "${authorizations.count { it.status == AuthorizationStatus.REJECTED }} rechazadas",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Button(
                onClick = onGenerateDocument,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGeneratingDoc && authorizations.any {
                    it.status == AuthorizationStatus.APPROVED && !it.minorName.isNullOrBlank()
                }
            ) {
                if (isGeneratingDoc) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isGeneratingDoc) "Generando lista…" else "Generar lista Word")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorizationCard(
    authorization: SignedAuthorization,
    paymentStatus: PaymentStatus?,
    isPaymentLoading: Boolean,
    onDownloadPdf: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onAllowResubmission: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = authorization.minorName?.takeIf { it.isNotBlank() } ?: "Menor sin nombre",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Autorización", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                AuthorizationStatusLabel(
                    text = when (authorization.status) {
                        AuthorizationStatus.APPROVED -> "✓ Aprobada"
                        AuthorizationStatus.PENDING -> "Pendiente"
                        AuthorizationStatus.REJECTED -> "Rechazada"
                    },
                    containerColor = when (authorization.status) {
                        AuthorizationStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
                        AuthorizationStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                        AuthorizationStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = when (authorization.status) {
                        AuthorizationStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
                        AuthorizationStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                        AuthorizationStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Pago", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                AuthorizationStatusLabel(
                    text = when {
                        isPaymentLoading -> "Cargando…"
                        paymentStatus == null -> "No realizado"
                        paymentStatus == PaymentStatus.PAID -> "✓ Realizado"
                        paymentStatus == PaymentStatus.PENDING -> "Pendiente de validación"
                        paymentStatus == PaymentStatus.REJECTED -> "Rechazado"
                        else -> "Iniciado"
                    },
                    containerColor = when {
                        isPaymentLoading || paymentStatus == PaymentStatus.INITIATED || paymentStatus == null -> MaterialTheme.colorScheme.surfaceVariant
                        paymentStatus == PaymentStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
                        paymentStatus == PaymentStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = when {
                        isPaymentLoading || paymentStatus == PaymentStatus.INITIATED || paymentStatus == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        paymentStatus == PaymentStatus.PAID -> MaterialTheme.colorScheme.onPrimaryContainer
                        paymentStatus == PaymentStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            if (authorization.status == AuthorizationStatus.REJECTED &&
                authorization.resubmissionGranted && !authorization.resubmissionUsed
            ) {
                Text("Nueva firma habilitada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (authorization.status == AuthorizationStatus.PENDING) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onApprove(authorization.id) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aprobar")
                    }
                    OutlinedButton(
                        onClick = { onReject(authorization.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rechazar")
                    }
                }
            }
            if (onAllowResubmission != null) {
                OutlinedButton(onClick = onAllowResubmission, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Permitir nueva firma")
                }
            }
            if (authorization.tutorEmail.isNotBlank() || authorization.tutorPhone.isNotBlank()) {
                HorizontalDivider()
                if (authorization.tutorEmail.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = "Email del tutor", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(authorization.tutorEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
                if (authorization.tutorPhone.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = "Teléfono del tutor", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(authorization.tutorPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizationStatusLabel(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
