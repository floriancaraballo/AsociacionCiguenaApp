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

    // Estado o (mutable) - solo el ViewModel puede modificarlo
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

    /**
     * Refresca las noticias (pull-to-refresh)
     */
    fun refresh() {
        viewModelScope.launch {
            android.util.Log.d("NewsVM", "🔄 refresh() iniciado")
            // Marcar como refrescando
            val currentState = _uiState.value
            if (currentState is NewsUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }

            // Cargar noticias nuevamente
            getNewsUseCase().collect { result ->
                android.util.Log.d("NewsVM", "📩 Resultado: ${result::class.simpleName}")
                _uiState.value = when (result) {
                    is Result.Success -> {
                        android.util.Log.d("NewsVM", "✅ Success: ${result.data.size} noticias")
                        _allNews.value = result.data  // ← AÑADIR
                        filterNews(_searchQuery.value)  // ← AÑADIR
                        NewsUiState.Success(
                            news = result.data,
                            isRefreshing = false
                        )
                    }

                    is Result.Error -> {
                        android.util.Log.e("NewsVM", "❌ Error: ${result.message}")
                        NewsUiState.Error(message = result.message)
                    }

                    is Result.Loading -> {
                        android.util.Log.d("NewsVM", "⏳ Loading...")
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
    private fun filterNews(query: String) {
        val currentNews = _allNews.value

        if (query.isBlank()) {
            // Mostrar todas
            _uiState.value = NewsUiState.Success(news = currentNews)
        } else {
            // Filtrar por título o descripción
            val filtered = currentNews.filter { news ->
                news.title.contains(query, ignoreCase = true) ||
                        news.shortDescription.contains(query, ignoreCase = true)
            }
            _uiState.value = NewsUiState.Success(news = filtered)
        }
    }

    /**
     * Reintentar después de un error
     */
    fun retry() {
        loadNews()
    }
}