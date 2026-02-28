package com.asociacionciguena.app.presentation.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.news.GetNewsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la pantalla de Noticias
 *
 * @HiltViewModel = Hilt inyectará las dependencias automáticamente
 * @Inject = Constructor con dependencias inyectadas
 */
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val getNewsUseCase: GetNewsUseCase
) : ViewModel() {

    // Estado privado (mutable) - solo el ViewModel puede modificarlo
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)

    // Estado público (inmutable) - la UI solo puede observarlo
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    /**
     * Init block se ejecuta al crear el ViewModel
     * Carga las noticias automáticamente
     */
    init {
        loadNews()
    }

    /**
     * Carga las noticias desde Firebase
     */
    fun loadNews() {
        viewModelScope.launch {
            // Llamar al Use Case
            getNewsUseCase().collect { result ->
                // Actualizar el estado según el resultado
                _uiState.value = when (result) {
                    is Result.Success -> {
                        NewsUiState.Success(news = result.data)
                    }

                    is Result.Error -> {
                        NewsUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        NewsUiState.Loading
                    }
                }
            }
        }
    }

    /**
     * Refresca las noticias (pull-to-refresh)
     */
    fun refresh() {
        viewModelScope.launch {
            // Marcar como refrescando
            val currentState = _uiState.value
            if (currentState is NewsUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }

            // Cargar noticias nuevamente
            getNewsUseCase().collect { result ->
                _uiState.value = when (result) {
                    is Result.Success -> {
                        NewsUiState.Success(
                            news = result.data,
                            isRefreshing = false
                        )
                    }

                    is Result.Error -> {
                        NewsUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        if (currentState is NewsUiState.Success) {
                            currentState.copy(isRefreshing = true)
                        } else {
                            NewsUiState.Loading
                        }
                    }
                }
            }
        }
    }

    /**
     * Reintentar después de un error
     */
    fun retry() {
        loadNews()
    }
}