package com.asociacionciguena.app.util

import com.google.firebase.Timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Extensiones útiles para conversión de tipos
 */

// Timestamp de Firebase a LocalDateTime
fun Timestamp.toLocalDateTime(): LocalDateTime {
    return Instant.fromEpochSeconds(seconds, nanoseconds)
        .toLocalDateTime(TimeZone.currentSystemDefault())
}

// LocalDateTime a Timestamp de Firebase
fun LocalDateTime.toTimestamp(): Timestamp {
    val instant = Instant.parse(this.toString())
    return Timestamp(instant.epochSeconds, instant.nanosecondsOfSecond)
}

