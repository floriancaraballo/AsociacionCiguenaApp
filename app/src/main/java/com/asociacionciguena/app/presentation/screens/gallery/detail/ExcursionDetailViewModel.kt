package com.asociacionciguena.app.presentation.screens.gallery.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Photo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
                    _uiState.value = ExcursionDetailUiState.Error("Excursion no encontrada")
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
                    latitude = excursionDoc.getDouble("latitude"),
                    longitude = excursionDoc.getDouble("longitude"),
                    imageUrl = excursionDoc.getString("imageUrl"),
                    authorizationPdfUrl = excursionDoc.getString("authorizationPdfUrl")
                )

                val currentUserUid = auth.currentUser?.uid
                val userDoc = currentUserUid?.let {
                    firestore.collection("users")
                        .document(it)
                        .get()
                        .await()
                }
                val canManageMedia = userDoc?.canManageMedia() ?: false
                val registrationYear = userDoc?.registrationYear()

                if (!canManageMedia && excursion.date.year != registrationYear) {
                    _uiState.value = ExcursionDetailUiState.Success(
                        excursion = excursion,
                        photos = emptyList(),
                        isAdmin = false
                    )
                    return@launch
                }

                setupPhotosListener(excursion, canManageMedia, currentUserUid)
            } catch (e: Exception) {
                _uiState.value = ExcursionDetailUiState.Error(
                    "Error al cargar detalles: ${e.message}"
                )
            }
        }
    }

    private fun setupPhotosListener(excursion: Excursion, isAdmin: Boolean, currentUserUid: String?) {
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

        photosListener = firestore.collection("photos")
            .whereEqualTo("excursionId", excursionId)
            .addSnapshotListener { snapshot, error ->
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
                            } catch (_: Exception) {
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

    fun deletePhoto(photo: Photo, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!currentUserCanManageMedia()) {
                    onError("No tienes permisos para eliminar fotos")
                    return@launch
                }

                deleteStorageAsset(photo.storagePath)
                photo.thumbnailUrl?.let { deleteStorageAssetByUrl(it) }

                firestore.collection("photos")
                    .document(photo.id)
                    .delete()
                    .await()

                onSuccess()
            } catch (e: Exception) {
                onError("Error al eliminar: ${e.message}")
            }
        }
    }

    fun deleteMultiplePhotos(photos: List<Photo>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!currentUserCanManageMedia()) {
                    onError("No tienes permisos para eliminar fotos")
                    return@launch
                }

                photos.forEach { photo ->
                    deleteStorageAsset(photo.storagePath)
                    photo.thumbnailUrl?.let { deleteStorageAssetByUrl(it) }
                    firestore.collection("photos")
                        .document(photo.id)
                        .delete()
                        .await()
                }

                onSuccess()
            } catch (e: Exception) {
                onError("Error al eliminar elementos: ${e.message}")
            }
        }
    }

    private suspend fun deleteStorageAsset(storagePath: String) {
        if (storagePath.isBlank()) return

        try {
            storage.reference.child(storagePath).delete().await()
        } catch (e: Exception) {
            if (e !is StorageException || e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw IllegalStateException(
                    "Storage denego el borrado de '$storagePath': ${e.message}",
                    e
                )
            }
        }
    }

    private suspend fun deleteStorageAssetByUrl(url: String) {
        if (url.isBlank()) return

        try {
            storage.getReferenceFromUrl(url).delete().await()
        } catch (e: Exception) {
            if (e !is StorageException || e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw IllegalStateException(
                    "Storage denego el borrado de la miniatura '$url': ${e.message}",
                    e
                )
            }
        }
    }

    private fun DocumentSnapshot.canManageMedia(): Boolean {
        val role = getString("role")
        return role == "admin" || role == "superadmin" || role == "monitor"
    }

    private fun DocumentSnapshot.registrationYear(): Int? {
        return getTimestamp("createdAt")?.let {
            kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
        } ?: auth.currentUser?.metadata?.creationTimestamp?.let {
            kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
        }
    }

    private suspend fun currentUserCanManageMedia(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return firestore.collection("users")
            .document(userId)
            .get()
            .await()
            .canManageMedia()
    }

    fun retry() {
        loadExcursionDetails()
    }

    override fun onCleared() {
        super.onCleared()
        photosListener?.remove()
    }
}
