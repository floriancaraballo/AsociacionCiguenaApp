package com.asociacionciguena.app.presentation.screens.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.EmptyState
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.screens.news.components.NewsCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

/**
 * Pantalla principal de Noticias
 *
 * @param viewModel ViewModel inyectado automáticamente por Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // ✅ Observar estado separado del spinner (igual que AdminDashboard)
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // ← AÑADIR ESTE LOG:
    android.util.Log.d("NewsUI", "📱 UI: isRefreshing = $isRefreshing")

    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            NewsTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onClearSearch = viewModel::clearSearch
            )
        }
    ) { paddingValues ->
        // ✅ PullToRefreshBox con patrón IDÉNTICO a AdminDashboard
        PullToRefreshBox(
            isRefreshing = isRefreshing,  // ← Boolean observado directamente
            onRefresh = {android.util.Log.d("NewsUI", "👆 UI: onRefresh llamado")
                viewModel.refresh() },  // ← Callback directo
            state = rememberPullToRefreshState(),  // ← Estado simple
            modifier = Modifier.padding(paddingValues)  // ← Padding correcto
        ) {
            // Contenido: mismo que tenías
            when (val state = uiState) {
                is NewsUiState.Loading -> {
                    LoadingIndicator(modifier = Modifier.fillMaxSize())
                }
                is NewsUiState.Success -> {
                    if (state.news.isEmpty()) {
                        EmptyState(
                            message = "No hay publicaciones disponibles",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.news,
                                key = { it.id }
                            ) { newsItem ->
                                NewsCard(
                                    news = newsItem,
                                    onClick = { onNavigateToDetail(newsItem.id) }
                                )
                            }
                        }
                    }
                }
                is NewsUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Buscar publicaciones...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,  // ← Fondo blanco
                        unfocusedContainerColor = Color.White,  // ← Fondo blanco
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.Black,  // ← Texto negro
                        unfocusedTextColor = Color.Black,  // ← Texto negro
                        cursorColor = MaterialTheme.colorScheme.primary  // ← Cursor con color primario
                    ),
                    shape = MaterialTheme.shapes.medium,  // ← Bordes redondeados
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)  // ← Padding
                )
            } else {
                Text("Publicaciones")
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda"
                        )
                    }
                }
                IconButton(onClick = {
                    isSearchActive = false
                    onClearSearch()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar búsqueda"
                    )
                }
            } else {
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

