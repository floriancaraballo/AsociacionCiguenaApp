package com.asociacionciguena.app.presentation.screens.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.EmptyState
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.screens.news.components.NewsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                NewsTopBar()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    label = { Text("Buscar publicaciones") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NewsUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
                is NewsUiState.Success -> {
                    if (state.news.isEmpty()) {
                        EmptyState(
                            message = if (searchQuery.isBlank()) {
                                "No hay publicaciones disponibles"
                            } else {
                                "No hay publicaciones que coincidan con tu búsqueda"
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "news_heading") {
                                Text(
                                    text = if (searchQuery.isBlank()) "Lo último" else "Resultados de búsqueda",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            itemsIndexed(state.news, key = { _, news -> news.id }) { index, news ->
                                NewsCard(
                                    news = news,
                                    onClick = { onNavigateToDetail(news.id) },
                                    isFeatured = index == 0 && searchQuery.isBlank()
                                )
                            }
                        }
                    }
                }
                is NewsUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NewsTopBar() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("ASOCIACIÓN CIGÜEÑA", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Publicaciones",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
