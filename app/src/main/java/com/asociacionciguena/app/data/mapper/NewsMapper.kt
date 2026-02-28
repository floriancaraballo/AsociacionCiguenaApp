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
        content = content,
        imageUrl = imageUrl,
        publishedDate = publishedDate?.toLocalDateTime()
            ?: LocalDateTime(2025, 1, 1, 0, 0),
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
        content = content,
        imageUrl = imageUrl,
        publishedDate = com.google.firebase.Timestamp.now(),  // Por ahora
        isPublic = isPublic
    )
}