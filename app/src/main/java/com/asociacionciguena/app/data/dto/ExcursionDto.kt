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
    val location: String = "",
    val maxParticipants: Int = 0,
    val currentParticipants: Int = 0,
    val imageUrl: String? = null
) {
    constructor() : this("", "", "", null, "", 0, 0, null)
}