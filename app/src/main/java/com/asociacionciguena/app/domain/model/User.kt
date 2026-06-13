package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: String,  // "superadmin", "admin", "monitor" o "socio"
    val createdAt: LocalDateTime,
    val photoConsents: List<String>  // IDs de excursiones autorizadas
)
