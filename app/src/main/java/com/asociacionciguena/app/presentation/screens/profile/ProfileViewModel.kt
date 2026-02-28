package com.asociacionciguena.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.auth.GetCurrentUserUseCase
import com.asociacionciguena.app.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la pantalla de Perfil
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * Cargar información del perfil
     */
    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading

            when (val result = getCurrentUserUseCase()) {
                is Result.Success -> {
                    val user = result.data
                    if (user != null) {
                        _uiState.value = ProfileUiState.LoggedIn(user)
                    } else {
                        _uiState.value = ProfileUiState.NotLoggedIn
                    }
                }

                is Result.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }

                is Result.Loading -> {
                    // Mantener loading
                }
            }
        }
    }

    /**
     * Cerrar sesión
     */
    fun logout() {
        viewModelScope.launch {
            // Marcar como "cerrando sesión"
            val currentState = _uiState.value
            if (currentState is ProfileUiState.LoggedIn) {
                _uiState.value = currentState.copy(isLoggingOut = true)
            }

            // Ejecutar logout
            logoutUseCase()

            // Actualizar estado
            _uiState.value = ProfileUiState.NotLoggedIn
        }
    }

    /**
     * Reintentar después de error
     */
    fun retry() {
        loadProfile()
    }
}