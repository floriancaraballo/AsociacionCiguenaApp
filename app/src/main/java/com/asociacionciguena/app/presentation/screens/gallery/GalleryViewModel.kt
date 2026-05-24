package com.asociacionciguena.app.presentation.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
class GalleryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var photosListener: ListenerRegistration? = null

    init {
        loadExcursionsWithPhotos()
    }

    fun loadExcursionsWithPhotos() {
        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState.Loading

                val currentUserId = auth.currentUser?.uid
                if (currentUserId == null) {
                    _uiState.value = GalleryUiState.Success(emptyList())
                    return@launch
                }

                val userDoc = firestore.collection("users")
                    .document(currentUserId)
                    .get()
                    .await()

                val isAdmin = userDoc.isAdmin()
                val registrationYear = userDoc.registrationYear()

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())

                val excursionsSnapshot = firestore.collection("excursions")
                    .get()
                    .await()

                val pastExcursions = excursionsSnapshot.documents.mapNotNull { doc ->
                    try {
                        Excursion(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            date = doc.getTimestamp("date")?.let {
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                            } ?: now,
                            location = doc.getString("location") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            authorizationPdfUrl = doc.getString("authorizationPdfUrl")
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.filter { it.date < now }

                val visibleExcursions = if (isAdmin) {
                    pastExcursions
                } else {
                    registrationYear?.let { year ->
                        pastExcursions.filter { it.date.year == year }
                    } ?: emptyList()
                }

                setupPhotosListener(visibleExcursions)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(
                    message = "Error al cargar galeria: ${e.message}"
                )
            }
        }
    }

    private fun setupPhotosListener(excursions: List<Excursion>) {
        photosListener?.remove()

        if (excursions.isEmpty()) {
            _uiState.value = GalleryUiState.Success(emptyList())
            return
        }

        photosListener = firestore.collection("photos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = GalleryUiState.Error("Error: ${error.message}")
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    try {
                        val photosByExcursion = snapshot?.documents
                            ?.groupBy { it.getString("excursionId") ?: "" }
                            ?: emptyMap()

                        val excursionsWithPhotos = excursions.map { excursion ->
                            val photos = photosByExcursion[excursion.id] ?: emptyList()
                            val firstPhoto = photos.firstOrNull()

                            ExcursionWithPhotos(
                                excursion = excursion,
                                photoCount = photos.size,
                                firstPhotoUrl = firstPhoto?.getString("imageUrl")
                                    ?: excursion.imageUrl,
                                firstPhotoThumbnailUrl = firstPhoto?.getString("thumbnailUrl"),
                                firstPhotoMediaType = firstPhoto?.getString("mediaType")
                                    ?: "image"
                            )
                        }

                        _uiState.value = GalleryUiState.Success(
                            excursionsWithPhotos.sortedByDescending { it.excursion.date }
                        )
                    } catch (e: Exception) {
                        _uiState.value = GalleryUiState.Error("Error: ${e.message}")
                    }
                }
            }
    }

    private fun DocumentSnapshot.isAdmin(): Boolean {
        val role = getString("role")
        return role == "admin" || role == "superadmin"
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

    fun retry() {
        loadExcursionsWithPhotos()
    }

    override fun onCleared() {
        super.onCleared()
        photosListener?.remove()
    }
}
