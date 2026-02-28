package com.asociacionciguena.app.data.dto

import com.google.firebase.Timestamp

/**
 * DTO para Usuarios desde Firestore
 */
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "socio",  // "admin" o "socio"
    val createdAt: Timestamp? = null,
    val photoConsents: List<String> = emptyList()  // IDs de excursiones autorizadas
) {
    constructor() : this("", "", "", "socio", null, emptyList())
}