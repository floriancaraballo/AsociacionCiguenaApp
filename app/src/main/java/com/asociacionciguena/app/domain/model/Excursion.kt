package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class Excursion(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDateTime,
    val endDate: LocalDateTime? = null,
    val location: String,
    val imageUrl: String?,
    val authorizationPdfUrl: String? = null,
    val price: Double? = null,
    val maxParticipants: Int = 0,
    val currentParticipants: Int = 0,
    val registrationClosed: Boolean = false,
    val registrationClosureReason: RegistrationClosureReason? = null,
    val registrationClosureSource: RegistrationClosureSource? = null
)

enum class RegistrationClosureReason {
    CAPACITY_FULL,
    DEADLINE_PASSED;

    companion object {
        fun fromStorage(value: String?): RegistrationClosureReason? =
            entries.firstOrNull { it.name == value }
    }
}

enum class RegistrationClosureSource {
    MANUAL,
    AUTOMATIC;

    companion object {
        fun fromStorage(value: String?): RegistrationClosureSource? =
            entries.firstOrNull { it.name == value }
    }
}
