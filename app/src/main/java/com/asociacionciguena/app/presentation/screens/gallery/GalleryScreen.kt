package com.asociacionciguena.app.presentation.screens.gallery

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.presentation.components.EmptyState
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.screens.gallery.components.ExcursionPhotoSection

/**
 * Pantalla de Galería de Fotos
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galería de Fotos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is GalleryUiState.Loading -> {
                LoadingIndicator()
            }

            is GalleryUiState.Success -> {
                GallerySuccessContent(
                    photosByExcursion = state.photosByExcursion,
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is GalleryUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is GalleryUiState.NotAuthenticated -> {
                NotAuthenticatedMessage(
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Contenido cuando hay fotos cargadas
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun GallerySuccessContent(
    photosByExcursion: Map<String, List<Photo>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (photosByExcursion.isEmpty()) {
            EmptyState(
                message = "No tienes fotos autorizadas\n\nContacta con un administrador para obtener acceso a las fotos",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = photosByExcursion.entries.toList(),
                    key = { it.key }
                ) { (excursionId, photos) ->
                    ExcursionPhotoSection(
                        excursionId = excursionId,
                        photos = photos,
                        onPhotoClick = { photo ->
                            // TODO: Abrir foto en pantalla completa
                        }
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Mensaje cuando el usuario no está autenticado
 */
@Composable
private fun NotAuthenticatedMessage(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "🔒",
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "Debes iniciar sesión",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Para acceder a la galería de fotos necesitas estar autenticado",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onNavigateToLogin) {
                Text("Iniciar Sesión")
            }
        }
    }
}