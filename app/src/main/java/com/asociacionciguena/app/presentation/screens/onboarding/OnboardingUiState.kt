package com.asociacionciguena.app.presentation.screens.onboarding

sealed class OnboardingUiState {
    object Initial : OnboardingUiState()
    object NotificationsRequesting : OnboardingUiState()
    data class Completed(val notificationsEnabled: Boolean) : OnboardingUiState()
}