package com.asociacionciguena.app.presentation.screens.admin.users

import com.asociacionciguena.app.domain.model.User

/**
 * Estados de la pantalla de gestión de usuarios
 */
sealed class UserManagementUiState {
    object Loading : UserManagementUiState()

    data class Success(
        val users: List<User>
    ) : UserManagementUiState()

    data class Error(
        val message: String
    ) : UserManagementUiState()
}
