package com.asociacionciguena.app.presentation.screens.admin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.AdminOnly

/**
 * Panel principal de administración
 * ACTUALIZADO - Con botón de volver
 */
@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = hiltViewModel(),
    onNavigateToNews: () -> Unit,
    onNavigateToExcursions: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val isAdmin by viewModel.isAdmin.collectAsState()
    val userName by viewModel.userName.collectAsState()

    AdminOnly(
        isAdmin = isAdmin,
        onNavigateBack = onNavigateBack
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Panel de Administración") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Bienvenida
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Bienvenido, $userName",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Administrador",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Grid de opciones
                val adminOptions = listOf(
                    AdminOption(
                        icon = Icons.Default.Article,
                        title = "Gestionar Noticias",
                        description = "Crear, editar y eliminar noticias",
                        onClick = onNavigateToNews
                    ),
                    AdminOption(
                        icon = Icons.Default.CalendarMonth,
                        title = "Gestionar Excursiones",
                        description = "Crear, editar y eliminar excursiones",
                        onClick = onNavigateToExcursions
                    ),
                    AdminOption(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Subir Fotos",
                        description = "Subir y gestionar fotos de excursiones",
                        onClick = onNavigateToPhotos
                    ),
                    AdminOption(
                        icon = Icons.Default.People,
                        title = "Gestionar Usuarios",
                        description = "Ver y modificar permisos",
                        onClick = { /* TODO */ }
                    )
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(adminOptions) { option ->
                        AdminOptionCard(option = option)
                    }
                }
            }
        }
    }
}

/**
 * Card de opción del panel
 */
@Composable
private fun AdminOptionCard(
    option: AdminOption
) {
    Card(
        onClick = option.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Data class para opciones del admin
 */
private data class AdminOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)
