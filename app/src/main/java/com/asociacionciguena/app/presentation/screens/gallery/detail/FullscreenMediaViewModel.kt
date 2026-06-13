package com.asociacionciguena.app.presentation.screens.gallery.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Photo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

sealed class FullscreenMediaUiState {
    object Loading : FullscreenMediaUiState()
    data class Success(
        val photos: List<Photo>,
        val initialIndex: Int
    ) : FullscreenMediaUiState()
    data class Error(val message: String) : FullscreenMediaUiState()
}

@HiltViewModel
class FullscreenMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val excursionId: String = checkNotNull(savedStateHandle["excursionId"])
    private val initialPhotoId: String = checkNotNull(savedStateHandle["photoId"])

    private val _uiState = MutableStateFlow<FullscreenMediaUiState>(FullscreenMediaUiState.Loading)
    val uiState: StateFlow<FullscreenMediaUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
    }

    fun retry() {
        loadPhotos()
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
