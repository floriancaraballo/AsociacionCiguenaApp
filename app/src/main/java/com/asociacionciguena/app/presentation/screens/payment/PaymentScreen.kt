package com.asociacionciguena.app.presentation.screens.payment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.PaymentWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    excursionId: String,
    amount: Double,
    onPaymentSuccess: () -> Unit,
    onPaymentError: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val cancelAndNavigateBack = {
        viewModel.cancelCurrentPayment(onFinished = onNavigateBack)
    }

    BackHandler(onBack = cancelAndNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pago Seguro") },
                navigationIcon = {
                    IconButton(onClick = cancelAndNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            PaymentWebView(
                excursionId = excursionId,
                amount = amount,
                onPaymentSuccess = onPaymentSuccess,
                onPaymentError = onPaymentError,
                onNavigateBack = cancelAndNavigateBack,
                viewModel = viewModel
            )
        }
    }
}
