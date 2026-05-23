package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class Payment(
    val id: String = "",
    val excursionId: String = "",
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val paymentProofUrl: String? = null,  // URL del comprobante
    val paymentProofContentType: String? = null,
    val paymentProofFileName: String? = null,
    val createdAt: LocalDateTime? = null,
    val validatedAt: LocalDateTime? = null,
    val validatedBy: String? = null  // UID del admin que validó
)

enum class PaymentStatus {
    PENDING,     // Pendiente de pago/validación
    PAID,        // Pagado y validado
    REJECTED     // Rechazado por admin
}
