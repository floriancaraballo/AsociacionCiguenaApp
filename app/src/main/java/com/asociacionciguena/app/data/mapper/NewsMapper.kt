package com.asociacionciguena.app.data.mapper

import com.asociacionciguena.app.data.dto.NewsDto
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.util.toLocalDateTime
import kotlinx.datetime.LocalDateTime

/**
 * Extensión para convertir NewsDto → News (Domain Model)
 */
fun NewsDto.toDomain(): News {
    return News(
        id = id,
        title = title,
        shortDescription = shortDescription,
        content = content,
        imageUrl = imageUrl,
        additionalPhotos = additionalPhotos,
        createdAt = createdAt?.toLocalDateTime()
            ?: LocalDateTime(2025, 1, 1, 0, 0),
        updatedAt = updatedAt?.toLocalDateTime(),  // ← NUEVO
        isPublic = isPublic
    )
}

/**
 * Extensión para convertir News (Domain) → NewsDto
 * Útil si necesitas guardar desde la app
 */
fun News.toDto(): NewsDto {
    return NewsDto(
        id = id,
        title = title,
        shortDescription = shortDescription,
        content = content,
        imageUrl = imageUrl,
        createdAt = com.google.firebase.Timestamp.now(),
        updatedAt = null,
        isPublic = isPublic,
        additionalPhotos = additionalPhotos
    )
}