package com.asociacionciguena.app.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.excursion.GetExcursionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la pantalla de Calendario
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getExcursionsUseCase: GetExcursionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadExcursions()
    }

    /**
     * Carga las excursiones desde Firebase
     */
    fun loadExcursions() {
        viewModelScope.launch {
            getExcursionsUseCase().collect { result ->
                _uiState.value = when (result) {
                    is Result.Success -> {
                        CalendarUiState.Success(excursions = result.data)
                    }

                    is Result.Error -> {
                        CalendarUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        CalendarUiState.Loading
                    }
                }
            }
        }
    }

    /**
     * Refresca las excursiones
     */
    fun refresh() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is CalendarUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }

            getExcursionsUseCase().collect { result ->
                _uiState.value = when (result) {
                    is Result.Success -> {
                        CalendarUiState.Success(
                            excursions = result.data,
                            isRefreshing = false
                        )
                    }

                    is Result.Error -> {
                        CalendarUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        if (currentState is CalendarUiState.Success) {
                            currentState.copy(isRefreshing = true)
                        } else {
                            CalendarUiState.Loading
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
        loadExcursions()
    }
}