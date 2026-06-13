package com.asociacionciguena.app.data.dto

import com.google.firebase.Timestamp

/**
 * DTO para Excursiones desde Firestore
 */
data class ExcursionDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Timestamp? = null,
    val endDate: Timestamp? = null,
    val location: String = "",
    val maxParticipants: Int = 0,
    val currentParticipants: Int = 0,
    val registrationClosed: Boolean = false,
    val registrationClosureReason: String? = null,
    val registrationClosureSource: String? = null,
    val imageUrl: String? = null,
    val authorizationPdfUrl: String? = null,
    val price: Double? = null
) {
    constructor() : this(
        "", "", "", null, null, "", 0, 0,
        false, null, null, null, null, null
    )
}
