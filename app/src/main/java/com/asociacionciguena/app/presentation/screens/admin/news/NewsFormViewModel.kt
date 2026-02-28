package com.asociacionciguena.app.presentation.screens.admin.news

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NewsFormViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val newsId: String? = savedStateHandle.get<String>("newsId")

    private val _uiState = MutableStateFlow<NewsFormUiState>(NewsFormUiState.Idle)
    val uiState: StateFlow<NewsFormUiState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _isPublic = MutableStateFlow(true)
    val isPublic: StateFlow<Boolean> = _isPublic.asStateFlow()

    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    // NUEVO: Fotos adicionales
    private val _additionalPhotoUris = MutableStateFlow<List<Uri>>(emptyList())
    val additionalPhotoUris: StateFlow<List<Uri>> = _additionalPhotoUris.asStateFlow()

    private val _additionalPhotoUrls = MutableStateFlow<List<String>>(emptyList())
    val additionalPhotoUrls: StateFlow<List<String>> = _additionalPhotoUrls.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    val isEditMode = newsId != null && newsId != "new"

    companion object {
        const val MAX_ADDITIONAL_PHOTOS = 5
    }

    init {
        if (isEditMode && newsId != null) {
            loadNews(newsId)
        }
    }

    private fun loadNews(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = NewsFormUiState.Loading

                val doc = firestore.collection("news")
                    .document(id)
                    .get()
                    .await()

                if (doc.exists()) {
                    _title.value = doc.getString("title") ?: ""
                    _content.value = doc.getString("content") ?: ""
                    _imageUrl.value = doc.getString("imageUrl") ?: ""
                    _isPublic.value = doc.getBoolean("isPublic") ?: true

                    // NUEVO: Cargar fotos adicionales
                    val additionalPhotos = (doc.get("additionalPhotos") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                    _additionalPhotoUrls.value = additionalPhotos

                    _uiState.value = NewsFormUiState.Idle
                } else {
                    _uiState.value = NewsFormUiState.Error("Noticia no encontrada")
                }

            } catch (e: Exception) {
                _uiState.value = NewsFormUiState.Error("Error al cargar: ${e.message}")
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun onContentChange(newContent: String) {
        _content.value = newContent
    }

    fun onImageUrlChange(newUrl: String) {
        _imageUrl.value = newUrl
        if (newUrl.isNotBlank()) {
            _selectedPhotoUri.value = null
        }
    }

    fun onIsPublicChange(newIsPublic: Boolean) {
        _isPublic.value = newIsPublic
    }

    fun onPhotoSelected(uri: Uri) {
        _selectedPhotoUri.value = uri
        _imageUrl.value = ""
    }

    fun clearSelectedPhoto() {
        _selectedPhotoUri.value = null
    }

    // NUEVO: Añadir foto adicional
    fun onAdditionalPhotoSelected(uri: Uri) {
        val currentPhotos = _additionalPhotoUris.value
        if (currentPhotos.size < MAX_ADDITIONAL_PHOTOS) {
            _additionalPhotoUris.value = currentPhotos + uri
        }
    }

    // NUEVO: Eliminar foto adicional
    fun removeAdditionalPhoto(index: Int) {
        val currentPhotos = _additionalPhotoUris.value.toMutableList()
        if (index in currentPhotos.indices) {
            currentPhotos.removeAt(index)
            _additionalPhotoUris.value = currentPhotos
        }
    }

    // NUEVO: Eliminar foto adicional por URL (para edición)
    fun removeAdditionalPhotoUrl(url: String) {
        val currentUrls = _additionalPhotoUrls.value.toMutableList()
        currentUrls.remove(url)
        _additionalPhotoUrls.value = currentUrls
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return true
        return url.matches(Regex("^https?://.+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", RegexOption.IGNORE_CASE))
    }

    private suspend fun uploadPhotoToStorage(uri: Uri, folder: String = "news"): String {
        val photoId = UUID.randomUUID().toString()
        val fileName = "$photoId.jpg"
        val storagePath = "$folder/$fileName"

        val storageRef = storage.reference.child(storagePath)
        val uploadTask = storageRef.putFile(uri)

        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
            _uploadProgress.value = progress / 100f
        }

        uploadTask.await()
        return storageRef.downloadUrl.await().toString()
    }

    fun saveNews(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                if (_title.value.isBlank()) {
                    _uiState.value = NewsFormUiState.Error("El título es obligatorio")
                    return@launch
                }

                if (_content.value.isBlank()) {
                    _uiState.value = NewsFormUiState.Error("El contenido es obligatorio")
                    return@launch
                }

                if (_imageUrl.value.isNotBlank() && !isValidImageUrl(_imageUrl.value)) {
                    _uiState.value = NewsFormUiState.Error("URL de imagen inválida")
                    return@launch
                }

                _uiState.value = NewsFormUiState.Saving
                _uploadProgress.value = 0f

                // Upload foto principal
                var finalImageUrl = _imageUrl.value
                if (_selectedPhotoUri.value != null) {
                    finalImageUrl = uploadPhotoToStorage(_selectedPhotoUri.value!!)
                }

                // NUEVO: Upload fotos adicionales
                val uploadedAdditionalPhotos = mutableListOf<String>()

                // Mantener URLs existentes
                uploadedAdditionalPhotos.addAll(_additionalPhotoUrls.value)

                // Upload nuevas fotos
                _additionalPhotoUris.value.forEachIndexed { index, uri ->
                    _uploadProgress.value = (index + 1).toFloat() / (_additionalPhotoUris.value.size + 1)
                    val url = uploadPhotoToStorage(uri, "news/additional")
                    uploadedAdditionalPhotos.add(url)
                }

                val newsData = hashMapOf(
                    "title" to _title.value,
                    "content" to _content.value,
                    "imageUrl" to finalImageUrl.ifBlank { null },
                    "publishedDate" to Timestamp.now(),
                    "isPublic" to _isPublic.value,
                    "additionalPhotos" to uploadedAdditionalPhotos  // NUEVO
                )

                if (isEditMode && newsId != null) {
                    firestore.collection("news")
                        .document(newsId)
                        .update(newsData as Map<String, Any>)
                        .await()
                } else {
                    firestore.collection("news")
                        .add(newsData)
                        .await()
                }

                _uiState.value = NewsFormUiState.Saved
                onSuccess()

            } catch (e: Exception) {
                _uiState.value = NewsFormUiState.Error("Error al guardar: ${e.message}")
            }
        }
    }

    fun clearError() {
        if (_uiState.value is NewsFormUiState.Error) {
            _uiState.value = NewsFormUiState.Idle
        }
    }
}
