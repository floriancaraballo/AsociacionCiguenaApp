package com.asociacionciguena.app.presentation.screens.news.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.presentation.components.ShimmerBox
import com.asociacionciguena.app.presentation.components.ShimmerText

/**
 * Skeleton de NewsCard mientras carga
 */
@Composable
fun NewsCardSkeleton(
    modifier: Modifier = Modifier.Companion
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Imagen skeleton
            ShimmerBox(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Contenido
            Column(
                modifier = Modifier.Companion.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Título (2 líneas)
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth(0.9f)
                        .height(24.dp)
                )
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth(0.7f)
                        .height(24.dp)
                )

                Spacer(modifier = Modifier.Companion.height(4.dp))

                // Fecha
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth(0.3f)
                        .height(16.dp)
                )

                Spacer(modifier = Modifier.Companion.height(4.dp))

                // Descripción (3 líneas)
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .height(16.dp)
                )
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth(0.95f)
                        .height(16.dp)
                )
                ShimmerText(
                    modifier = Modifier.Companion
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                )
            }
        }
    }
}