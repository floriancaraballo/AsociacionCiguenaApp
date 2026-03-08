package com.asociacionciguena.app.presentation.screens.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.asociacionciguena.app.presentation.screens.news.components.NewsCardSkeleton
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent

/**
 * Pantalla principal de Noticias
 *
 * @param viewModel ViewModel inyectado automáticamente por Hilt
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
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
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                NewsSkeletonContent(modifier = Modifier.padding(paddingValues))
            }

            is NewsUiState.Success -> {
                NewsSuccessContent(
                    news = state.news,
                    isRefreshing = false,
                    onRefresh = { viewModel.refresh() },
                    onNewsClick = onNavigateToDetail,  // ← AÑADIR
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is NewsUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Contenido cuando hay noticias cargadas
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun NewsSuccessContent(
    news: List<com.asociacionciguena.app.domain.model.News>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNewsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (news.isEmpty()) {
            // Sin noticias
            EmptyState(
                message = "No hay publicaciones disponibles",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Lista de noticias
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = news,
                    key = { it.id }
                ) { newsItem ->
                    NewsCard(
                        news = newsItem,
                        onClick = {
                            onNewsClick(newsItem.id)
                        }
                    )
                }
            }
        }

        // Indicador de pull-to-refresh
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
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

/**
 * Contenido skeleton mientras carga
 */
@Composable
private fun NewsSkeletonContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(4) { // Mostrar 4 skeletons
            NewsCardSkeleton()
        }
    }
}