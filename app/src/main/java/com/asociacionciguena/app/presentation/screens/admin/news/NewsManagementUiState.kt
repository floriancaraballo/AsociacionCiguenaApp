package com.asociacionciguena.app.presentation.screens.admin.news

import com.asociacionciguena.app.domain.model.News

/**
 * Estados de la pantalla de gestión de noticias
 */
sealed class NewsManagementUiState {
    object Loading : NewsManagementUiState()

    data class Success(
        val news: List<News>
    ) : NewsManagementUiState()

    data class Error(
        val message: String
    ) : NewsManagementUiState()
}