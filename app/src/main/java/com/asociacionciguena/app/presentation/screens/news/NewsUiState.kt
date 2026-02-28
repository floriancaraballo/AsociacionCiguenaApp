package com.asociacionciguena.app.presentation.screens.news

import com.asociacionciguena.app.domain.model.News

/**
 * Estados posibles de la pantalla de Noticias
 *
 * sealed class = solo puede tener estos estados
 * Garantiza que en el when() no olvidemos ningún caso
 */
sealed class NewsUiState {

    /**
     * Estado inicial: Cargando datos
     */
    object Loading : NewsUiState()

    /**
     * Estado exitoso: Datos cargados
     * @param news Lista de noticias
     * @param isRefreshing Si está refrescando (pull-to-refresh)
     */
    data class Success(
        val news: List<News>,
        val isRefreshing: Boolean = false
    ) : NewsUiState()

    /**
     * Estado de error: Algo salió mal
     * @param message Mensaje de error para mostrar al usuario
     */
    data class Error(
        val message: String
    ) : NewsUiState()
}