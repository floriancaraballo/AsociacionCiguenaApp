package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio para Noticias
 */
data class News(
    val id: String,
    val title: String,
    val content: String,
    val imageUrl: String?,
    val publishedDate: LocalDateTime,
    val isPublic: Boolean = true,
    val additionalPhotos: List<String> = emptyList()  // URLs de fotos extra
)