package com.asociacionciguena.app.presentation.screens.admin.excursions

import com.asociacionciguena.app.domain.model.Excursion

/**
 * Estados de la pantalla de gestión de excursiones
 */
sealed class ExcursionManagementUiState {
    object Loading : ExcursionManagementUiState()

    data class Success(
        val excursions: List<Excursion>
    ) : ExcursionManagementUiState()

    data class Error(
        val message: String
    ) : ExcursionManagementUiState()
}