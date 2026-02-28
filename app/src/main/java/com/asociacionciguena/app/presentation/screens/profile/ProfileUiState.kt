package com.asociacionciguena.app.presentation.screens.profile

import com.asociacionciguena.app.domain.model.User

/**
 * Estados posibles de la pantalla de Perfil
 */
sealed class ProfileUiState {

    /**
     * Cargando información del usuario
     */
    object Loading : ProfileUiState()

    /**
     * Usuario logueado con información
     */
    data class LoggedIn(
        val user: User,
        val isLoggingOut: Boolean = false
    ) : ProfileUiState()

    /**
     * Usuario no logueado
     */
    object NotLoggedIn : ProfileUiState()

    /**
     * Error al cargar perfil
     */
    data class Error(
        val message: String
    ) : ProfileUiState()
}