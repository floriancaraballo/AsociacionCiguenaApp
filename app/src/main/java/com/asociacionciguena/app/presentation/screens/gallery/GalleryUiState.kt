package com.asociacionciguena.app.presentation.screens.gallery

import com.asociacionciguena.app.domain.model.Excursion

/**
 * Estados de UI para la pantalla de Galería
 */
sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val excursions: List<ExcursionWithPhotos>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

/**
 * Modelo que combina excursión con sus fotos para la galería
 */
data class ExcursionWithPhotos(
    val excursion: Excursion,
    val photoCount: Int,
    val firstPhotoUrl: String?
)
