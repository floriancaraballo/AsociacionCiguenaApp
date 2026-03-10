package com.asociacionciguena.app.presentation.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.auth.GetCurrentUserUseCase
import com.asociacionciguena.app.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.asociacionciguena.app.data.manager.FCMTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asociacionciguena.app.util.ImageCompressor

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val fcmTokenManager: FCMTokenManager,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Estados de edición
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _editedName = MutableStateFlow("")
    val editedName: StateFlow<String> = _editedName.asStateFlow()

    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading

            when (val result = getCurrentUserUseCase()) {
                is Result.Success -> {
                    val user = result.data
                    if (user != null) {
                        _uiState.value = ProfileUiState.LoggedIn(user)
                        _editedName.value = user.displayName
                    } else {
                        _uiState.value = ProfileUiState.NotLoggedIn
                    }
                }

                is Result.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }

                is Result.Loading -> {
                    // Mantener loading
                }
            }
        }
    }

    fun enableEditMode() {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.LoggedIn) {
            _editedName.value = currentState.user.displayName
            _selectedPhotoUri.value = null
            _isEditMode.value = true
        }
    }

    fun cancelEdit() {
        _isEditMode.value = false
        _selectedPhotoUri.value = null
        _uploadProgress.value = 0f
    }

    fun onNameChange(newName: String) {
        _editedName.value = newName
    }

    fun onPhotoSelected(uri: Uri) {
        _selectedPhotoUri.value = uri
    }

    fun removeSelectedPhoto() {
        _selectedPhotoUri.value = null
    }

    fun saveProfile() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState !is ProfileUiState.LoggedIn) return@launch

                if (_editedName.value.isBlank()) {
                    _uiState.value = ProfileUiState.Error("El nombre no puede estar vacío")
                    return@launch
                }

                _isSaving.value = true
                _uploadProgress.value = 0f

                val userId = auth.currentUser?.uid ?: return@launch

                // Subir foto si hay una nueva seleccionada
                var newPhotoUrl = currentState.user.photoUrl
                if (_selectedPhotoUri.value != null) {
                    // COMPRIMIR antes de subir
                    val compressedFile = ImageCompressor.compressProfilePhoto(context, _selectedPhotoUri.value!!)
                    newPhotoUrl = uploadProfilePhoto(Uri.fromFile(compressedFile), userId)

                    // Limpiar archivo temporal
                    compressedFile.delete()
                }

                // Actualizar en Firestore
                val updates = hashMapOf<String, Any>(
                    "displayName" to _editedName.value
                )
                if (newPhotoUrl != null) {
                    updates["photoUrl"] = newPhotoUrl
                    println("🔍 Guardando photoUrl: $newPhotoUrl")  // ← DEBUG
                }

                println("🔍 Updates a guardar: $updates")  // ← DEBUG

                firestore.collection("users")
                    .document(userId)
                    .update(updates)
                    .await()

                println("✅ Perfil guardado en Firestore")  // ← DEBUG

                // Actualizar perfil de Firebase Auth también
                val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                    displayName = _editedName.value
                    if (newPhotoUrl != null) {
                        photoUri = Uri.parse(newPhotoUrl)
                    }
                }
                auth.currentUser?.updateProfile(profileUpdates)?.await()
// IMPORTANTE: Refrescar el token para que los cambios se sincronicen
                auth.currentUser?.reload()?.await()

                println("🔄 Firebase Auth actualizado y refrescado")

                _isEditMode.value = false
                _isSaving.value = false
                _selectedPhotoUri.value = null
                _uploadProgress.value = 0f

                // Actualizar estado directamente con los nuevos valores
                val updatedUser = currentState.user.copy(
                    displayName = _editedName.value,
                    photoUrl = newPhotoUrl ?: currentState.user.photoUrl
                )
                _uiState.value = ProfileUiState.LoggedIn(updatedUser)
                println("✅ Estado actualizado con nueva foto: ${updatedUser.photoUrl}")


            } catch (e: Exception) {
                _isSaving.value = false
                _uiState.value = ProfileUiState.Error("Error al guardar: ${e.message}")
            }
        }
    }

    private suspend fun uploadProfilePhoto(uri: Uri, userId: String): String {
        val photoId = UUID.randomUUID().toString()
        val storagePath = "users/$userId/profile_$photoId.jpg"
        val storageRef = storage.reference.child(storagePath)

        // Subir archivo
        val uploadTask = storageRef.putFile(uri)

        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
            _uploadProgress.value = progress / 100f
        }

        // Esperar a que termine la subida
        val taskSnapshot = uploadTask.await()

        // Obtener URL directamente del resultado del upload
        val downloadUrl = taskSnapshot.metadata?.reference?.downloadUrl?.await()

        return downloadUrl?.toString() ?: throw Exception("No se pudo obtener la URL de descarga")
    }

    fun logout() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ProfileUiState.LoggedIn) {
                _uiState.value = currentState.copy(isLoggingOut = true)
            }

            try {
                fcmTokenManager.deleteToken()
            } catch (e: Exception) {
                // Log error pero continuar con logout
            }

            logoutUseCase()
            _uiState.value = ProfileUiState.NotLoggedIn
        }
    }

    fun retry() {
        loadProfile()
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Error) {
            loadProfile()
        }
    }
}
