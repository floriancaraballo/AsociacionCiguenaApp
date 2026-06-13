package com.asociacionciguena.app.data.mapper

import com.asociacionciguena.app.data.dto.ExcursionDto
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.RegistrationClosureReason
import com.asociacionciguena.app.domain.model.RegistrationClosureSource
import com.asociacionciguena.app.util.toLocalDateTime
import kotlinx.datetime.LocalDateTime

fun ExcursionDto.toDomain(): Excursion {
    return Excursion(
        id = id,
        title = title,
        description = description,
        date = date?.toLocalDateTime() ?: LocalDateTime(2025, 1, 1, 0, 0),
        endDate = endDate?.toLocalDateTime(),
        location = location,
        imageUrl = imageUrl,
        authorizationPdfUrl = authorizationPdfUrl,
        price = price,
        maxParticipants = maxParticipants,
        currentParticipants = currentParticipants,
        registrationClosed = registrationClosed,
        registrationClosureReason = RegistrationClosureReason.fromStorage(registrationClosureReason),
        registrationClosureSource = RegistrationClosureSource.fromStorage(registrationClosureSource)
    )
}
