package com.asociacionciguena.app.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime

/**
 * Modelo de dominio para una foto
 * ACTUALIZADO: Añadido excursionId para vincular fotos a excursiones
 */
data class Photo(
    val id: String = "",
    val excursionId: String = "",  // ← Para vincular con excursión
    val imageUrl: String = "",
    val storagePath: String = "",
    val uploadedBy: String = "",
    val uploadedAt: LocalDateTime = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
    val authorizedUsers: List<String> = emptyList()
)
