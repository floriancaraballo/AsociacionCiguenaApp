package com.asociacionciguena.app.presentation.screens.admin.excursions

import com.asociacionciguena.app.domain.model.Excursion

/**
 * Estados del formulario de excursión
 */
sealed class ExcursionFormUiState {
    object Idle : ExcursionFormUiState()
    object Loading : ExcursionFormUiState()
    object Saving : ExcursionFormUiState()

    data class Loaded(
        val excursion: Excursion
    ) : ExcursionFormUiState()

    object Saved : ExcursionFormUiState()

    data class Error(
        val message: String
    ) : ExcursionFormUiState()
}