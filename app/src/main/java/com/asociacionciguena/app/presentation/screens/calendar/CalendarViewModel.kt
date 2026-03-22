package com.asociacionciguena.app.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.excursion.GetAllExcursionsUseCase
import com.asociacionciguena.app.domain.usecase.excursion.GetExcursionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getAllExcursionsUseCase: GetAllExcursionsUseCase  // ← Cambio
) : ViewModel() {
    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // ✅ AÑADIR: Estado separado para el spinner (igual que NewsViewModel)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ← NUEVO: Todas las excursiones sin filtrar
    private val _allExcursions = MutableStateFlow<List<Excursion>>(emptyList())

    // ← NUEVO: Año seleccionado
    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()

    // ← NUEVO: Lista de años disponibles
    private val _availableYears = MutableStateFlow<List<Int>>(emptyList())
    val availableYears: StateFlow<List<Int>> = _availableYears.asStateFlow()

    // ← NUEVO: Query de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadExcursions()
    }

    fun loadExcursions() {
        viewModelScope.launch {
            getAllExcursionsUseCase().collect { result ->
                when (result) {
                    is Result.Success -> {
                        _allExcursions.value = result.data

                        // Extraer años únicos y ordenarlos descendente
                        val years = result.data
                            .map { it.date.year }
                            .distinct()
                            .sortedDescending()

                        _availableYears.value = years

                        // Seleccionar año actual o el más reciente
                        if (_selectedYear.value == null && years.isNotEmpty()) {
                            val currentYear = kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year
                            _selectedYear.value = if (years.contains(currentYear)) {
                                currentYear
                            } else {
                                years.first()
                            }
                        }

                        filterExcursions()
                    }

                    is Result.Error -> {
                        _uiState.value = CalendarUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        _uiState.value = CalendarUiState.Loading
                    }
                }
            }
        }
    }

    // ← NUEVO: Seleccionar año
    fun selectYear(year: Int) {
        _selectedYear.value = year
        filterExcursions()
    }

    // ← NUEVO: Actualizar búsqueda
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterExcursions()
    }

    // ← NUEVO: Limpiar búsqueda
    fun clearSearch() {
        _searchQuery.value = ""
        filterExcursions()
    }

    // ← NUEVO: Filtrar excursiones por año y búsqueda
    private fun filterExcursions() {
        val selectedYear = _selectedYear.value
        val searchQuery = _searchQuery.value
        val allExcursions = _allExcursions.value

        var filtered = allExcursions

        // Filtrar por año
        if (selectedYear != null) {
            filtered = filtered.filter { it.date.year == selectedYear }
        }

        // Filtrar por búsqueda
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { excursion ->
                excursion.title.contains(searchQuery, ignoreCase = true) ||
                        excursion.location.contains(searchQuery, ignoreCase = true) ||
                        excursion.description.contains(searchQuery, ignoreCase = true)
            }
        }

        // Ordenar por fecha descendente (más recientes primero)
        filtered = filtered.sortedByDescending { it.date }

        _uiState.value = CalendarUiState.Success(excursions = filtered)
    }

    fun refresh() {
        viewModelScope.launch {
            // ✅ Activar spinner al inicio
            _isRefreshing.value = true

            val startTime = System.currentTimeMillis()
            val MIN_REFRESH_TIME = 200L

            // ✅ Flag para procesar solo la primera emisión útil
            var finished = false

            getAllExcursionsUseCase().collect { result ->
                if (!finished && result !is Result.Loading) {
                    finished = true

                    _uiState.value = when (result) {
                        is Result.Success -> {
                            _allExcursions.value = result.data

                            // Actualizar años disponibles
                            val years = result.data
                                .map { it.date.year }
                                .distinct()
                                .sortedDescending()
                            _availableYears.value = years

                            // Aplicar filtros actuales
                            filterExcursions()

                            CalendarUiState.Success(excursions = result.data)
                        }
                        is Result.Error -> {
                            CalendarUiState.Error(message = result.message)
                        }
                        is Result.Loading -> _uiState.value
                    }

                    // Antes de ocultar el spinner:
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < MIN_REFRESH_TIME) {
                        kotlinx.coroutines.delay(MIN_REFRESH_TIME - elapsed)
                    }

                    // ✅ Ocultar spinner después de procesar
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun retry() {
        loadExcursions()
    }
}