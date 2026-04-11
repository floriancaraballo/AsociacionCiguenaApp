package com.asociacionciguena.app.presentation.screens.calendar.detail

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
import androidx.compose.ui.res.painterResource

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

    // Photo picker para comprobante
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
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

            // ✅ NUEVA SECCIÓN: Pago con tarjeta (Redsys)
            Text(
                text = "Pago con tarjeta",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    // ✅ AÑADIR ESTE LOG
                    android.util.Log.d("PAYMENT_DEBUG", "🔘 Botón 'Pagar con tarjeta' PULSADO")
                    android.util.Log.d("PAYMENT_DEBUG", "   excursionId: $excursionId")
                    android.util.Log.d("PAYMENT_DEBUG", "   amount: $amount")
                    // ✅ Navegar a WebView de Redsys
                    onNavigateToPayment(excursionId, amount)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(id = com.asociacionciguena.app.R.drawable.ic_payment), // O el icono que tengas
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pagar con tarjeta (TPV Seguro)")
            }

            Text(
                text = "Pago seguro mediante pasarela de Cajasur. Tus datos están protegidos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            // Sección existente: Datos bancarios (transferencia)
            Text(
                text = "Transferencia bancaria",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


            Text(
                text = "Opciones de pago",
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
                                    text = "ES59 0182 5332 1302 0749 5699",
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
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
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
                text = "Sube una captura o foto del justificante. Un administrador lo validará.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}