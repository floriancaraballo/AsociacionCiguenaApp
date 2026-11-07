package com.asociacionciguena.app.presentation.screens.gallery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.presentation.components.ShimmerBox
import com.asociacionciguena.app.presentation.components.ShimmerText

/**
 * Skeleton de ExcursionCard (galería) mientras carga
 */
@Composable
fun GalleryCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(160.dp))
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título
                ShimmerText(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(20.dp)
                )

                // Fecha
                ShimmerText(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(16.dp)
                )

                // Contador de fotos
                ShimmerText(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(16.dp)
                )
            }
        }
    }
}
