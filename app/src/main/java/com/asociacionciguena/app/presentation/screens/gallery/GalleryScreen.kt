package com.asociacionciguena.app.presentation.screens.gallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.ErrorMessage
import kotlinx.datetime.LocalDateTime
import androidx.compose.material.icons.filled.CalendarToday
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
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
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("ASOCIACIÓN CIGÜEÑA", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Galería",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
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
            excursions.groupBy { it.excursion.date.year }.forEach { (year, albums) ->
                item(key = "year_$year") {
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(items = albums, key = { it.excursion.id }) { item ->
                    ExcursionCard(
                        excursionWithPhotos = item,
                        onClick = { onExcursionClick(item.excursion.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcursionCard(
    excursionWithPhotos: ExcursionWithPhotos,
    onClick: () -> Unit
) {
    val coverUrl = excursionWithPhotos.firstPhotoUrl?.takeIf { it.isNotBlank() }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (coverUrl != null) {
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                GalleryMediaPreview(
                    mediaUrl = coverUrl,
                    thumbnailUrl = excursionWithPhotos.firstPhotoThumbnailUrl,
                    mediaType = excursionWithPhotos.firstPhotoMediaType,
                    modifier = Modifier.fillMaxSize()
                )
                if (excursionWithPhotos.firstPhotoMediaType == "video") {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Vídeo", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = excursionWithPhotos.excursion.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = formatDate(excursionWithPhotos.excursion.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = when (val count = excursionWithPhotos.photoCount) {
                            0 -> "Sin fotos ni vídeos"
                            1 -> "1 archivo"
                            else -> "$count archivos"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Row(
                modifier = Modifier.heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ver álbum",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
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
        val generatedThumbnail by produceState<Bitmap?>(initialValue = null, mediaUrl, thumbnailUrl) {
            if (!thumbnailUrl.isNullOrBlank()) {
                value = null
                return@produceState
            }

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
            !thumbnailUrl.isNullOrBlank() -> {
                CachedSubcomposeAsyncImage(
                    imageUrl = thumbnailUrl,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
            thumbnail != null -> {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
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
