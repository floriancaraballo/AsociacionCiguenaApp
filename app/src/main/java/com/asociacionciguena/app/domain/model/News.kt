package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio para Noticias
 */
data class News(
    val id: String,
    val title: String,
    val shortDescription: String,
    val content: String,  // Contenido completo para el detalle
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,  // ← NUEVO
    val isPublic: Boolean = true,
    val additionalPhotos: List<String> = emptyList()  // URLs de fotos extra
)