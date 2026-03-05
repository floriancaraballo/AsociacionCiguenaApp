package com.asociacionciguena.app.presentation.screens.auth

import com.asociacionciguena.app.domain.model.User

/**
 * Estados posibles de la pantalla de autenticación
 */
sealed class AuthUiState {

    /**
     * Estado inicial
     */
    object Idle : AuthUiState()

    /**
     * Cargando (procesando login)
     */
    object Loading : AuthUiState()

    /**
     * Login exitoso
     */
    data class Success(val user: User) : AuthUiState()

    /**
     * Error en login
     */
    data class Error(val message: String) : AuthUiState()
}

sealed class ResetPasswordState {
    object Idle : ResetPasswordState()
    object Loading : ResetPasswordState()
    object Success : ResetPasswordState()
    data class Error(val message: String) : ResetPasswordState()
}