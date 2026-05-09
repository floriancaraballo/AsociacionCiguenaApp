package com.asociacionciguena.app.presentation.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.news.GetNewsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ViewModel de la pantalla de Noticias.
 */
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val getNewsUseCase: GetNewsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allNews = MutableStateFlow<List<News>>(emptyList())

    private companion object {
        const val MIN_REFRESH_TIME_MS = 300L
        const val REFRESH_TIMEOUT_MS = 10_000L
    }

    init {
        loadNews()
    }

    fun loadNews() {
        viewModelScope.launch {
            getNewsUseCase().collect { result ->
                when (result) {
                    is Result.Success -> {
                        _allNews.value = result.data
                        filterNews(_searchQuery.value)
                    }

                    is Result.Error -> {
                        _uiState.value = NewsUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        _uiState.value = NewsUiState.Loading
                    }
                }
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            val startTime = System.currentTimeMillis()

            try {
                val result = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    getNewsUseCase().first { it !is Result.Loading }
                }

                when (result) {
                    is Result.Success -> {
                        _allNews.value = result.data
                        filterNews(_searchQuery.value)
                    }

                    is Result.Error -> {
                        _uiState.value = NewsUiState.Error(message = result.message)
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterNews(query)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        filterNews("")
    }

    private fun filterNews(query: String, isRefreshing: Boolean = false) {
        val currentNews = _allNews.value

        val filtered = if (query.isBlank()) {
            currentNews
        } else {
            currentNews.filter { news ->
                news.title.contains(query, ignoreCase = true) ||
                    news.shortDescription.contains(query, ignoreCase = true)
            }
        }

        _uiState.value = NewsUiState.Success(news = filtered, isRefreshing = isRefreshing)
    }

    fun retry() {
        loadNews()
    }
}
