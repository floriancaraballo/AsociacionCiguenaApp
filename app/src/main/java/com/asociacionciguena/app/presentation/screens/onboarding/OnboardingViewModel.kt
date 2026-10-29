package com.asociacionciguena.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.data.manager.FCMTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

            completeOnboarding(notificationsEnabled = true)
        }
    }

    fun skipNotifications() {
        viewModelScope.launch {
            completeOnboarding(notificationsEnabled = false)
        }
    }

    private suspend fun completeOnboarding(notificationsEnabled: Boolean) {
        try {
            preferencesDataSource.setOnboardingCompleted(true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e(
                "OnboardingViewModel",
                "No se pudo guardar el onboarding: ${error.message}"
            )
        }

        if (notificationsEnabled) {
            fcmTokenManager.refreshTokenInBackground()
        }

        _uiState.value = OnboardingUiState.Completed(
            notificationsEnabled = notificationsEnabled
        )
    }
}
