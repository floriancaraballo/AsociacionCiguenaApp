package com.asociacionciguena.app.presentation.screens.calendar.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.util.BankingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentBottomSheet(
    excursionTitle: String,
    amount: Double,
    userName: String,
    excursionId: String,  // ← AÑADIR: para pasar al pago
    onDismiss: () -> Unit,
    onUploadProof: (Uri) -> Unit,
    onNavigateToPayment: (String, Double) -> Unit
) {
    val context = LocalContext.current
    val concept = "Excursión $excursionTitle - $userName"

    var showBankingDetails by remember { mutableStateOf(false) }

    val proofPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // El permiso temporal del selector sigue siendo suficiente para subirlo al momento.
            }
            onUploadProof(it)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Pagar Excursión",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = excursionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Importe
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Importe",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format("%.2f€", amount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Divider()

            // Sección existente: Datos bancarios (transferencia)
            Text(
                text = "Pago con tarjeta",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { onNavigateToPayment(excursionId, amount) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pagar con tarjeta")
            }

            Text(
                text = "Seras redirigido a la pasarela segura del banco para completar el pago.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text(
                text = "Transferencia bancaria",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Opción 1: Mostrar datos bancarios (más confiable)
            Button(
                onClick = { showBankingDetails = !showBankingDetails },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (showBankingDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showBankingDetails) "Ocultar datos" else "Ver datos bancarios")
            }

            // Mostrar datos automáticamente
            if (showBankingDetails) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // IBAN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "IBAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ES98 2095 8032 20 9171007944",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { BankingUtils.copyIbanToClipboard(context) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar IBAN")
                            }
                        }

                        Divider()

                        // Titular
                        Column {
                            Text(
                                text = "Titular",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Asociación Cigüeña",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Importe
                        Column {
                            Text(
                                text = "Importe",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.2f€", amount),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Concepto
                        Column {
                            Text(
                                text = "Concepto",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = concept,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Divider()

                        Button(
                            onClick = {
                                BankingUtils.copyToClipboard(
                                    context = context,
                                    amount = amount,
                                    concept = concept
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copiar todos los datos")
                        }
                    }
                }
            }

            Divider()

            // Opción 3: Subir comprobante
            Text(
                text = "¿Ya realizaste el pago?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = {
                    proofPickerLauncher.launch(
                        arrayOf(
                            "image/*",
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir comprobante de pago")
            }

            Text(
                text = "Sube una captura, PDF o documento del justificante. Un administrador lo validará.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
