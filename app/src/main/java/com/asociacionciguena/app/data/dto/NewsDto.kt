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
    val content: String = "",
    val imageUrl: String? = null,
    val publishedDate: Timestamp? = null,
    val isPublic: Boolean = true,
    val additionalPhotos: List<String> = emptyList()
) {
    /**
     * Constructor sin parámetros requerido por Firebase
     */
    constructor() : this(
        id = "",
        title = "",
        content = "",
        imageUrl = null,
        publishedDate = null,
        isPublic = true,
        additionalPhotos = emptyList()
    )
}