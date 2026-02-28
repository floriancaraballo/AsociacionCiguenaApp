package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime

data class Photo(
    val id: String,
    val excursionId: String,
    val imageUrl: String,
    val storagePath: String,
    val uploadedBy: String,
    val uploadedAt: LocalDateTime,
    val authorizedUsers: List<String>
)