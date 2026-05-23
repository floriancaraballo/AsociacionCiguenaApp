package com.asociacionciguena.app.presentation.screens.admin.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.repository.PaymentRepository
import com.asociacionciguena.app.domain.model.Payment
import com.asociacionciguena.app.domain.model.PaymentStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PaymentsListViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadPayments(excursionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (!isCurrentUserAdmin()) {
                    _payments.value = emptyList()
                    _message.value = "No tienes permisos para ver comprobantes"
                    return@launch
                }

                paymentRepository.getExcursionPayments(excursionId).collect { payments ->
                    _payments.value = payments
                }
            } catch (e: Exception) {
                _message.value = "Error al cargar comprobantes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approvePayment(paymentId: String, excursionId: String) {
        updatePayment(paymentId, PaymentStatus.PAID, excursionId)
    }

    fun rejectPayment(paymentId: String, excursionId: String) {
        updatePayment(paymentId, PaymentStatus.REJECTED, excursionId)
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun updatePayment(
        paymentId: String,
        status: PaymentStatus,
        excursionId: String
    ) {
        viewModelScope.launch {
            if (!isCurrentUserAdmin()) {
                _payments.value = emptyList()
                _message.value = "No tienes permisos para validar comprobantes"
                return@launch
            }

            val result = paymentRepository.updatePaymentStatus(paymentId, status)
            if (result.isSuccess) {
                _message.value = when (status) {
                    PaymentStatus.PAID -> "Comprobante aprobado"
                    PaymentStatus.REJECTED -> "Comprobante rechazado"
                    PaymentStatus.PENDING -> null
                }
                loadPayments(excursionId)
            } else {
                _message.value = "Error al actualizar: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private suspend fun isCurrentUserAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val userDoc = firestore.collection("users")
            .document(uid)
            .get()
            .await()
        val role = userDoc.getString("role")
        return role == "admin" || role == "superadmin"
    }
}
