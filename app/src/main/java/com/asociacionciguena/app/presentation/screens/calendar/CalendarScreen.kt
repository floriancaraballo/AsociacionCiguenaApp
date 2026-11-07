package com.asociacionciguena.app.presentation.screens.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.EmptyState
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.AppSearchTopBarContent
import com.asociacionciguena.app.presentation.screens.calendar.components.ExcursionCard
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import com.asociacionciguena.app.presentation.screens.calendar.components.ExcursionCardSkeleton
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onNavigateToExcursionDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            CalendarTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onClearSearch = viewModel::clearSearch
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Selector de año
            if (availableYears.isNotEmpty()) {
                YearSelector(
                    selectedYear = selectedYear,
                    availableYears = availableYears,
                    onYearSelected = viewModel::selectYear
                )
            }

            // Contenido
            when (val state = uiState) {
                is CalendarUiState.Loading -> {
                    CalendarSkeletonContent(modifier = Modifier.weight(1f))
                }

                is CalendarUiState.Success -> {
                    CalendarSuccessContent(
                        excursions = state.excursions,
                        isRefreshing = isRefreshing,  // ← Usar la variable observada directamente
                        onRefresh = { viewModel.refresh() },
                        onExcursionClick = onNavigateToExcursionDetail,
                        isSearching = searchQuery.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }

                is CalendarUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }
    val closeSearch = {
        isSearchActive = false
        onClearSearch()
        focusManager.clearFocus()
    }
    BackHandler(enabled = isSearchActive, onBack = closeSearch)
    Column {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchActive) {
                        AppSearchTopBarContent(
                            query = searchQuery,
                            placeholder = "Buscar excursiones",
                            onQueryChange = onSearchQueryChange,
                            onClear = onClearSearch,
                            onClose = closeSearch
                        )
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text("ASOCIACIÓN CIGÜEÑA", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "Calendario",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar excursiones")
                        }
                    }
                }
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun YearSelector(
    selectedYear: Int?,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedYear == null || availableYears.isEmpty()) return

    val currentIndex = availableYears.indexOf(selectedYear)
    val canGoPrevious = currentIndex < availableYears.size - 1
    val canGoNext = currentIndex > 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón anterior (<)
            IconButton(
                onClick = {
                    if (canGoPrevious) {
                        onYearSelected(availableYears[currentIndex + 1])
                    }
                },
                enabled = canGoPrevious
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Año anterior",
                    tint = if (canGoPrevious) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(32.dp)
                )
            }

            // Año actual
            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Botón siguiente (>)
            IconButton(
                onClick = {
                    if (canGoNext) {
                        onYearSelected(availableYears[currentIndex - 1])
                    }
                },
                enabled = canGoNext
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Año siguiente",
                    tint = if (canGoNext) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSuccessContent(
    excursions: List<com.asociacionciguena.app.domain.model.Excursion>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onExcursionClick: (String) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {

    // ✅ Material3: Estado simple, sin parámetros en el remember
    val pullRefreshState = rememberPullToRefreshState()

    // ✅ Material3: PullToRefreshBox incluye el indicador automáticamente
    PullToRefreshBox(
        isRefreshing = isRefreshing,  // ← CORREGIDO: era 'refreshing'
        onRefresh = onRefresh,
        state = pullRefreshState,     // ← Usar la misma instancia
        // ✅ AÑADIR: fillMaxSize() para que detecte gestos en toda el área
        modifier = modifier.fillMaxSize()  // ← Cambiar de 'modifier' a 'modifier.fillMaxSize()'
    ) {
        if (excursions.isEmpty()) {
            EmptyState(
                message = if (isSearching) "No hay excursiones que coincidan con tu búsqueda" else "No hay excursiones en este año",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                excursions.groupBy { it.date.month }.forEach { (month, monthExcursions) ->
                    item(key = "month_$month") {
                        Text(
                            text = monthExcursions.first().date.toJavaLocalDateTime()
                                .format(calendarMonthFormatter)
                                .replaceFirstChar { it.titlecase(calendarLocale) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(monthExcursions, key = { it.id }) { excursion ->
                        ExcursionCard(
                            excursion = excursion,
                            onClick = { onExcursionClick(excursion.id) }
                        )
                    }
                }
            }
        }
    }
}

private val calendarLocale = Locale.forLanguageTag("es-ES")
private val calendarMonthFormatter = DateTimeFormatter.ofPattern("MMMM", calendarLocale)
/**
 * Contenido skeleton mientras carga
 */
@Composable
private fun CalendarSkeletonContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(6) { // Mostrar 6 skeletons
            ExcursionCardSkeleton()
        }
    }
}
