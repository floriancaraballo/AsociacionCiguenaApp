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
    val currentParticipants: Int = 0
)
