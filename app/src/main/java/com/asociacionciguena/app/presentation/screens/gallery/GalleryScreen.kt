package com.asociacionciguena.app.presentation.screens.gallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.imageLoader
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.datetime.LocalDateTime
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.CalendarToday
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.preloadedImageRequest
import com.asociacionciguena.app.presentation.screens.gallery.components.GalleryCardSkeleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onNavigateToExcursionDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            val accentColor = MaterialTheme.colorScheme.tertiary
            TopAppBar(
                modifier = Modifier.drawBehind {
                    val strokeWidth = 2.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(accentColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
                },
                title = {
                    Text(
                        text = "Galería",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is GalleryUiState.Loading -> {
                GallerySkeletonContent(modifier = Modifier.padding(paddingValues))
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
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(excursions) {
        excursions.forEach { excursion ->
            listOfNotNull(
                excursion.firstPhotoThumbnailUrl,
                excursion.firstPhotoUrl,
                excursion.excursion.imageUrl
            ).distinct().forEach { url ->
                context.imageLoader.enqueue(
                    preloadedImageRequest(context, url)
                )
            }
        }
    }

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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagen de la excursión o placeholder
            Card(
                modifier = Modifier.size(80.dp),
                colors = if (excursionWithPhotos.firstPhotoUrl == null) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                if (excursionWithPhotos.firstPhotoUrl != null) {
                    GalleryMediaPreview(
                        mediaUrl = excursionWithPhotos.firstPhotoUrl,
                        thumbnailUrl = excursionWithPhotos.firstPhotoThumbnailUrl,
                        mediaType = excursionWithPhotos.firstPhotoMediaType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder cuando no hay fotos
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Info de la excursión
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = excursionWithPhotos.excursion.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (excursionWithPhotos.photoCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (excursionWithPhotos.photoCount == 0) {
                            "Sin fotos"
                        } else {
                            "${excursionWithPhotos.photoCount} foto${if (excursionWithPhotos.photoCount != 1) "s" else ""}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (excursionWithPhotos.photoCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Fecha
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(excursionWithPhotos.excursion.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Icono de flecha
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
private fun GalleryMediaPreview(
    mediaUrl: String,
    thumbnailUrl: String?,
    mediaType: String,
    modifier: Modifier = Modifier
) {
    if (mediaType == "video") {
        val generatedThumbnail by produceState<Bitmap?>(initialValue = null, mediaUrl) {
            val thumbnail = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(mediaUrl, emptyMap())
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
            value = thumbnail
        }

        val thumbnail = generatedThumbnail
        when {
            thumbnail != null -> {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
            !thumbnailUrl.isNullOrBlank() -> {
                CachedSubcomposeAsyncImage(
                    imageUrl = thumbnailUrl,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                CachedSubcomposeAsyncImage(
                    imageUrl = mediaUrl,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
        }
    } else {
        CachedSubcomposeAsyncImage(
            imageUrl = mediaUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Contenido skeleton mientras carga
 */
@Composable
private fun GallerySkeletonContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(8) { // Mostrar 8 skeletons (más porque esperamos más contenido)
            GalleryCardSkeleton()
        }
    }
}

private fun formatDate(date: LocalDateTime): String {
    val monthNames = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )
    return "${date.dayOfMonth} ${monthNames[date.monthNumber - 1]} ${date.year}"
}
