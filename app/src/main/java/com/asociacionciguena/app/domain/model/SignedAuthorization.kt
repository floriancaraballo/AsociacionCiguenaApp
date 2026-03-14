package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class SignedAuthorization(
    val id: String = "",
    val excursionId: String = "",
    val excursionTitle: String = "",
    val excursionDate: LocalDateTime? = null,

    // Datos del tutor
    val userId: String = "",
    val tutorName: String = "",
    val tutorDni: String = "",
    val tutorPhone: String = "",
    val tutorEmail: String = "",

    // Datos del menor (opcional para adultos)
    val minorName: String? = null,

    // Firma y PDF
    val signatureImageUrl: String = "",
    val signedPdfUrl: String = "",

    // Metadatos
    val signedAt: LocalDateTime? = null,
    val emailSent: Boolean = false,
    val status: AuthorizationStatus = AuthorizationStatus.PENDING
)

enum class AuthorizationStatus {
    PENDING,    // Firmada, pendiente de revisión admin
    APPROVED,   // Aprobada por admin
    REJECTED    // Rechazada por admin
}