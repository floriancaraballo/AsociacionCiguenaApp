package com.asociacionciguena.app.presentation.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.usecase.auth.GetCurrentUserUseCase
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * ViewModel de la galería de fotos
 * VERSIÓN QUE CARGA FOTOS DIRECTAMENTE DESDE FIRESTORE
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            try {
                // Verificar usuario
                when (val result = getCurrentUserUseCase()) {
                    is Result.Success -> {
                        val user = result.data
                        if (user == null) {
                            _uiState.value = GalleryUiState.NotAuthenticated
                            return@launch
                        }

                        // Cargar fotos directamente desde Firestore
                        loadPhotosFromFirestore(user.id)
                    }

                    is Result.Error -> {
                        _uiState.value = GalleryUiState.NotAuthenticated
                    }

                    is Result.Loading -> {
                        // Mantener loading
                    }
                }
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadPhotosFromFirestore(userId: String) {
        try {
            // Query: Obtener TODAS las fotos donde authorizedUsers contiene el userId
            val snapshot = firestore.collection("photos")
                .whereArrayContains("authorizedUsers", userId)
                .get()
                .await()

            if (snapshot.isEmpty) {
                // No hay fotos autorizadas
                _uiState.value = GalleryUiState.Success(
                    photosByExcursion = emptyMap()
                )
                return
            }

            // Convertir documentos a Photos y agrupar por excursión
            val photosByExcursion = mutableMapOf<String, MutableList<Photo>>()

            snapshot.documents.forEach { doc ->
                try {
                    val photo = Photo(
                        id = doc.getString("id") ?: doc.id,
                        excursionId = doc.getString("excursionId") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        storagePath = doc.getString("storagePath") ?: "",
                        uploadedBy = doc.getString("uploadedBy") ?: "",
                        uploadedAt = doc.getTimestamp("uploadedAt")?.let {
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        } ?: kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                        authorizedUsers = (doc.get("authorizedUsers") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                    )

                    // Agrupar por excursión
                    val excursionId = photo.excursionId
                    if (photosByExcursion.containsKey(excursionId)) {
                        photosByExcursion[excursionId]?.add(photo)
                    } else {
                        photosByExcursion[excursionId] = mutableListOf(photo)
                    }

                } catch (e: Exception) {
                    android.util.Log.e("GalleryViewModel", "Error parseando foto ${doc.id}", e)
                }
            }

            // Actualizar estado
            _uiState.value = GalleryUiState.Success(
                photosByExcursion = photosByExcursion
            )

        } catch (e: Exception) {
            _uiState.value = GalleryUiState.Error(
                message = "Error al cargar fotos: ${e.message}"
            )
        }
    }

    fun refresh() {
        _uiState.value = GalleryUiState.Loading
        loadPhotos()
    }

    fun retry() {
        _uiState.value = GalleryUiState.Loading
        loadPhotos()
    }
}
