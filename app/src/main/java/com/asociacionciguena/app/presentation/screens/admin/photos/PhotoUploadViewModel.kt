package com.asociacionciguena.app.presentation.screens.admin.photos

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.util.ImageCompressor
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@HiltViewModel
class PhotoUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val preselectedExcursionId: String? = savedStateHandle["excursionId"]
    private val _uiState = MutableStateFlow<PhotoUploadUiState>(PhotoUploadUiState.Idle)
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _excursions = MutableStateFlow<List<ExcursionOption>>(emptyList())
    val excursions: StateFlow<List<ExcursionOption>> = _excursions.asStateFlow()

    init {
        loadExcursions()
    }

    private fun loadExcursions() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("excursions")
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                _excursions.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        ExcursionOption(
                            id = doc.id,
                            title = doc.getString("title") ?: ""
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
                // Keep the current list; upload validation will still require a selected excursion.
            }
        }
    }

    fun onPhotoSelected(uri: Uri) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = listOf(uri),
            selectedExcursionId = preselectedExcursionId
        )
    }

    fun onMediaSelected(uris: List<Uri>) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = uris,
            selectedExcursionId = preselectedExcursionId
        )
    }

    fun addMorePhotos(newUris: List<Uri>) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            _uiState.value = currentState.copy(
                uris = currentState.uris + newUris
            )
        }
    }

    fun removePhoto(uri: Uri) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            val updatedUris = currentState.uris.filter { it != uri }
            _uiState.value = if (updatedUris.isEmpty()) {
                PhotoUploadUiState.Idle
            } else {
                currentState.copy(uris = updatedUris)
            }
        }
    }

    fun onExcursionSelected(excursionId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            _uiState.value = currentState.copy(selectedExcursionId = excursionId)
        }
    }

    fun uploadPhotos(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                if (!networkMonitor.isCurrentlyOnline()) {
                    _uiState.value = PhotoUploadUiState.Error(
                        "Sin conexion a internet. No se pueden subir fotos."
                    )
                    return@launch
                }

                val uploaderId = auth.currentUser?.uid
                if (uploaderId == null || !canManageExcursionMedia(uploaderId)) {
                    _uiState.value = PhotoUploadUiState.Error(
                        "No tienes permisos para subir fotos"
                    )
                    return@launch
                }

                val currentState = _uiState.value
                if (currentState !is PhotoUploadUiState.PhotosSelected) {
                    _uiState.value = PhotoUploadUiState.Error("Estado invalido")
                    return@launch
                }

                val selectedExcursionId = currentState.selectedExcursionId
                if (selectedExcursionId == null) {
                    _uiState.value = PhotoUploadUiState.Error("Selecciona una excursion")
                    return@launch
                }

                val totalPhotos = currentState.uris.size
                val batchId = UUID.randomUUID().toString()
                val batchData = hashMapOf(
                    "excursionId" to selectedExcursionId,
                    "photoCount" to totalPhotos,
                    "status" to "uploading",
                    "createdAt" to Timestamp.now()
                )

                firestore.collection("uploadBatches")
                    .document(batchId)
                    .set(batchData)
                    .await()

                currentState.uris.forEachIndexed { index, uri ->
                    _uiState.value = PhotoUploadUiState.Uploading(
                        progress = index.toFloat() / totalPhotos,
                        currentPhotoIndex = index + 1,
                        totalPhotos = totalPhotos
                    )

                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isVideo = mimeType.startsWith("video/")
                    if (!mimeType.matches(Regex("image/.*|video/.*"))) {
                        _uiState.value = PhotoUploadUiState.Error("Tipo de archivo no soportado: $mimeType")
                        return@launch
                    }

                    val fileSize = context.contentResolver.openInputStream(uri)?.use { it.available() } ?: 0
                    val sizeInMb = fileSize / (1024 * 1024)
                    if (sizeInMb > 100) {
                        _uiState.value = PhotoUploadUiState.Error("El archivo supera el limite de 100MB")
                        return@launch
                    }

                    val photoId = UUID.randomUUID().toString()
                    val extension = if (isVideo) "mp4" else "jpg"
                    val storagePath = "excursions/$selectedExcursionId/$photoId.$extension"

                    val downloadUrl: String
                    val thumbnailUrl: String?

                    if (isVideo) {
                        val storageRef = storage.reference.child(storagePath)
                        val metadata = StorageMetadata.Builder()
                            .setContentType("video/mp4")
                            .setCacheControl("public, max-age=31536000")
                            .build()

                        storageRef.putFile(uri, metadata).await()
                        downloadUrl = storageRef.downloadUrl.await().toString()
                        thumbnailUrl = uploadVideoThumbnail(selectedExcursionId, photoId, uri)
                    } else {
                        val compressedFile = ImageCompressor.compressPhoto(context, uri)
                        val storageRef = storage.reference.child(storagePath)

                        storageRef.putFile(Uri.fromFile(compressedFile)).await()
                        downloadUrl = storageRef.downloadUrl.await().toString()
                        compressedFile.delete()
                        thumbnailUrl = null
                    }

                    val photoData = hashMapOf(
                        "id" to photoId,
                        "excursionId" to selectedExcursionId,
                        "imageUrl" to downloadUrl,
                        "storagePath" to storagePath,
                        "uploadedBy" to uploaderId,
                        "uploadedAt" to Timestamp.now(),
                        "authorizedUsers" to emptyList<String>(),
                        "batchId" to batchId,
                        "mediaType" to if (isVideo) "video" else "image",
                        "thumbnailUrl" to thumbnailUrl
                    )

                    firestore.collection("photos")
                        .document(photoId)
                        .set(photoData)
                        .await()
                }

                firestore.collection("uploadBatches")
                    .document(batchId)
                    .update(
                        mapOf(
                            "status" to "completed",
                            "completedAt" to Timestamp.now()
                        )
                    )
                    .await()

                _uiState.value = PhotoUploadUiState.Success
                onSuccess()

                viewModelScope.launch {
                    delay(1500)
                    _uiState.value = PhotoUploadUiState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = PhotoUploadUiState.Error(
                    message = "Error al subir: ${e.message}"
                )
            }
        }
    }

    private suspend fun uploadVideoThumbnail(
        excursionId: String,
        photoId: String,
        uri: Uri
    ): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            val thumbnailBitmap = try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            } finally {
                retriever.release()
            } ?: return null

            val thumbnailPath = "excursions/$excursionId/thumbnails/$photoId.jpg"
            val thumbnailRef = storage.reference.child(thumbnailPath)
            val output = java.io.ByteArrayOutputStream()

            thumbnailBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
            thumbnailRef.putBytes(output.toByteArray()).await()
            thumbnailRef.downloadUrl.await().toString()
        } catch (_: Exception) {
            null
        }
    }

    fun clearError() {
        if (_uiState.value is PhotoUploadUiState.Error) {
            _uiState.value = PhotoUploadUiState.Idle
        }
    }

    fun reset() {
        _uiState.value = PhotoUploadUiState.Idle
    }

    private suspend fun canManageExcursionMedia(userId: String): Boolean {
        val role = firestore.collection("users")
            .document(userId)
            .get()
            .await()
            .getString("role")
        return role == "admin" || role == "superadmin" || role == "monitor"
    }
}

data class ExcursionOption(
    val id: String,
    val title: String
)
