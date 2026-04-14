package com.asociacionciguena.app.presentation.screens.gallery.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Photo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.google.firebase.storage.FirebaseStorage

@HiltViewModel
class ExcursionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : ViewModel() {

    private val excursionId: String = checkNotNull(savedStateHandle["excursionId"])

    private val _uiState = MutableStateFlow<ExcursionDetailUiState>(
        ExcursionDetailUiState.Loading
    )
    val uiState: StateFlow<ExcursionDetailUiState> = _uiState.asStateFlow()

    // Listener en tiempo real
    private var photosListener: ListenerRegistration? = null

    init {
        loadExcursionDetails()
    }

    private fun loadExcursionDetails() {
        viewModelScope.launch {
            try {
                _uiState.value = ExcursionDetailUiState.Loading

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())

                val excursionDoc = firestore.collection("excursions")
                    .document(excursionId)
                    .get()
                    .await()

                if (!excursionDoc.exists()) {
                    _uiState.value = ExcursionDetailUiState.Error("Excursión no encontrada")
                    return@launch
                }

                val excursion = Excursion(
                    id = excursionDoc.id,
                    title = excursionDoc.getString("title") ?: "",
                    description = excursionDoc.getString("description") ?: "",
                    date = excursionDoc.getTimestamp("date")?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                    } ?: now,
                    location = excursionDoc.getString("location") ?: "",
                    imageUrl = excursionDoc.getString("imageUrl"),
                    authorizationPdfUrl = excursionDoc.getString("authorizationPdfUrl")
                )

                val currentUserUid = auth.currentUser?.uid

                // Verificar si es admin
                val isAdmin = if (currentUserUid != null) {
                    val userDoc = firestore.collection("users")
                        .document(currentUserUid)
                        .get()
                        .await()
                    val role = userDoc.getString("role")
                    role == "admin" || role == "superadmin"
                } else {
                    false
                }

                // Configurar listener en tiempo real
                setupPhotosListener(excursion, isAdmin, currentUserUid)

            } catch (e: Exception) {
                _uiState.value = ExcursionDetailUiState.Error(
                    "Error al cargar detalles: ${e.message}"
                )
            }
        }
    }

    private fun setupPhotosListener(excursion: Excursion, isAdmin: Boolean, currentUserUid: String?) {
        // Cancelar listener anterior si existe
        photosListener?.remove()

        if (currentUserUid == null) {
            _uiState.value = ExcursionDetailUiState.Success(
                excursion = excursion,
                photos = emptyList(),
                isAdmin = false
            )
            return
        }

        val now = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())

        // Query según permisos
        val query = if (isAdmin) {
            // Admins ven TODAS las fotos
            firestore.collection("photos")
                .whereEqualTo("excursionId", excursionId)
        } else {
            // Socios ven SOLO fotos autorizadas
            firestore.collection("photos")
                .whereEqualTo("excursionId", excursionId)
                .whereArrayContains("authorizedUsers", currentUserUid)
        }

        // Listener en tiempo real
        photosListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.value = ExcursionDetailUiState.Error("Error: ${error.message}")
                return@addSnapshotListener
            }

            viewModelScope.launch {
                try {
                    val photos = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            Photo(
                                id = doc.id,
                                excursionId = doc.getString("excursionId") ?: "",
                                imageUrl = doc.getString("imageUrl") ?: "",
                                storagePath = doc.getString("storagePath") ?: "",
                                uploadedBy = doc.getString("uploadedBy") ?: "",
                                uploadedAt = doc.getTimestamp("uploadedAt")?.let {
                                    kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                } ?: now,
                                authorizedUsers = (doc.get("authorizedUsers") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                mediaType = doc.getString("mediaType") ?: "image",
                                thumbnailUrl = doc.getString("thumbnailUrl")
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    _uiState.value = ExcursionDetailUiState.Success(
                        excursion = excursion,
                        photos = photos,
                        isAdmin = isAdmin
                    )

                } catch (e: Exception) {
                    _uiState.value = ExcursionDetailUiState.Error("Error: ${e.message}")
                }
            }
        }
    }

    fun deletePhoto(photoId: String, storagePath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Eliminar de Storage
                storage.reference.child(storagePath).delete().await()

                // Eliminar de Firestore
                firestore.collection("photos")
                    .document(photoId)
                    .delete()
                    .await()

                onSuccess()
                // El listener actualizará automáticamente

            } catch (e: Exception) {
                onError("Error al eliminar: ${e.message}")
            }
        }
    }

    fun deleteMultiplePhotos(photos: List<Photo>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                photos.forEach { photo ->
                    // Eliminar de Storage
                    storage.reference.child(photo.storagePath).delete().await()

                    // Eliminar de Firestore
                    firestore.collection("photos")
                        .document(photo.id)
                        .delete()
                        .await()
                }

                onSuccess()
                // El listener actualizará automáticamente

            } catch (e: Exception) {
                onError("Error al eliminar fotos: ${e.message}")
            }
        }
    }

    fun retry() {
        loadExcursionDetails()
    }

    override fun onCleared() {
        super.onCleared()
        // Cancelar listener al destruir el ViewModel
        photosListener?.remove()
    }
}
