package com.asociacionciguena.app.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.excursion.GetAllExcursionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getAllExcursionsUseCase: GetAllExcursionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _allExcursions = MutableStateFlow<List<Excursion>>(emptyList())

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear.asStateFlow()

    private val _availableYears = MutableStateFlow<List<Int>>(emptyList())
    val availableYears: StateFlow<List<Int>> = _availableYears.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private companion object {
        const val MIN_REFRESH_TIME_MS = 300L
        const val REFRESH_TIMEOUT_MS = 10_000L
    }

    init {
        loadExcursions()
    }

    fun loadExcursions() {
        viewModelScope.launch {
            getAllExcursionsUseCase().collect { result ->
                when (result) {
                    is Result.Success -> {
                        updateExcursionData(result.data)
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

    fun selectYear(year: Int) {
        _selectedYear.value = year
        filterExcursions()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterExcursions()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        filterExcursions()
    }

    fun refresh() {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            val startTime = System.currentTimeMillis()

            try {
                val result = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    getAllExcursionsUseCase().first { it !is Result.Loading }
                }

                when (result) {
                    is Result.Success -> {
                        updateExcursionData(result.data)
                        filterExcursions()
                    }

                    is Result.Error -> {
                        _uiState.value = CalendarUiState.Error(message = result.message)
                    }

                    is Result.Loading,
                    null -> Unit
                }
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < MIN_REFRESH_TIME_MS) {
                    delay(MIN_REFRESH_TIME_MS - elapsed)
                }
                _isRefreshing.value = false
            }
        }
    }

    fun retry() {
        loadExcursions()
    }

    private fun updateExcursionData(excursions: List<Excursion>) {
        _allExcursions.value = excursions

        val years = excursions
            .map { it.date.year }
            .distinct()
            .sortedDescending()

        _availableYears.value = years

        if (_selectedYear.value == null && years.isNotEmpty()) {
            val currentYear = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).year
            _selectedYear.value = if (years.contains(currentYear)) {
                currentYear
            } else {
                years.first()
            }
        }
    }

    private fun filterExcursions() {
        val selectedYear = _selectedYear.value
        val searchQuery = _searchQuery.value

        val filtered = _allExcursions.value
            .filter { excursion ->
                selectedYear == null || excursion.date.year == selectedYear
            }
            .filter { excursion ->
                searchQuery.isBlank() ||
                    excursion.title.contains(searchQuery, ignoreCase = true) ||
                    excursion.location.contains(searchQuery, ignoreCase = true) ||
                    excursion.description.contains(searchQuery, ignoreCase = true)
            }
            .sortedByDescending { it.date }

        _uiState.value = CalendarUiState.Success(excursions = filtered)
    }
}
