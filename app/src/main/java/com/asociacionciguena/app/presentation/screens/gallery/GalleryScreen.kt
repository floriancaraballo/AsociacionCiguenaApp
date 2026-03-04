package com.asociacionciguena.app.presentation.screens.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.datetime.LocalDateTime

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onNavigateToExcursionDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galería") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is GalleryUiState.Loading -> {
                LoadingIndicator()
            }

            is GalleryUiState.Success -> {
                GalleryContent(
                    excursions = state.excursions,
                    onExcursionClick = onNavigateToExcursionDetail,
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
        }
    }
}

@Composable
private fun GalleryContent(
    excursions: List<ExcursionWithPhotos>,
    onExcursionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (excursions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No hay excursiones pasadas",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = excursions, key = { it.excursion.id }) { item ->
                ExcursionCard(
                    excursionWithPhotos = item,
                    onClick = { onExcursionClick(item.excursion.id) }
                )
            }
        }
    }
}

@Composable
private fun ExcursionCard(
    excursionWithPhotos: ExcursionWithPhotos,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (excursionWithPhotos.firstPhotoUrl != null) {
                Card(modifier = Modifier.size(80.dp)) {
                    SubcomposeAsyncImage(
                        model = excursionWithPhotos.firstPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = excursionWithPhotos.excursion.title, style = MaterialTheme.typography.titleMedium)
                Text(text = "${excursionWithPhotos.photoCount} fotos", style = MaterialTheme.typography.bodySmall)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
