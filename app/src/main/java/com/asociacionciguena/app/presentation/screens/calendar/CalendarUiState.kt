package com.asociacionciguena.app.presentation.screens.calendar

import com.asociacionciguena.app.domain.model.Excursion

/**
 * Estados posibles de la pantalla de Calendario
 */
sealed class CalendarUiState {

    /**
     * Estado inicial: Cargando datos
     */
    object Loading : CalendarUiState()

    /**
     * Estado exitoso: Datos cargados
     */
    data class Success(
        val excursions: List<Excursion>,
        val isRefreshing: Boolean = false
    ) : CalendarUiState()

    /**
     * Estado de error
     */
    data class Error(
        val message: String
    ) : CalendarUiState()
}