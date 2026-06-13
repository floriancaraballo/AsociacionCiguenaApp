package com.asociacionciguena.app.presentation.screens.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.repository.PaymentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class PaymentUiState {
    object Loading : PaymentUiState()
    data class PaymentReady(
        val orderId: String,
        val tpvUrl: String,
        val params: Map<String, String>
    ) : PaymentUiState()
    object Success : PaymentUiState()
    data class Error(val message: String) : PaymentUiState()
}

data class PaymentViewConfig(
    val loadingTitle: String = "Conectando con la pasarela de pago segura...",
    val redirectMessage: String = "Redirigiendo a Redsys...",
    val progressColor: String = "#1976D2",
    val errorPrefix: String = "Error al iniciar el pago"
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Loading)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    val viewConfig = PaymentViewConfig()

    fun createPaymentIntent(excursionId: String, amount: Double) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    _uiState.value = PaymentUiState.Error("Debes estar logueado para realizar un pago")
                    return@launch
                }

                // Obtener datos del usuario para el pago
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val userName = userDoc.getString("displayName") ?: user.email ?: "Usuario"
                val userEmail = user.email ?: ""

                // Llamar a Cloud Function para crear intención de pago
                val result = paymentRepository.createPaymentIntent(
                    excursionId = excursionId,
                    amount = amount,
                    userName = userName,
                    userEmail = userEmail
                )

                // Actualizar estado con URL y parámetros para el WebView
                _uiState.value = PaymentUiState.PaymentReady(
                    orderId = result.orderId,
                    tpvUrl = result.tpvUrl,
                    params = result.params
                )

            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Error("${viewConfig.errorPrefix}: ${e.message}")
            }
        }
    }

    fun onPaymentCompleted(success: Boolean) {
        if (success) {
            _uiState.value = PaymentUiState.Success
        } else {
            _uiState.value = PaymentUiState.Error("El pago no se ha completado")
        }
    }

    fun onPaymentLoadFailed(message: String) {
        _uiState.value = PaymentUiState.Error(message)
    }

    fun cancelCurrentPayment(onFinished: () -> Unit) {
        onFinished()
    }
}
