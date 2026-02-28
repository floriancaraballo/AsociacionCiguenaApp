package com.asociacionciguena.app.presentation.screens.news

import com.asociacionciguena.app.domain.model.News

/**
 * Estados de la pantalla de detalle de noticia
 */
sealed class NewsDetailUiState {
    object Loading : NewsDetailUiState()

    data class Success(
        val news: News
    ) : NewsDetailUiState()

    data class Error(
        val message: String
    ) : NewsDetailUiState()
}
