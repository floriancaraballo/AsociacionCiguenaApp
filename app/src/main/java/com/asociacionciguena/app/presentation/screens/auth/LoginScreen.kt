package com.asociacionciguena.app.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val resetPasswordState by viewModel.resetPasswordState.collectAsState()

    var showResetPasswordDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Navegar si el login es exitoso
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onLoginSuccess()
        }
    }

    // Mostrar mensaje cuando se envía el email de recuperación
    LaunchedEffect(resetPasswordState) {
        when (resetPasswordState) {
            is ResetPasswordState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Email de recuperación enviado. Revisa tu bandeja de entrada.",
                    duration = SnackbarDuration.Long
                )
                showResetPasswordDialog = false
                viewModel.clearResetPasswordState()
            }
            is ResetPasswordState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (resetPasswordState as ResetPasswordState.Error).message,
                    duration = SnackbarDuration.Long
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área Privada") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LoginContent(
            email = email,
            password = password,
            uiState = uiState,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onLoginClick = viewModel::login,
            onClearError = viewModel::clearError,
            onForgotPasswordClick = { showResetPasswordDialog = true },
            modifier = Modifier.padding(paddingValues)
        )

        // Dialog de recuperación de contraseña
        if (showResetPasswordDialog) {
            ForgotPasswordDialog(
                currentEmail = email,
                resetPasswordState = resetPasswordState,
                onDismiss = {
                    showResetPasswordDialog = false
                    viewModel.clearResetPasswordState()
                },
                onSendResetEmail = { resetEmail ->
                    viewModel.sendPasswordResetEmail(resetEmail)
                }
            )
        }
    }
}

@Composable
private fun LoginContent(
    email: String,
    password: String,
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onClearError: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo o título
        Text(
            text = "🔐",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Acceso a Socios",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Accede a la galería de fotos y contenido exclusivo",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Campo Email
        OutlinedTextField(
            value = email,
            onValueChange = {
                onEmailChange(it)
                onClearError()
            },
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is AuthUiState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo Password
        OutlinedTextField(
            value = password,
            onValueChange = {
                onPasswordChange(it)
                onClearError()
            },
            label = { Text("Contraseña") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible)
                            "Ocultar contraseña"
                        else
                            "Mostrar contraseña"
                    )
                }
            },
            visualTransformation = if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onLoginClick()
                    }
                }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is AuthUiState.Loading
        )

        // NUEVO: Botón "¿Olvidaste tu contraseña?"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onForgotPasswordClick) {
                Text("¿Olvidaste tu contraseña?")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón Login
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = email.isNotBlank() &&
                    password.isNotBlank() &&
                    uiState !is AuthUiState.Loading
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Iniciar Sesión")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mensaje de error
        if (uiState is AuthUiState.Error) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    currentEmail: String,
    resetPasswordState: ResetPasswordState,
    onDismiss: () -> Unit,
    onSendResetEmail: (String) -> Unit
) {
    var resetEmail by remember { mutableStateOf(currentEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Recuperar Contraseña")
        },
        text = {
            Column {
                Text(
                    text = "Introduce tu email y te enviaremos un enlace para restablecer tu contraseña.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Email") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = resetPasswordState !is ResetPasswordState.Loading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendResetEmail(resetEmail) },
                enabled = resetEmail.isNotBlank() &&
                        resetPasswordState !is ResetPasswordState.Loading
            ) {
                if (resetPasswordState is ResetPasswordState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Enviar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = resetPasswordState !is ResetPasswordState.Loading
            ) {
                Text("Cancelar")
            }
        }
    )
}