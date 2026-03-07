package com.asociacionciguena.app.data.dto

import com.google.firebase.Timestamp

/**
 * DTO para Noticias desde Firestore
 *
 * Estructura en Firestore:
 * news/{newsId}
 *   - title: String
 *   - content: String
 *   - imageUrl: String?
 *   - publishedDate: Timestamp
 *   - isPublic: Boolean
 */
data class NewsDto(
    val id: String = "",
    val title: String = "",
    val shortDescription: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val isPublic: Boolean = true,
    val additionalPhotos: List<String> = emptyList()
) {
    /**
     * Constructor sin parámetros requerido por Firebase
     */
    constructor() : this(
        id = "",
        title = "",
        shortDescription = "",  // ← NUEVO
        content = "",
        imageUrl = null,
        createdAt = null,
        updatedAt = null,
        isPublic = true,
        additionalPhotos = emptyList()
    )
}