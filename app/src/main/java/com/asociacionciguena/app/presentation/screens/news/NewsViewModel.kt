package com.asociacionciguena.app.presentation.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.asociacionciguena.app.domain.usecase.news.GetNewsUseCase

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

    // Estado o (mutable) - solo el ViewModel puede modificarlo
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)

    // Estado público (inmutable) - la UI solo puede observarlo
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    // ✅ AÑADIR: Estado separado para el spinner (igual que AdminDashboard)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
                        _allNews.value = result.data  // ← NUEVO: Guardar todas
                        filterNews(_searchQuery.value)  // ← NUEVO: Aplicar filtro actual
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

    fun refresh() {
        viewModelScope.launch {
            android.util.Log.d("NewsVM", "🔴 [1] refresh() INICIADO")

            // ✅ Activar spinner
            _isRefreshing.value = true
            android.util.Log.d("NewsVM", "🟡 [2] _isRefreshing.value = true EJECUTADO")

            var finished = false

            getNewsUseCase().collect { result ->
                android.util.Log.d("NewsVM", "🔵 [3] collect: ${result::class.simpleName}, finished=$finished")

                if (!finished && result !is Result.Loading) {
                    finished = true
                    android.util.Log.d("NewsVM", "🟢 [4] Procesando primera emisión útil")

                    _uiState.value = when (result) {
                        is Result.Success -> {
                            android.util.Log.d("NewsVM", "📦 [5] Success: ${result.data.size} noticias")
                            _allNews.value = result.data
                            filterNews(_searchQuery.value)
                            NewsUiState.Success(news = result.data)
                        }
                        is Result.Error -> {
                            android.util.Log.e("NewsVM", "❌ [5] Error: ${result.message}")
                            NewsUiState.Error(message = result.message)
                        }
                        is Result.Loading -> _uiState.value
                    }

                    // ✅ Ocultar spinner
                    _isRefreshing.value = false
                    android.util.Log.d("NewsVM", "🟣 [6] _isRefreshing.value = false EJECUTADO")
                }
            }
        }
    }

    // ← NUEVO: Estado de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allNews = MutableStateFlow<List<com.asociacionciguena.app.domain.model.News>>(emptyList())

    // ← NUEVO: Actualizar query de búsqueda
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterNews(query)
    }

    // ← NUEVO: Limpiar búsqueda
    fun clearSearch() {
        _searchQuery.value = ""
        filterNews("")
    }

    // ← NUEVO: Filtrar noticias
    private fun filterNews(query: String, isRefreshing: Boolean = false) {
        val currentNews = _allNews.value

        if (query.isBlank()) {
            // Mostrar todas
            _uiState.value = NewsUiState.Success(news = currentNews, isRefreshing = isRefreshing)
        } else {
            // Filtrar por título o descripción
            val filtered = currentNews.filter { news ->
                news.title.contains(query, ignoreCase = true) ||
                        news.shortDescription.contains(query, ignoreCase = true)
            }
            _uiState.value = NewsUiState.Success(news = filtered, isRefreshing = isRefreshing)
        }
    }

    /**
     * Reintentar después de un error
     */
    fun retry() {
        loadNews()
    }
}
