package com.asociacionciguena.app.presentation.screens.admin.pins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinManagementScreen(
    viewModel: PinManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var editingEnabled by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var childToDelete by remember { mutableStateOf<ChildPin?>(null) }

    fun showError(error: String?) {
        if (error != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gestionar Pines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadPins) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar lista")
                    }
                    TextButton(onClick = { editingEnabled = !editingEnabled }) {
                        Icon(
                            imageVector = if (editingEnabled) Icons.Default.Done else Icons.Default.Edit,
                            contentDescription = null
                        )
                        Text(if (editingEnabled) " Listo" else " Editar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (editingEnabled) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir niño o niña")
                }
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            PinManagementUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is PinManagementUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp).padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::loadPins) { Text("Reintentar") }
                    }
                }
            }

            is PinManagementUiState.Success -> {
                PinList(
                    children = state.children.filter { it.name.matchesSearch(searchQuery) },
                    totalChildren = state.children.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isEditing = editingEnabled,
                    onIncrement = { child ->
                        viewModel.changePinCount(child.id, 1) { showError(it) }
                    },
                    onDecrement = { child ->
                        viewModel.changePinCount(child.id, -1) { showError(it) }
                    },
                    onDelete = { childToDelete = it },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    if (showAddDialog) {
        AddChildDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addChild(name) { error ->
                    if (error == null) showAddDialog = false else showError(error)
                }
            }
        )
    }

    childToDelete?.let { child ->
        DeleteChildDialog(
            child = child,
            onDismiss = { childToDelete = null },
            onConfirm = {
                viewModel.deleteChild(child.id) { error ->
                    if (error == null) childToDelete = null else showError(error)
                }
            }
        )
    }
}

@Composable
private fun PinList(
    children: List<ChildPin>,
    totalChildren: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isEditing: Boolean,
    onIncrement: (ChildPin) -> Unit,
    onDecrement: (ChildPin) -> Unit,
    onDelete: (ChildPin) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PinSearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
        }
        if (children.isEmpty()) {
            item {
                EmptyPinListMessage(
                    hasActiveSearch = searchQuery.isNotBlank(),
                    isEditing = isEditing,
                    totalChildren = totalChildren
                )
            }
        } else {
            items(children, key = { it.id }) { child ->
                PinRow(
                    child = child,
                    isEditing = isEditing,
                    onIncrement = { onIncrement(child) },
                    onDecrement = { onDecrement(child) },
                    onDelete = { onDelete(child) }
                )
            }
        }
    }
}

@Composable
private fun PinSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Buscar por nombre") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        }
    )
}

@Composable
private fun EmptyPinListMessage(hasActiveSearch: Boolean, isEditing: Boolean, totalChildren: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (hasActiveSearch) Icons.Default.Search else Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasActiveSearch && totalChildren > 0) "No hay coincidencias."
            else "Todavía no hay niños o niñas en la lista."
        )
        if (!hasActiveSearch && !isEditing) {
            Text(
                "Pulsa Editar para añadir el primero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PinRow(
    child: ChildPin,
    isEditing: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(child.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (isEditing) {
                IconButton(onClick = onDecrement, enabled = child.pinCount > 0) {
                    Icon(Icons.Default.Remove, contentDescription = "Quitar un pin a ${child.name}")
                }
            }
            Text("${child.pinCount}", style = MaterialTheme.typography.headlineSmall)
            if (isEditing) {
                IconButton(onClick = onIncrement) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir un pin a ${child.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar a ${child.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddChildDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir niño o niña") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 100) name = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteChildDialog(child: ChildPin, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar de la lista") },
        text = { Text("Se eliminará a ${child.name} y su contador de ${child.pinCount} pines. Esta acción no se puede deshacer.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Eliminar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun String.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return normalizeForSearch().contains(query.normalizeForSearch())
}

private fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
