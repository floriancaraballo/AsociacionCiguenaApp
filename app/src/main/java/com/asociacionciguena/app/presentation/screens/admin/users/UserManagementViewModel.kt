package com.asociacionciguena.app.presentation.screens.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserManagementUiState>(UserManagementUiState.Loading)
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val _isSuperAdmin = MutableStateFlow(false)
    val isSuperAdmin: StateFlow<Boolean> = _isSuperAdmin.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    init {
        checkSuperAdminStatus()
        loadUsers()
    }

    private fun checkSuperAdminStatus() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    _currentUserId.value = currentUser.uid

                    val userDoc = firestore.collection("users")
                        .document(currentUser.uid)
                        .get()
                        .await()

                    _isSuperAdmin.value = userDoc.getString("role") == "superadmin"
                }
            } catch (e: Exception) {
                _isSuperAdmin.value = false
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            try {
                _uiState.value = UserManagementUiState.Loading

                val snapshot = firestore.collection("users")
                    .get()
                    .await()

                val usersList = snapshot.documents.mapNotNull { doc ->
                    try {
                        User(
                            id = doc.id,
                            email = doc.getString("email") ?: "",
                            displayName = doc.getString("displayName") ?: "Usuario",
                            role = doc.getString("role") ?: "socio",
                            createdAt = doc.getTimestamp("createdAt")?.let {
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            } ?: kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                            photoConsents = (doc.get("photoConsents") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Filtrar super-admins si no eres super-admin
                val filteredUsers = if (_isSuperAdmin.value) {
                    usersList
                } else {
                    usersList.filter { it.role != "superadmin" }
                }

                val sortedUsers = filteredUsers.sortedWith(
                    compareByDescending<User> { it.role == "superadmin" }
                        .thenByDescending { it.role == "admin" }
                        .thenBy { it.displayName }
                )

                _uiState.value = UserManagementUiState.Success(sortedUsers)

            } catch (e: Exception) {
                _uiState.value = UserManagementUiState.Error(
                    message = "Error al cargar usuarios: ${e.message}"
                )
            }
        }
    }

    fun changeUserRole(
        userId: String,
        currentUserRole: String,
        newRole: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid

                if (currentUserRole == "superadmin") {
                    onError("⛔ El rol de Super-Administrador está protegido y solo puede modificarse desde Firebase Console")
                    return@launch
                }

                if (newRole == "superadmin") {
                    onError("⛔ No se puede asignar el rol de Super-Administrador desde la app. Solo puede hacerse desde Firebase Console")
                    return@launch
                }

                if (userId == currentUserId && newRole !in listOf("admin", "superadmin")) {
                    onError("No puedes degradar tu propio rol")
                    return@launch
                }

                if (currentUserRole == "admin" && !_isSuperAdmin.value) {
                    onError("Solo un Super-Administrador puede modificar a un Administrador")
                    return@launch
                }

                firestore.collection("users")
                    .document(userId)
                    .update("role", newRole)
                    .await()

                onSuccess()
                loadUsers()

            } catch (e: Exception) {
                onError("Error al cambiar rol: ${e.message}")
            }
        }
    }

    // NUEVO: Crear usuario solo en colección "pendingUsers"
    // La Cloud Function detectará esto y creará el usuario en Auth + migrará a "users"
    fun createUser(
        email: String,
        displayName: String,
        role: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Validaciones
                if (email.isBlank()) {
                    onError("El email es obligatorio")
                    return@launch
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    onError("Email inválido")
                    return@launch
                }

                if (displayName.isBlank()) {
                    onError("El nombre es obligatorio")
                    return@launch
                }

                if (role == "superadmin") {
                    onError("⛔ No se puede crear Super-Administradores desde la app")
                    return@launch
                }

                // Verificar si el email ya existe
                val existingUser = firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()

                if (!existingUser.isEmpty) {
                    onError("Ya existe un usuario con ese email")
                    return@launch
                }

                // CAMBIO CRÍTICO: Crear en colección temporal "pendingUsers"
                // La Cloud Function lo detectará, creará en Auth, y lo moverá a "users" con UID correcto
                val userData = hashMapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "role" to role,
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "photoConsents" to emptyList<String>(),
                    "needsRegistration" to true,
                    "invitationSent" to false
                )

                // Crear en colección temporal
                firestore.collection("pendingUsers")
                    .add(userData) // ← Usar add() para ID automático
                    .await()

                onSuccess()
                // No llamar a loadUsers() porque aún no está en "users"

            } catch (e: Exception) {
                onError("Error al crear usuario: ${e.message}")
            }
        }
    }

    fun retry() {
        loadUsers()
    }
}
