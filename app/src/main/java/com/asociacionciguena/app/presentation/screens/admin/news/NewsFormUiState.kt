package com.asociacionciguena.app.presentation.screens.admin.news

import com.asociacionciguena.app.domain.model.News

/**
 * Estados del formulario de noticia
 */
sealed class NewsFormUiState {
    object Idle : NewsFormUiState()
    object Loading : NewsFormUiState()
    object Saving : NewsFormUiState()

    data class Loaded(
        val news: News
    ) : NewsFormUiState()

    object Saved : NewsFormUiState()  // ← CAMBIADO: object en vez de data class

    data class Error(
        val message: String
    ) : NewsFormUiState()
}