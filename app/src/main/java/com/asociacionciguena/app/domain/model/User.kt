package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,  // "admin" o "socio"
    val createdAt: LocalDateTime,
    val photoConsents: List<String>  // IDs de excursiones autorizadas
)