package com.asociacionciguena.app.data.mapper

import com.asociacionciguena.app.data.dto.PhotoDto
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.util.toLocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun PhotoDto.toDomain(): Photo {
    return Photo(
        id = id,
        excursionId = excursionId,
        imageUrl = imageUrl,
        storagePath = storagePath,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt?.toLocalDateTime()
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        authorizedUsers = authorizedUsers
    )
}