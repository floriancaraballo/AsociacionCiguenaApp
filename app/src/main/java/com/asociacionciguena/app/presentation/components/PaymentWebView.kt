package com.asociacionciguena.app.presentation.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.screens.payment.PaymentViewModel
import com.asociacionciguena.app.presentation.screens.payment.PaymentUiState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebView(
    excursionId: String,
    amount: Double,
    onPaymentSuccess: () -> Unit,
    onPaymentError: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewConfig = viewModel.viewConfig

    LaunchedEffect(Unit) {
        viewModel.createPaymentIntent(excursionId, amount)
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PaymentUiState.Success -> onPaymentSuccess()
            is PaymentUiState.Error -> onPaymentError(state.message)
            else -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PaymentUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(viewConfig.loadingTitle)
                }
            }
            is PaymentUiState.PaymentReady -> {
                // ✅ Escapar valores para JavaScript (prevenir inyección)
                fun escapeForJs(value: String): String {
                    return value.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                }

                val params = escapeForJs(state.params["Ds_MerchantParameters"] ?: "")
                val signature = escapeForJs(state.params["Ds_Signature"] ?: "")
                val progressColor = escapeForJs(viewConfig.progressColor)
                val redirectMessage = escapeForJs(viewConfig.redirectMessage)

                val html = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
body { 
    font-family: Arial, sans-serif; 
    display: flex; 
    justify-content: center; 
    align-items: center; 
    height: 100vh; 
    margin: 0;
    background: #f5f5f5;
}
.loader {
    text-align: center;
}
.spinner {
    border: 4px solid #f3f3f3;
    border-top: 4px solid $progressColor;
    border-radius: 50%;
    width: 40px;
    height: 40px;
    animation: spin 1s linear infinite;
    margin: 0 auto 16px;
}
@keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}
</style>
</head>
<body>
<div class="loader">
    <div class="spinner"></div>
    <p>$redirectMessage</p>
</div>
<form id="redsysForm" method="POST" action="${state.tpvUrl}" accept-charset="UTF-8">
    <input type="hidden" name="Ds_SignatureVersion" value="HMAC_SHA256_V1">
    <input type="hidden" name="Ds_MerchantParameters" value='$params'>
    <input type="hidden" name="Ds_Signature" value='$signature'>
</form>
<script>
document.getElementById('redsysForm').submit();
</script>
</body>
</html>
""".trimIndent()

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Mantener el WebView limitado al formulario de pago servido en memoria.
                            settings.allowFileAccess = false

                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        viewModel.onPaymentLoadFailed(
                                            "No se pudo cargar Redsys: ${error?.description ?: "error de conexion"}"
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        viewModel.onPaymentLoadFailed(
                                            "Redsys devolvio un error HTTP ${errorResponse?.statusCode ?: ""}".trim()
                                        )
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.contains("paymentResult")) {
                                        val uri = android.net.Uri.parse(url)
                                        val status = uri.getQueryParameter("status")
                                        if (status == "success") {
                                            viewModel.onPaymentCompleted(true)
                                        } else {
                                            viewModel.onPaymentCompleted(false)
                                        }
                                        return true
                                    }
                                    return false
                                }
                            }

                            // ✅ Cargar HTML con encoding UTF-8 explícito
                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {}
        }
    }
}
