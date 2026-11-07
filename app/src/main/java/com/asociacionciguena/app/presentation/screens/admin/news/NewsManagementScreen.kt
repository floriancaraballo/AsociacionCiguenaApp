package com.asociacionciguena.app.presentation.screens.admin.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de gestión de noticias
 * CON BÚSQUEDA, FILTROS Y SNACKBAR
 */
@Composable
fun NewsManagementScreen(
    viewModel: NewsManagementViewModel = hiltViewModel(),
    onNavigateToForm: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val canDeleteNews by viewModel.canDeleteNews.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Estados para búsqueda y filtros
    var searchQuery by remember { mutableStateOf("") }
    var filterPublic by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Publicaciones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToForm("new") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva publicación") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is NewsManagementUiState.Loading -> {
                LoadingIndicator(modifier = Modifier.fillMaxSize().padding(paddingValues))
            }

            is NewsManagementUiState.Success -> {
                NewsListContent(
                    news = state.news,
                    searchQuery = searchQuery,
                    filterPublic = filterPublic,
                    onSearchChange = { searchQuery = it },
                    onFilterChange = { filterPublic = it },
                    onEditClick = { onNavigateToForm(it.id) },
                    onDeleteClick = { newsId ->
                        viewModel.deleteNews(newsId) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Publicación eliminada correctamente",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onToggleVisibility = { newsId, isPublic ->
                        viewModel.toggleVisibility(newsId, isPublic)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Visibilidad actualizada",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    canDeleteNews = canDeleteNews,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is NewsManagementUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Lista de noticias con búsqueda y filtros
 */
@Composable
private fun NewsListContent(
    news: List<News>,
    searchQuery: String,
    filterPublic: Boolean?,
    onSearchChange: (String) -> Unit,
    onFilterChange: (Boolean?) -> Unit,
    onEditClick: (News) -> Unit,
    onDeleteClick: (String) -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit,
    canDeleteNews: Boolean,
    modifier: Modifier = Modifier
) {
    // Filtrar noticias
    val filteredNews = news.filter { newsItem ->
        val matchesSearch = newsItem.title.contains(searchQuery, ignoreCase = true) ||
                newsItem.content.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterPublic) {
            true -> newsItem.isPublic
            false -> !newsItem.isPublic
            null -> true
        }
        matchesSearch && matchesFilter
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Barra de búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar publicaciones...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        // Chips de filtro
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterPublic == null,
                onClick = { onFilterChange(null) },
                label = { Text("Todas") }
            )
            FilterChip(
                selected = filterPublic == true,
                onClick = { onFilterChange(true) },
                label = { Text("Públicas") }
            )
            FilterChip(
                selected = filterPublic == false,
                onClick = { onFilterChange(false) },
                label = { Text("Privadas") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lista
        if (filteredNews.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (news.isEmpty()) "No hay publicaciones" else "No se encontraron resultados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (news.isEmpty()) {
                        Text(
                            text = "Pulsa Nueva publicación para crear una",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = filteredNews,
                    key = { it.id }
                ) { newsItem ->
                    NewsManagementCard(
                        news = newsItem,
                        onEditClick = { onEditClick(newsItem) },
                        onDeleteClick = { onDeleteClick(newsItem.id) },
                        onToggleVisibility = { onToggleVisibility(newsItem.id, newsItem.isPublic) },
                        canDelete = canDeleteNews
                    )
                }
            }
        }
    }
}

/**
 * Card de noticia con acciones
 */
@Composable
private fun NewsManagementCard(
    news: News,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleVisibility: () -> Unit,
    canDelete: Boolean
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header con título y badge
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = news.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Badge público/o
                AssistChip(
                    onClick = onToggleVisibility,
                    label = {
                        Text(if (news.isPublic) "Pública" else "Privada")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (news.isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (news.isPublic)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fecha
            Text(
                text = formatDate(news.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Contenido (preview)
            Text(
                text = news.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editar")
                }

                if (canDelete) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }
            }
        }
    }

    // Diálogo de confirmación
    if (showDeleteDialog && canDelete) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar publicación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta publicación? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun formatDate(date: kotlinx.datetime.LocalDateTime): String {
    val javaDate = date.toJavaLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "ES"))
    return javaDate.format(formatter)
}
