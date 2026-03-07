package com.asociacionciguena.app.presentation.screens.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.presentation.components.ShimmerText

/**
 * Skeleton de ExcursionCard mientras carga
 */
@Composable
fun ExcursionCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Título
            ShimmerText(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Fecha
            ShimmerText(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
            )

            // Ubicación
            ShimmerText(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Descripción (2 líneas)
            ShimmerText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )
            ShimmerText(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
            )
        }
    }
}