package com.asociacionciguena.app.data.dto

import com.google.firebase.Timestamp

/**
 * DTO para Usuarios desde Firestore
 */
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "socio",  // "superadmin", "admin", "monitor" o "socio"
    val createdAt: Timestamp? = null,
    val photoConsents: List<String> = emptyList()  // IDs de excursiones autorizadas
) {
    constructor() : this("", "", "", "","",null, emptyList())
}
