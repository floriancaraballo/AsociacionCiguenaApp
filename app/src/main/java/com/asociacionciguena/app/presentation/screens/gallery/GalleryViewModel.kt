package com.asociacionciguena.app.presentation.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Listener para actualizaciones en tiempo real
    private var photosListener: ListenerRegistration? = null

    init {
        loadExcursionsWithPhotos()
    }

    fun loadExcursionsWithPhotos() {
        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState.Loading

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())

                // Obtener TODAS las excursiones
                val excursionsSnapshot = firestore.collection("excursions")
                    .get()
                    .await()

                // Filtrar solo excursiones PASADAS
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
                            imageUrl = doc.getString("imageUrl"),
                            authorizationPdfUrl = doc.getString("authorizationPdfUrl")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.filter { it.date < now }

                // Configurar listener en tiempo real para fotos
                setupPhotosListener(allExcursions)

            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(
                    message = "Error al cargar galería: ${e.message}"
                )
            }
        }
    }

    private fun setupPhotosListener(excursions: List<Excursion>) {
        // Cancelar listener anterior si existe
        photosListener?.remove()

        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            _uiState.value = GalleryUiState.Success(emptyList())
            return
        }

        // Listener en tiempo real para cambios en fotos
        photosListener = firestore.collection("photos")
            .whereArrayContains("authorizedUsers", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = GalleryUiState.Error("Error: ${error.message}")
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    try {
                        // Agrupar fotos por excursión
                        val photosByExcursion = snapshot?.documents
                            ?.groupBy { it.getString("excursionId") ?: "" }
                            ?: emptyMap()

                        // Mapear excursiones con sus fotos
                        // Mapear TODAS las excursiones con sus fotos (incluidas las que tienen 0)
                        val excursionsWithPhotos = excursions.map { excursion ->
                            val photos = photosByExcursion[excursion.id] ?: emptyList()
                            val photoCount = photos.size
                            val firstPhotoUrl = photos.firstOrNull()?.let { firstPhoto ->
                                val mediaType = firstPhoto.getString("mediaType") ?: "image"
                                if (mediaType == "video") {
                                    firstPhoto.getString("thumbnailUrl")
                                        ?: firstPhoto.getString("imageUrl")
                                } else {
                                    firstPhoto.getString("imageUrl")
                                }
                            }

                            ExcursionWithPhotos(
                                excursion = excursion,
                                photoCount = photoCount,
                                firstPhotoUrl = firstPhotoUrl ?: excursion.imageUrl  // Usar imagen de excursión si no hay fotos
                            )
                        }

                        // Ordenar por fecha (más recientes primero)
                        val sortedExcursions = excursionsWithPhotos
                            .sortedByDescending { it.excursion.date }

                        _uiState.value = GalleryUiState.Success(sortedExcursions)

                    } catch (e: Exception) {
                        _uiState.value = GalleryUiState.Error("Error: ${e.message}")
                    }
                }
            }
    }

    fun retry() {
        loadExcursionsWithPhotos()
    }

    override fun onCleared() {
        super.onCleared()
        // Cancelar listener al destruir el ViewModel
        photosListener?.remove()
    }
}
