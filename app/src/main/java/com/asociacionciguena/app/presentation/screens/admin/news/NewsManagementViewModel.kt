package com.asociacionciguena.app.presentation.screens.admin.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.News
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlinx.datetime.toLocalDateTime

@HiltViewModel
class NewsManagementViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsManagementUiState>(NewsManagementUiState.Loading)
    val uiState: StateFlow<NewsManagementUiState> = _uiState.asStateFlow()

    init {
        loadNews()
    }

    /**
     * Cargar todas las noticias (públicas y as)
     */
    fun loadNews() {
        viewModelScope.launch {
            try {
                _uiState.value = NewsManagementUiState.Loading

                // No usar orderBy para evitar necesitar índice
// Ordenaremos en memoria después
                val snapshot = firestore.collection("news")
                    .get()
                    .await()

                val newsList = snapshot.documents.mapNotNull { doc ->
                    try {
                        News(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            shortDescription = doc.getString("shortDescription") ?: "",
                            content = doc.getString("content") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            additionalPhotos = (doc.get("additionalPhotos") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            // COMPATIBILIDAD: Usar createdAt si existe, sino publishedDate
                            createdAt = (doc.getTimestamp("createdAt")
                                ?: doc.getTimestamp("publishedDate"))?.let {
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            } ?: kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                            updatedAt = doc.getTimestamp("updatedAt")?.let {
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            },
                            isPublic = doc.getBoolean("isPublic") ?: true
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
// Ordenar por createdAt en memoria (más recientes primero)
                val sortedNewsList = newsList.sortedByDescending { it.createdAt }

                _uiState.value = NewsManagementUiState.Success(sortedNewsList)


            } catch (e: Exception) {
                _uiState.value = NewsManagementUiState.Error(
                    message = "Error al cargar publicaciones: ${e.message}"
                )
            }
        }
    }

    /**
     * Eliminar noticia
     */
    fun deleteNews(newsId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("news")
                    .document(newsId)
                    .delete()
                    .await()

                onSuccess()
                loadNews()

            } catch (e: Exception) {
                _uiState.value = NewsManagementUiState.Error(
                    message = "Error al eliminar: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle visibilidad (público/o)
     */
    fun toggleVisibility(newsId: String, currentIsPublic: Boolean) {
        viewModelScope.launch {
            try {
                firestore.collection("news")
                    .document(newsId)
                    .update("isPublic", !currentIsPublic)
                    .await()

                loadNews()

            } catch (e: Exception) {
                _uiState.value = NewsManagementUiState.Error(
                    message = "Error al cambiar visibilidad: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadNews()
    }
}