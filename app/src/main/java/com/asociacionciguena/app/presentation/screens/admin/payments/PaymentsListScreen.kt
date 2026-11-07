package com.asociacionciguena.app.presentation.screens.admin.payments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.Payment
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsListScreen(
    excursionId: String,
    excursionTitle: String,
    viewModel: PaymentsListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val payments by viewModel.payments.collectAsState()
    val proofPayments = remember(payments) {
        payments.filter { !it.paymentProofUrl.isNullOrBlank() }
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(excursionId) {
        viewModel.loadPayments(excursionId)
    }

    LaunchedEffect(message) {
        val currentMessage = message
        if (!currentMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(currentMessage)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Comprobantes de pago") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PaymentSummaryCard(
                excursionTitle = excursionTitle,
                payments = proofPayments,
                isLoading = isLoading,
                modifier = Modifier.padding(16.dp)
            )

            if (proofPayments.isEmpty() && !isLoading) {
                EmptyPayments()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(proofPayments, key = { it.id }) { payment ->
                        PaymentCard(
                            payment = payment,
                            onApprove = { viewModel.approvePayment(payment.id, excursionId) },
                            onReject = { viewModel.rejectPayment(payment.id, excursionId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentSummaryCard(
    excursionTitle: String,
    payments: List<Payment>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = excursionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PaymentStatus.PENDING, PaymentStatus.PAID, PaymentStatus.REJECTED).forEach { status ->
                    val count = payments.count { it.status == status }
                    val label = when (status) {
                        PaymentStatus.PENDING -> "$count pendientes"
                        PaymentStatus.PAID -> "$count pagados"
                        PaymentStatus.REJECTED -> "$count rechazados"
                        PaymentStatus.INITIATED -> "$count iniciados"
                    }
                    PaymentStatusChip(status = status, label = label)
                }
            }
        }
    }
}

@Composable
private fun EmptyPayments() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No hay comprobantes subidos",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentCard(
    payment: Payment,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current

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
                text = payment.participantNames
                    .takeIf { names -> names.isNotEmpty() }
                    ?.joinToString(", ")
                    ?: payment.userName.ifBlank { "Participante sin nombre" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Importe",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.2f EUR", payment.amount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                PaymentStatusChip(payment.status)
            }

            payment.paymentProofUrl?.takeIf { it.isNotBlank() }?.let { proofUrl ->
                val contentType = payment.paymentProofContentType.orEmpty()
                val isImage = contentType.startsWith("image/") || contentType.isBlank()

                if (isImage) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        CachedSubcomposeAsyncImage(
                            imageUrl = proofUrl,
                            contentDescription = "Comprobante de ${payment.userName}",
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = payment.paymentProofFileName ?: "Comprobante adjunto",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = contentType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(proofUrl)).apply {
                            if (contentType.isNotBlank()) {
                                setDataAndType(Uri.parse(proofUrl), contentType)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir comprobante")
                }
            }

            if (payment.status == PaymentStatus.PENDING) {
                HorizontalDivider()

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onApprove
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aprobar")
                    }

                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rechazar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentStatusChip(status: PaymentStatus, label: String? = null) {
    val containerColor = when (status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
        PaymentStatus.INITIATED -> MaterialTheme.colorScheme.surfaceVariant
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
        PaymentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.onPrimaryContainer
        PaymentStatus.INITIATED -> MaterialTheme.colorScheme.onSurfaceVariant
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
        PaymentStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val statusLabel = label ?: when (status) {
        PaymentStatus.PAID -> "Pagado"
        PaymentStatus.INITIATED -> "Iniciado"
        PaymentStatus.PENDING -> "Pendiente"
        PaymentStatus.REJECTED -> "Rechazado"
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
