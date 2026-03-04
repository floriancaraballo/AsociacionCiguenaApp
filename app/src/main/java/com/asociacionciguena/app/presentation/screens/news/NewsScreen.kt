package com.asociacionciguena.app.presentation.screens.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publicaciones") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                LoadingIndicator()
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