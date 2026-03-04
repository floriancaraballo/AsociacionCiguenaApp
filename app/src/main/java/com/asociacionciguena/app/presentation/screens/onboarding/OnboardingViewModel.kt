package com.asociacionciguena.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.data.manager.FCMTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val fcmTokenManager: FCMTokenManager,
    private val preferencesDataSource: PreferencesDataSource  // ← AÑADIDO
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Initial)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun enableNotifications() {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.NotificationsRequesting

            try {
                fcmTokenManager.refreshToken()

                // Guardar que el onboarding fue completado
                preferencesDataSource.setOnboardingCompleted(true)

                _uiState.value = OnboardingUiState.Completed(notificationsEnabled = true)
            } catch (e: Exception) {
                // Guardar onboarding completado aunque falle el token
                preferencesDataSource.setOnboardingCompleted(true)

                _uiState.value = OnboardingUiState.Completed(notificationsEnabled = false)
            }
        }
    }

    fun skipNotifications() {
        viewModelScope.launch {
            // Guardar que el onboarding fue completado
            preferencesDataSource.setOnboardingCompleted(true)

            _uiState.value = OnboardingUiState.Completed(notificationsEnabled = false)
        }
    }
}