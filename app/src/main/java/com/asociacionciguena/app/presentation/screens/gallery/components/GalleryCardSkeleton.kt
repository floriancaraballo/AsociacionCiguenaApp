package com.asociacionciguena.app.presentation.screens.gallery.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Imagen thumbnail (cuadrado)
            ShimmerBox(
                modifier = Modifier.size(80.dp)
            )

            // Contenido
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                Spacer(modifier = Modifier.weight(1f))

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