package com.asociacionciguena.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.auth.GetCurrentUserUseCase
import com.asociacionciguena.app.domain.usecase.auth.LoginUseCase
import com.asociacionciguena.app.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de autenticación
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Estados del formulario
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    init {
        checkIfUserIsLoggedIn()
    }

    /**
     * Verifica si hay un usuario logueado
     */
    private fun checkIfUserIsLoggedIn() {
        viewModelScope.launch {
            when (val result = getCurrentUserUseCase()) {
                is Result.Success -> {
                    result.data?.let { user ->
                        _uiState.value = AuthUiState.Success(user)
                    }
                }
                is Result.Error -> {
                    // Usuario no logueado, mantener Idle
                }
                is Result.Loading -> {
                    // No hacer nada
                }
            }
        }
    }

    /**
     * Actualizar email
     */
    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
    }

    /**
     * Actualizar password
     */
    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
    }

    /**
     * Realizar login
     */
    fun login() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            when (val result = loginUseCase(_email.value, _password.value)) {
                is Result.Success -> {
                    _uiState.value = AuthUiState.Success(result.data)
                }

                is Result.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }

                is Result.Loading -> {
                    // No hacer nada
                }
            }
        }
    }

    /**
     * Cerrar sesión
     */
    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.value = AuthUiState.Idle
            _email.value = ""
            _password.value = ""
        }
    }

    /**
     * Limpiar error
     */
    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }
}