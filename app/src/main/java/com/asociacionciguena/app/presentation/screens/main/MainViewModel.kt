package com.asociacionciguena.app.presentation.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.asociacionciguena.app.util.NetworkMonitor

sealed class AppInitState {
    object Loading : AppInitState()
    data class Ready(
        val isOnboardingCompleted: Boolean,
        val isUserLoggedIn: Boolean
    ) : AppInitState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val auth: FirebaseAuth,
    val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _initState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val initState: StateFlow<AppInitState> = _initState.asStateFlow()

    init {
        checkInitialState()
        observeAuthChanges()  // ← NUEVO
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            val isOnboardingCompleted = preferencesDataSource.isOnboardingCompleted()
                .first()

            val isUserLoggedIn = auth.currentUser != null

            _initState.value = AppInitState.Ready(
                isOnboardingCompleted = isOnboardingCompleted,
                isUserLoggedIn = isUserLoggedIn
            )
        }
    }

    // ← NUEVO: Observar cambios de autenticación
    private fun observeAuthChanges() {
        auth.addAuthStateListener { firebaseAuth ->
            val currentState = _initState.value
            if (currentState is AppInitState.Ready) {
                val isUserLoggedIn = firebaseAuth.currentUser != null

                // Solo actualizar si cambió el estado de login
                if (currentState.isUserLoggedIn != isUserLoggedIn) {
                    _initState.value = currentState.copy(
                        isUserLoggedIn = isUserLoggedIn
                    )
                }
            }
        }
    }
}