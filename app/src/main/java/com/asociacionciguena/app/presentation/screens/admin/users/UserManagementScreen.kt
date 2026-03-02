package com.asociacionciguena.app.presentation.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.domain.model.User
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import kotlinx.coroutines.launch

@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSuperAdmin by viewModel.isSuperAdmin.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
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
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Crear Usuario")
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is UserManagementUiState.Loading -> {
                LoadingIndicator()
            }

            is UserManagementUiState.Success -> {
                UserListContent(
                    users = state.users,
                    isSuperAdmin = isSuperAdmin,
                    currentUserId = currentUserId,
                    onRoleChange = { userId, currentRole, newRole ->
                        viewModel.changeUserRole(
                            userId = userId,
                            currentUserRole = currentRole,
                            newRole = newRole,
                            onSuccess = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Rol actualizado correctamente",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onError = { errorMessage ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = errorMessage,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            }
                        )
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is UserManagementUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }

        // Diálogo de creación de usuario
        if (showCreateDialog) {
            CreateUserDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { email, displayName, role ->
                    viewModel.createUser(
                        email = email,
                        displayName = displayName,
                        role = role,
                        onSuccess = {
                            showCreateDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Usuario creado correctamente. Debe registrarse con ese email",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        },
                        onError = { errorMessage ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = errorMessage,
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("socio") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Usuario") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Crea un perfil de usuario. El usuario deberá registrarse posteriormente con este email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    )
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nombre completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Rol:",
                    style = MaterialTheme.typography.titleSmall
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == "admin",
                            onClick = { selectedRole = "admin" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Administrador")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == "socio",
                            onClick = { selectedRole = "socio" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Socio")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(email.trim(), displayName.trim(), selectedRole)
                },
                enabled = email.isNotBlank() && displayName.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun UserListContent(
    users: List<User>,
    isSuperAdmin: Boolean,
    currentUserId: String?,
    onRoleChange: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (users.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No hay usuarios",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = users,
                key = { it.id }
            ) { user ->
                UserCard(
                    user = user,
                    isSuperAdmin = isSuperAdmin,
                    isCurrentUser = user.id == currentUserId,
                    onRoleChange = { newRole -> onRoleChange(user.id, user.role, newRole) }
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    isSuperAdmin: Boolean,
    isCurrentUser: Boolean,
    onRoleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRoleDialog by remember { mutableStateOf(false) }
    val isClickable = user.role != "superadmin"

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (isCurrentUser) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Tú",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = {
                        if (isClickable) {
                            showRoleDialog = true
                        }
                    },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (user.role) {
                                    "superadmin" -> "Super-Admin"
                                    "admin" -> "Admin"
                                    "socio" -> "Socio"
                                    else -> "Usuario"
                                }
                            )

                            if (user.role == "superadmin") {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Protegido",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (user.role) {
                                "superadmin" -> Icons.Default.Shield
                                "admin" -> Icons.Default.Shield
                                else -> Icons.Default.Person
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (user.role) {
                            "superadmin" -> MaterialTheme.colorScheme.tertiaryContainer
                            "admin" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                    enabled = isClickable
                )
            }
        }
    }

    if (showRoleDialog && user.role != "superadmin") {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            title = { Text("Cambiar Rol") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecciona el nuevo rol para ${user.displayName}:")

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = user.role == "admin",
                            onClick = {
                                onRoleChange("admin")
                                showRoleDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Administrador",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Gestión de contenido y usuarios",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = user.role == "socio",
                            onClick = {
                                onRoleChange("socio")
                                showRoleDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Socio",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Acceso a contenido de socios",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isSuperAdmin && user.role == "admin") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Solo Super-Administradores pueden modificar Administradores",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoleDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
