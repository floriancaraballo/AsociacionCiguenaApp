package com.asociacionciguena.app.presentation.screens.admin.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PhotoUploadViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhotoUploadUiState>(PhotoUploadUiState.Idle)
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _excursions = MutableStateFlow<List<ExcursionOption>>(emptyList())
    val excursions: StateFlow<List<ExcursionOption>> = _excursions.asStateFlow()

    private val _users = MutableStateFlow<List<UserOption>>(emptyList())
    val users: StateFlow<List<UserOption>> = _users.asStateFlow()

    init {
        loadExcursions()
        loadUsers()
    }

    /**
     * Cargar excursiones disponibles
     */
    private fun loadExcursions() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("excursions")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                _excursions.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        ExcursionOption(
                            id = doc.id,
                            title = doc.getString("title") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Cargar usuarios disponibles
     */
    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users")
                    .get()
                    .await()

                _users.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        UserOption(
                            id = doc.id,
                            displayName = doc.getString("displayName") ?: "Usuario",
                            email = doc.getString("email") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Usuario selecciona una foto
     */
    fun onPhotoSelected(uri: Uri) {
        _uiState.value = PhotoUploadUiState.PhotoSelected(uri = uri)
    }

    /**
     * Seleccionar excursión
     */
    fun onExcursionSelected(excursionId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotoSelected) {
            _uiState.value = currentState.copy(selectedExcursionId = excursionId)
        }
    }

    /**
     * Toggle selección de usuario
     */
    fun onUserToggled(userId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotoSelected) {
            val currentUsers = currentState.selectedUsers.toMutableList()
            if (userId in currentUsers) {
                currentUsers.remove(userId)
            } else {
                currentUsers.add(userId)
            }
            _uiState.value = currentState.copy(selectedUsers = currentUsers)
        }
    }

    /**
     * Subir foto a Firebase Storage
     */
    fun uploadPhoto(uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState !is PhotoUploadUiState.PhotoSelected) {
                    _uiState.value = PhotoUploadUiState.Error("Estado inválido")
                    return@launch
                }

                // Validaciones
                if (currentState.selectedExcursionId == null) {
                    _uiState.value = PhotoUploadUiState.Error("Selecciona una excursión")
                    return@launch
                }

                if (currentState.selectedUsers.isEmpty()) {
                    _uiState.value = PhotoUploadUiState.Error("Selecciona al menos un usuario")
                    return@launch
                }

                _uiState.value = PhotoUploadUiState.Uploading(progress = 0f)

                // Generar nombre único para la foto
                val photoId = UUID.randomUUID().toString()
                val fileName = "$photoId.jpg"
                val storagePath = "excursions/${currentState.selectedExcursionId}/$fileName"

                // Subir a Storage
                val storageRef = storage.reference.child(storagePath)
                val uploadTask = storageRef.putFile(uri)

                // Monitorear progreso
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                    _uiState.value = PhotoUploadUiState.Uploading(progress = progress / 100f)
                }

                // Esperar a que termine
                uploadTask.await()

                // Obtener URL de descarga
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Guardar metadata en Firestore
                val photoData = hashMapOf(
                    "id" to photoId,
                    "excursionId" to currentState.selectedExcursionId,
                    "imageUrl" to downloadUrl,
                    "storagePath" to storagePath,
                    "uploadedBy" to "admin",
                    "uploadedAt" to Timestamp.now(),
                    "authorizedUsers" to currentState.selectedUsers
                )

                firestore.collection("photos")
                    .document(photoId)
                    .set(photoData)
                    .await()

                _uiState.value = PhotoUploadUiState.Success
                onSuccess()

            } catch (e: Exception) {
                _uiState.value = PhotoUploadUiState.Error(
                    message = "Error al subir: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value is PhotoUploadUiState.Error) {
            _uiState.value = PhotoUploadUiState.Idle
        }
    }

    fun reset() {
        _uiState.value = PhotoUploadUiState.Idle
    }
}

/**
 * Data classes auxiliares
 */
data class ExcursionOption(
    val id: String,
    val title: String
)

data class UserOption(
    val id: String,
    val displayName: String,
    val email: String
)