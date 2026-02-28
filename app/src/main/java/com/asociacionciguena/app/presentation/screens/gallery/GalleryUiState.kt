package com.asociacionciguena.app.presentation.screens.gallery

import com.asociacionciguena.app.domain.model.Photo

/**
 * Estados posibles de la pantalla de Galería
 */
sealed class GalleryUiState {

    /**
     * Estado inicial: Cargando
     */
    object Loading : GalleryUiState()

    /**
     * Fotos cargadas exitosamente
     */
    data class Success(
        val photosByExcursion: Map<String, List<Photo>>,
        val isRefreshing: Boolean = false
    ) : GalleryUiState()

    /**
     * Error al cargar fotos
     */
    data class Error(
        val message: String
    ) : GalleryUiState()

    /**
     * Usuario no autenticado
     */
    object NotAuthenticated : GalleryUiState()
}