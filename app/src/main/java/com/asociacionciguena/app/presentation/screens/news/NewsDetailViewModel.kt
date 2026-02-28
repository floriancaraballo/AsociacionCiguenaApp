package com.asociacionciguena.app.presentation.screens.news

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val newsId: String = checkNotNull(savedStateHandle["newsId"])

    private val _uiState = MutableStateFlow<NewsDetailUiState>(NewsDetailUiState.Loading)
    val uiState: StateFlow<NewsDetailUiState> = _uiState.asStateFlow()

    init {
        loadNews(newsId)
    }

    fun loadNews(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = NewsDetailUiState.Loading

                val doc = firestore.collection("news")
                    .document(id)
                    .get()
                    .await()

                if (doc.exists()) {
                    // NUEVO: Cargar fotos adicionales
                    val additionalPhotos = (doc.get("additionalPhotos") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()

                    val news = News(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        imageUrl = doc.getString("imageUrl"),
                        publishedDate = doc.getTimestamp("publishedDate")?.let {
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        } ?: kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                        isPublic = doc.getBoolean("isPublic") ?: true,
                        additionalPhotos = additionalPhotos  // NUEVO
                    )

                    _uiState.value = NewsDetailUiState.Success(news)
                } else {
                    _uiState.value = NewsDetailUiState.Error("Noticia no encontrada")
                }

            } catch (e: Exception) {
                _uiState.value = NewsDetailUiState.Error("Error al cargar: ${e.message}")
            }
        }
    }
}
