package com.asociacionciguena.app.data.dto

import com.google.firebase.Timestamp

/**
 * DTO para Fotos desde Firestore
 */
data class PhotoDto(
    val id: String = "",
    val excursionId: String = "",
    val imageUrl: String = "",
    val storagePath: String = "",
    val uploadedBy: String = "",
    val uploadedAt: Timestamp? = null,
    val authorizedUsers: List<String> = emptyList()  // UIDs con permiso
) {
    constructor() : this("", "", "", "", "", null, emptyList())
}