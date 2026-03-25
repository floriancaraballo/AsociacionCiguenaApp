package com.asociacionciguena.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.manager.FCMTokenManager
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
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.messaging.FirebaseMessaging

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val fcmTokenManager: FCMTokenManager,
    private val auth: FirebaseAuth,  // ← AÑADIDO
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    init {
        checkIfUserIsLoggedIn()
    }

    private fun checkIfUserIsLoggedIn() {
        viewModelScope.launch {
            when (val result = getCurrentUserUseCase()) {
                is Result.Success -> {
                    result.data?.let { user ->
                        // NUEVO: Obtener token FCM si ya está logueado
                        refreshFCMToken()
                        // ✅ Si el usuario ya estaba logueado al iniciar la app, suscribir a topic privado
                        subscribeToAuthenticatedTopic()

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

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
    }

    fun login() {
        viewModelScope.launch {
            // Verificar conexión primero
            if (!networkMonitor.isCurrentlyOnline()) {
                _uiState.value = AuthUiState.Error("Sin conexión a internet. Por favor, verifica tu conexión.")
                return@launch
            }
            _uiState.value = AuthUiState.Loading

            when (val result = loginUseCase(_email.value, _password.value)) {
                is Result.Success -> {
                    // NUEVO: Obtener token FCM después del login exitoso
                    refreshFCMToken()

                    // ✅ NUEVO: Suscribirse a topic "authenticated" para notificaciones privadas (fotos)
                    subscribeToAuthenticatedTopic()

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

    fun logout() {
        viewModelScope.launch {
            // NUEVO: Eliminar token FCM antes de logout
            try {
                fcmTokenManager.deleteToken()
            } catch (e: Exception) {
                // Log error pero continuar con logout
            }

            // ✅ NUEVO: Desuscribir de topic "authenticated" al cerrar sesión
            unsubscribeFromAuthenticatedTopic()

            logoutUseCase()
            _uiState.value = AuthUiState.Idle
            _email.value = ""
            _password.value = ""
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    /**
     * NUEVO: Obtener y guardar token FCM
     */
    private fun refreshFCMToken() {
        viewModelScope.launch {
            try {
                fcmTokenManager.refreshToken()
            } catch (e: Exception) {
                // Log error pero no bloquear el login
            }
        }
    }
    /**
     * Estado del password reset
     */
    private val _resetPasswordState = MutableStateFlow<ResetPasswordState>(ResetPasswordState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordState> = _resetPasswordState.asStateFlow()

    /**
     * Enviar email de recuperación de contraseña
     */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _resetPasswordState.value = ResetPasswordState.Loading

            if (email.isBlank()) {
                _resetPasswordState.value = ResetPasswordState.Error("Introduce tu email")
                return@launch
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _resetPasswordState.value = ResetPasswordState.Error("Email inválido")
                return@launch
            }

            try {
                auth.sendPasswordResetEmail(email).await()
                _resetPasswordState.value = ResetPasswordState.Success
            } catch (e: Exception) {
                _resetPasswordState.value = ResetPasswordState.Error(
                    when {
                        e.message?.contains("user-not-found") == true -> "No existe una cuenta con este email"
                        e.message?.contains("invalid-email") == true -> "Email inválido"
                        else -> "Error al enviar email: ${e.message}"
                    }
                )
            }
        }
    }

    /**
     * Suscribirse a topic "authenticated" para recibir notificaciones privadas (fotos)
     */
    private fun subscribeToAuthenticatedTopic() {
        FirebaseMessaging.getInstance()
            .subscribeToTopic("authenticated")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM_TOPIC", "✅ Suscrito a topic 'authenticated' tras login")
                } else {
                    android.util.Log.e("FCM_TOPIC", "❌ Error al suscribir a 'authenticated': ${task.exception?.message}")
                }
            }
    }
    /**
     * Desuscribirse de topic "authenticated" al cerrar sesión
     */
    private fun unsubscribeFromAuthenticatedTopic() {
        FirebaseMessaging.getInstance()
            .unsubscribeFromTopic("authenticated")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM_TOPIC", "✅ Desuscrito de topic 'authenticated' tras logout")
                } else {
                    android.util.Log.e("FCM_TOPIC", "❌ Error al desuscribir de 'authenticated': ${task.exception?.message}")
                }
            }
    }
    /**
     * Limpiar estado de password reset
     */
    fun clearResetPasswordState() {
        _resetPasswordState.value = ResetPasswordState.Idle
    }
}