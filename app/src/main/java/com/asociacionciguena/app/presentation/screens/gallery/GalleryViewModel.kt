package com.asociacionciguena.app.presentation.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.asociacionciguena.app.domain.model.Excursion
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.google.firebase.auth.FirebaseAuth

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth  // ← AÑADIR
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadPastExcursions()
    }

    fun loadPastExcursions() {
        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState.Loading

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())

                val excursionsSnapshot = firestore.collection("excursions")
                    .get()
                    .await()

                val allExcursions = excursionsSnapshot.documents.mapNotNull { doc ->
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
                            imageUrl = doc.getString("imageUrl")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val pastExcursions = allExcursions.filter { it.date < now }

                val excursionsWithPhotos = pastExcursions.map { excursion ->
                    // Obtener UID del usuario actual
                    val currentUserId = auth.currentUser?.uid

                    val photosSnapshot = if (currentUserId != null) {
                        // Buscar solo fotos donde el usuario está autorizado
                        firestore.collection("photos")
                            .whereEqualTo("excursionId", excursion.id)
                            .whereArrayContains("authorizedUsers", currentUserId)
                            .get()
                            .await()
                    } else {
                        // Si no hay usuario, colección vacía
                        null
                    }

                    val photoCount = photosSnapshot?.size() ?: 0
                    val firstPhotoUrl = photosSnapshot?.documents?.firstOrNull()
                        ?.getString("imageUrl")

                    ExcursionWithPhotos(
                        excursion = excursion,
                        photoCount = photoCount,
                        firstPhotoUrl = firstPhotoUrl
                    )
                }

                val sortedExcursions = excursionsWithPhotos
                    .sortedByDescending { it.excursion.date }

                _uiState.value = GalleryUiState.Success(sortedExcursions)

            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(
                    message = "Error al cargar galería: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadPastExcursions()
    }
}
