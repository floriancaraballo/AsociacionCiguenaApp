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
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asociacionciguena.app.util.ImageCompressor

@HiltViewModel
class PhotoUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val preselectedExcursionId: String? = savedStateHandle["excursionId"]
    private val _uiState = MutableStateFlow<PhotoUploadUiState>(PhotoUploadUiState.Idle)
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _excursions = MutableStateFlow<List<ExcursionOption>>(emptyList())
    val excursions: StateFlow<List<ExcursionOption>> = _excursions.asStateFlow()

    private val _users = MutableStateFlow<List<UserOption>>(emptyList())
    val users: StateFlow<List<UserOption>> = _users.asStateFlow()

    init {
        loadExcursions()
        loadUsers()

        // Pre-seleccionar excursión si viene por parámetro
        preselectedExcursionId?.let { excId ->
            // Esperamos a que se seleccione una foto
            // El estado se actualizará en onPhotoSelected
        }
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
    /**
     * Usuario selecciona UNA foto
     */
    fun onPhotoSelected(uri: Uri) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = listOf(uri),
            selectedExcursionId = preselectedExcursionId
        )
    }

    /**
     * Usuario selecciona MÚLTIPLES fotos
     */
    fun onPhotosSelected(uris: List<Uri>) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = uris,
            selectedExcursionId = preselectedExcursionId
        )
    }

    /**
     * Añadir más fotos a la selección actual
     */
    fun addMorePhotos(newUris: List<Uri>) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            _uiState.value = currentState.copy(
                uris = currentState.uris + newUris
            )
        }
    }

    /**
     * Eliminar una foto de la selección
     */
    fun removePhoto(uri: Uri) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            val updatedUris = currentState.uris.filter { it != uri }
            if (updatedUris.isEmpty()) {
                _uiState.value = PhotoUploadUiState.Idle
            } else {
                _uiState.value = currentState.copy(uris = updatedUris)
            }
        }
    }

    /**
     * Seleccionar excursión
     */
    fun onExcursionSelected(excursionId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            _uiState.value = currentState.copy(selectedExcursionId = excursionId)
        }
    }

    /**
     * Toggle selección de usuario
     */
    fun onUserToggled(userId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
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
    /**
     * Subir MÚLTIPLES fotos a Firebase Storage
     */
    fun uploadPhotos(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState !is PhotoUploadUiState.PhotosSelected) {
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

                val totalPhotos = currentState.uris.size

                // ← NUEVO: Crear documento de batch
                val batchId = UUID.randomUUID().toString()
                val batchData = hashMapOf(
                    "excursionId" to currentState.selectedExcursionId,
                    "photoCount" to totalPhotos,
                    "status" to "uploading",
                    "authorizedUsers" to currentState.selectedUsers,
                    "createdAt" to Timestamp.now()
                )

                firestore.collection("uploadBatches")
                    .document(batchId)
                    .set(batchData)
                    .await()

                // Subir cada foto
                currentState.uris.forEachIndexed { index, uri ->
                    _uiState.value = PhotoUploadUiState.Uploading(
                        progress = index.toFloat() / totalPhotos,
                        currentPhotoIndex = index + 1,
                        totalPhotos = totalPhotos
                    )

                    // Generar nombre único
                    val photoId = UUID.randomUUID().toString()
                    val fileName = "$photoId.jpg"
                    val storagePath = "excursions/${currentState.selectedExcursionId}/$fileName"

                    // COMPRIMIR antes de subir
                    val compressedFile = ImageCompressor.compressPhoto(context, uri)

// Subir a Storage
                    val storageRef = storage.reference.child(storagePath)
                    storageRef.putFile(Uri.fromFile(compressedFile)).await()

// Obtener URL
                    val downloadUrl = storageRef.downloadUrl.await().toString()

// Limpiar archivo temporal
                    compressedFile.delete()

                    // Guardar en Firestore
                    val photoData = hashMapOf(
                        "id" to photoId,
                        "excursionId" to currentState.selectedExcursionId,
                        "imageUrl" to downloadUrl,
                        "storagePath" to storagePath,
                        "uploadedBy" to "admin",
                        "uploadedAt" to Timestamp.now(),
                        "authorizedUsers" to currentState.selectedUsers,
                        "batchId" to batchId  // ← NUEVO: Asociar con batch
                    )

                    firestore.collection("photos")
                        .document(photoId)
                        .set(photoData)
                        .await()
                }

                // ← NUEVO: Marcar batch como completado
                firestore.collection("uploadBatches")
                    .document(batchId)
                    .update(
                        mapOf(
                            "status" to "completed",
                            "completedAt" to Timestamp.now()
                        )
                    )
                    .await()

                _uiState.value = PhotoUploadUiState.Success
                onSuccess()

                // Pequeño delay y volver a Idle
                viewModelScope.launch {
                    delay(1500)
                    _uiState.value = PhotoUploadUiState.Idle
                }

            } catch (e: Exception) {
                _uiState.value = PhotoUploadUiState.Error(
                    message = "Error al subir: ${e.message}"
                )
            }
        }
    }
    /**
     * Seleccionar todos los socios
     */
    fun selectAllUsers() {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            val allSocioIds = _users.value.map { it.id }
            _uiState.value = currentState.copy(selectedUsers = allSocioIds)
        }
    }

    fun deselectAllUsers() {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            _uiState.value = currentState.copy(selectedUsers = emptyList())
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