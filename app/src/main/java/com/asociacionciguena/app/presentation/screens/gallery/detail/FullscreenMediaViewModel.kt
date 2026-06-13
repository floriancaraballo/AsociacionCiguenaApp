package com.asociacionciguena.app.presentation.screens.gallery.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.photo.DownloadPhotoUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class FullscreenMediaUiState {
    object Loading : FullscreenMediaUiState()
    data class Success(
        val photos: List<Photo>,
        val initialIndex: Int
    ) : FullscreenMediaUiState()
    data class Error(val message: String) : FullscreenMediaUiState()
}

sealed class FullscreenMediaEvent {
    data class PhotoDownloaded(
        val photo: Photo,
        val bytes: ByteArray
    ) : FullscreenMediaEvent()

    data class DownloadError(val message: String) : FullscreenMediaEvent()
}

@HiltViewModel
class FullscreenMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val downloadPhotoUseCase: DownloadPhotoUseCase
) : ViewModel() {

    private val excursionId: String = checkNotNull(savedStateHandle["excursionId"])
    private val initialPhotoId: String = checkNotNull(savedStateHandle["photoId"])

    private val _uiState = MutableStateFlow<FullscreenMediaUiState>(FullscreenMediaUiState.Loading)
    val uiState: StateFlow<FullscreenMediaUiState> = _uiState.asStateFlow()

    private val _downloadingPhotoId = MutableStateFlow<String?>(null)
    val downloadingPhotoId: StateFlow<String?> = _downloadingPhotoId.asStateFlow()

    private val eventChannel = Channel<FullscreenMediaEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        loadPhotos()
    }

    fun retry() {
        loadPhotos()
    }

    fun downloadPhoto(photo: Photo) {
        if (photo.mediaType != "image" || _downloadingPhotoId.value != null) return

        viewModelScope.launch {
            _downloadingPhotoId.value = photo.id
            when (val result = downloadPhotoUseCase(photo.id)) {
                is Result.Success -> {
                    eventChannel.send(
                        FullscreenMediaEvent.PhotoDownloaded(
                            photo = photo,
                            bytes = result.data
                        )
                    )
                }

                is Result.Error -> {
                    eventChannel.send(
                        FullscreenMediaEvent.DownloadError(
                            result.message.ifBlank { "No se pudo descargar la foto" }
                        )
                    )
                }

                Result.Loading -> Unit
            }
            _downloadingPhotoId.value = null
        }
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            try {
                _uiState.value = FullscreenMediaUiState.Loading

                val currentUserUid = auth.currentUser?.uid
                if (currentUserUid == null) {
                    _uiState.value = FullscreenMediaUiState.Error("Debes iniciar sesion")
                    return@launch
                }

                val userDoc = firestore.collection("users")
                    .document(currentUserUid)
                    .get()
                    .await()

                val excursionDoc = firestore.collection("excursions")
                    .document(excursionId)
                    .get()
                    .await()

                val role = userDoc.getString("role")
                val canManageMedia =
                    role == "admin" || role == "superadmin" || role == "monitor"
                val registrationYear = userDoc.registrationYear()
                val excursionYear = excursionDoc.getTimestamp("date")?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .year
                }

                if (!canManageMedia && excursionYear != registrationYear) {
                    _uiState.value = FullscreenMediaUiState.Error("No tienes permiso para ver estas fotos")
                    return@launch
                }

                firestore.collection("photos")
                    .whereEqualTo("excursionId", excursionId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            _uiState.value = FullscreenMediaUiState.Error(
                                error.message ?: "Error al cargar archivos multimedia"
                            )
                            return@addSnapshotListener
                        }

                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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

                        val initialIndex = photos.indexOfFirst { it.id == initialPhotoId }
                            .takeIf { it >= 0 } ?: 0

                        _uiState.value = FullscreenMediaUiState.Success(
                            photos = photos,
                            initialIndex = initialIndex
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = FullscreenMediaUiState.Error(
                    e.message ?: "Error al cargar archivos multimedia"
                )
            }
        }
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
}
