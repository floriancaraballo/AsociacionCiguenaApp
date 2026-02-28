package com.asociacionciguena.app.data.mapper

import com.asociacionciguena.app.data.dto.UserDto
import com.asociacionciguena.app.domain.model.User
import com.asociacionciguena.app.util.toLocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun UserDto.toDomain(): User {
    return User(
        id = id,
        email = email,
        displayName = displayName,
        role = role,
        createdAt = createdAt?.toLocalDateTime()
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        photoConsents = photoConsents
    )
}