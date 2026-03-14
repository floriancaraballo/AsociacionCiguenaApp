package com.asociacionciguena.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SignatureCanvas(
    paths: List<Path> = emptyList(),
    onPathsChange: (List<Path>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf(Path()) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }  // ← NUEVO: forzar redibujado

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(paths) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply {
                                moveTo(offset.x, offset.y)
                            }
                            currentOffset = offset  // ← Actualizar para forzar redibujado
                        },
                        onDrag = { change, _ ->
                            currentPath.lineTo(
                                change.position.x,
                                change.position.y
                            )
                            currentOffset = change.position  // ← Forzar redibujado
                        },
                        onDragEnd = {
                            onPathsChange(paths + currentPath)
                            currentPath = Path()
                            currentOffset = null
                        }
                    )
                }
        ) {
            // Dibujar paths guardados
            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Dibujar path actual (en tiempo real)
            if (currentOffset != null) {  // ← Condición que activa redibujado
                drawPath(
                    path = currentPath,
                    color = Color.Black,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}