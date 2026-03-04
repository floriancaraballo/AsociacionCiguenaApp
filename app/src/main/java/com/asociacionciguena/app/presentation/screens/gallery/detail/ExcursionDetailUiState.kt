package com.asociacionciguena.app.presentation.screens.gallery.detail

import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Photo

sealed class ExcursionDetailUiState {
    object Loading : ExcursionDetailUiState()
    data class Success(
        val excursion: Excursion,
        val photos: List<Photo>,
        val isAdmin: Boolean
    ) : ExcursionDetailUiState()
    data class Error(val message: String) : ExcursionDetailUiState()
}