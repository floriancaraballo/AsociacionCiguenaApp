package com.asociacionciguena.app.presentation.screens.admin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel// ✅ Añadir estos:
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = hiltViewModel(),
    onNavigateToNews: () -> Unit,
    onNavigateToExcursions: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToPins: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val isLoadingStats by viewModel.isLoadingStats.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()
    val isMonitor = currentRole == "monitor"
    val isAdmin = currentRole == "admin" || currentRole == "superadmin"

    @OptIn(ExperimentalMaterial3Api::class)  // ← AÑADIR ESTA ANOTACIÓN
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isMonitor) "Panel de Monitor" else "Panel de Administración")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadStats() }) {
                        Icon(Icons.Default.Refresh, "Actualizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        // ✅ PullToRefreshBox nativo de Material3
        PullToRefreshBox(
            isRefreshing = isLoadingStats,
            onRefresh = { viewModel.loadStats() },
            state = rememberPullToRefreshState(),
            modifier = Modifier.padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card de estadísticas generales
                StatsOverviewCard(
                    stats = stats,
                    isLoading = isLoadingStats,
                    showUserStats = isAdmin
                )

                Text(
                    text = "Gestión",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                AdminOptionCard(
                    title = "Publicaciones",
                    description = "Crear y editar publicaciones",
                    icon = Icons.AutoMirrored.Filled.Article,
                    onClick = onNavigateToNews,
                    enabled = isAdmin || isMonitor
                )
                AdminOptionCard(
                    title = "Excursiones",
                    description = "Gestionar actividades e inscripciones",
                    icon = Icons.Default.CalendarMonth,
                    onClick = onNavigateToExcursions,
                    enabled = isAdmin
                )
                AdminOptionCard(
                    title = "Fotos y vídeos",
                    description = "Añadir archivos a la galería",
                    icon = Icons.Default.PhotoLibrary,
                    onClick = onNavigateToPhotos,
                    enabled = isAdmin || isMonitor
                )
                AdminOptionCard(
                    title = "Usuarios",
                    description = "Gestionar los usuarios de la asociación",
                    icon = Icons.Default.People,
                    onClick = onNavigateToUsers,
                    enabled = isAdmin
                )
                AdminOptionCard(
                    title = "Pines",
                    description = "Gestionar el contador de pines",
                    icon = Icons.Default.Star,
                    onClick = onNavigateToPins,
                    enabled = isAdmin || isMonitor
                )
            }
        }
    }
}

@Composable
private fun StatsOverviewCard(
    stats: DashboardStats,
    isLoading: Boolean,
    showUserStats: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resumen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            HorizontalDivider()

            StatRow(
                icon = Icons.AutoMirrored.Filled.Article,
                label = "Publicaciones",
                value = "${stats.totalNews}",
                detail = "${stats.publicNews} públicas • ${stats.privateNews} privadas"
            )

            StatRow(
                icon = Icons.Default.CalendarMonth,
                label = "Excursiones",
                value = "${stats.totalExcursions}"
            )

            StatRow(
                icon = Icons.Default.Photo,
                label = "Fotos",
                value = "${stats.totalPhotos}"
            )

            if (showUserStats) {
                StatRow(
                    icon = Icons.Default.People,
                    label = "Usuarios",
                    value = "${stats.totalUsers}",
                    detail = "${stats.adminUsers} administradores"
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AdminOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
