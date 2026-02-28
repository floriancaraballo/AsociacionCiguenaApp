package com.asociacionciguena.app.presentation.screens.gallery.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.asociacionciguena.app.domain.model.Photo
import kotlin.math.ceil

/**
 * Sección de fotos de una excursión
 */
@Composable
fun ExcursionPhotoSection(
    excursionId: String,
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header de la sección
        Text(
            text = "Excursión",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "${photos.size} ${if (photos.size == 1) "foto" else "fotos"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grid de fotos - altura dinámica corregida
        val rows = ceil(photos.size / 3.0).toInt()
        val gridHeight = (rows * 124).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(gridHeight)
        ) {
            items(
                items = photos,
                key = { it.id }
            ) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    onClick = { onPhotoClick(photo) }
                )
            }
        }
    }
}

/**
 * Miniatura de foto
 */
@Composable
private fun PhotoThumbnail(
    photo: Photo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = "Foto de excursión",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
