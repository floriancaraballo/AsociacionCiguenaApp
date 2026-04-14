package com.asociacionciguena.app.presentation.screens.admin.photos

import android.net.Uri
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
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asociacionciguena.app.util.ImageCompressor
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.storage.StorageMetadata

@HiltViewModel
class PhotoUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val preselectedExcursionId: String? = savedStateHandle["excursionId"]
    private val _uiState = MutableStateFlow<PhotoUploadUiState>(PhotoUploadUiState.Idle)
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _excursions = MutableStateFlow<List<ExcursionOption>>(emptyList())
    val excursions: StateFlow<List<ExcursionOption>> = _excursions.asStateFlow()

    private val _users = MutableStateFlow<List<UserOption>>(emptyList())
    val users: StateFlow<List<UserOption>> = _users.asStateFlow()

    init {
        loadExcursions()
        loadUsers()

        // Pre-seleccionar excursión si viene por parámetro
        preselectedExcursionId?.let { excId ->
            // Esperamos a que se seleccione una foto
            // El estado se actualizará en onPhotoSelected
        }
    }

    /**
     * Cargar excursiones disponibles
     */
    private fun loadExcursions() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("excursions")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                _excursions.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        ExcursionOption(
                            id = doc.id,
                            title = doc.getString("title") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Cargar usuarios disponibles
     */
    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users")
                    .get()
                    .await()

                _users.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        UserOption(
                            id = doc.id,
                            displayName = doc.getString("displayName") ?: "Usuario",
                            email = doc.getString("email") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Usuario selecciona una foto
     */
    /**
     * Usuario selecciona UNA foto
     */
    fun onPhotoSelected(uri: Uri) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = listOf(uri),
            selectedExcursionId = preselectedExcursionId
        )
    }

    /**
     * Usuario selecciona MÚLTIPLES fotos
     */
    fun onMediaSelected(uris: List<Uri>) {
        _uiState.value = PhotoUploadUiState.PhotosSelected(
            uris = uris,
            selectedExcursionId = preselectedExcursionId
        )
    }

    /**
     * Añadir más fotos a la selección actual
     */
    fun addMorePhotos(newUris: List<Uri>) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            _uiState.value = currentState.copy(
                uris = currentState.uris + newUris
            )
        }
    }

    /**
     * Eliminar una foto de la selección
     */
    fun removePhoto(uri: Uri) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {
            val updatedUris = currentState.uris.filter { it != uri }
            if (updatedUris.isEmpty()) {
                _uiState.value = PhotoUploadUiState.Idle
            } else {
                _uiState.value = currentState.copy(uris = updatedUris)
            }
        }
    }

    /**
     * Seleccionar excursión
     */
    fun onExcursionSelected(excursionId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            _uiState.value = currentState.copy(selectedExcursionId = excursionId)
        }
    }

    /**
     * Toggle selección de usuario
     */
    fun onUserToggled(userId: String) {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            val currentUsers = currentState.selectedUsers.toMutableList()
            if (userId in currentUsers) {
                currentUsers.remove(userId)
            } else {
                currentUsers.add(userId)
            }
            _uiState.value = currentState.copy(selectedUsers = currentUsers)
        }
    }

    /**
     * Subir foto a Firebase Storage
     */
    /**
     * Subir MÚLTIPLES fotos a Firebase Storage
     */
    fun uploadPhotos(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                if (!networkMonitor.isCurrentlyOnline()) {
                    _uiState.value = PhotoUploadUiState.Error("Sin conexión a internet. No se pueden subir fotos.")
                    return@launch
                }

                val currentState = _uiState.value
                if (currentState !is PhotoUploadUiState.PhotosSelected) {
                    _uiState.value = PhotoUploadUiState.Error("Estado inválido")
                    return@launch
                }

                // Validaciones
                if (currentState.selectedExcursionId == null) {
                    _uiState.value = PhotoUploadUiState.Error("Selecciona una excursión")
                    return@launch
                }

                val totalPhotos = currentState.uris.size

                // ← NUEVO: Crear documento de batch
                val batchId = UUID.randomUUID().toString()
                val batchData = hashMapOf(
                    "excursionId" to currentState.selectedExcursionId,
                    "photoCount" to totalPhotos,
                    "status" to "uploading",
                    "authorizedUsers" to emptyList<String>(),
                    "createdAt" to Timestamp.now()
                )

                firestore.collection("uploadBatches")
                    .document(batchId)
                    .set(batchData)
                    .await()

                // Subir cada media (foto o vídeo)
                currentState.uris.forEachIndexed { index, uri ->
                    _uiState.value = PhotoUploadUiState.Uploading(
                        progress = index.toFloat() / totalPhotos,
                        currentPhotoIndex = index + 1,
                        totalPhotos = totalPhotos
                    )

                    // ✅ Detectar tipo de media
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isVideo = mimeType.startsWith("video/")

                    android.util.Log.d("MEDIA_UPLOAD", "URI: $uri, MIME: $mimeType, isVideo: $isVideo")

                    // Generar nombre único
                    val photoId = UUID.randomUUID().toString()
                    val extension = if (isVideo) "mp4" else "jpg"
                    val fileName = "$photoId.$extension"
                    val storagePath = "excursions/${currentState.selectedExcursionId}/$fileName"

                    android.util.Log.d("UPLOAD_DEBUG", "════════════════════════════════")
                    android.util.Log.d("UPLOAD_DEBUG", "📤 Intentando subir media")
                    android.util.Log.d("UPLOAD_DEBUG", "   photoId: $photoId")
                    android.util.Log.d("UPLOAD_DEBUG", "   extension: $extension")
                    android.util.Log.d("UPLOAD_DEBUG", "   fileName: $fileName")
                    android.util.Log.d("UPLOAD_DEBUG", "   storagePath: $storagePath")
                    android.util.Log.d("UPLOAD_DEBUG", "   excursionId: ${currentState.selectedExcursionId}")
                    android.util.Log.d("UPLOAD_DEBUG", "   mimeType: $mimeType")
                    android.util.Log.d("UPLOAD_DEBUG", "   isVideo: $isVideo")
                    android.util.Log.d("UPLOAD_DEBUG", "════════════════════════════════")

                    val downloadUrl: String
                    val thumbnailUrl: String?

                    if (isVideo) {

                        // ✅ PROCESAR VÍDEO
                        // Subir vídeo directamente (sin compresión por ahora)

                        android.util.Log.d("UPLOAD_DEBUG", "🎬 Subiendo vídeo...")
                        android.util.Log.d("UPLOAD_DEBUG", "   Full path: ${storage.reference.child(storagePath).path}")

                        // ✅ Log para debuggear permisos
                        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        android.util.Log.d("UPLOAD_DEBUG", "🔐 Usuario actual: ${currentUser?.uid ?: "null"}")
                        android.util.Log.d("UPLOAD_DEBUG", "🔐 Email: ${currentUser?.email ?: "null"}")

                        // En PhotoUploadViewModel.kt, justo antes de storageRef.putFile():

                        val mimeType = context.contentResolver.getType(uri) ?: ""
                        val fileSize = context.contentResolver.openInputStream(uri)?.use { it.available() } ?: 0
                        val sizeInMB = fileSize / (1024 * 1024)

                        android.util.Log.d("UPLOAD_DEBUG", "🔍 MIME type: '$mimeType'")
                        android.util.Log.d("UPLOAD_DEBUG", "🔍 Tamaño: $sizeInMB MB")

                        // Validaciones explícitas
                        if (!mimeType.matches(Regex("image/.*|video/.*"))) {
                            _uiState.value = PhotoUploadUiState.Error("Tipo de archivo no soportado: $mimeType")
                            return@launch
                        }

                        if (sizeInMB > 100) {
                            _uiState.value = PhotoUploadUiState.Error("El archivo supera el límite de 100MB")
                            return@launch
                        }

                        val storageRef = storage.reference.child(storagePath)

                        // ✅ NUEVO: Configurar metadata para vídeo
                        val metadata = StorageMetadata.Builder()
                            .setContentType("video/mp4")  // ← ¡CRUCIAL para que se reproduzca!
                            .setCacheControl("public, max-age=31536000")  // ← Opcional: caching
                            .build()

                        storageRef.putFile(uri,metadata).await()

                        android.util.Log.d("UPLOAD_DEBUG", "✅ Vídeo subido correctamente")
                        downloadUrl = storageRef.downloadUrl.await().toString()

                        // ✅ GENERAR MINIATURA DEL VÍDEO (CORREGIDO)
                        thumbnailUrl = try {
                            // ✅ PASO 1: Convertir Uri a String path
                            val uriPath = uri.toString()

                            // ✅ PASO 2: Usar la sobrecarga que acepta String
                            val thumbnailBitmap = android.media.ThumbnailUtils.createVideoThumbnail(
                                uriPath,  // ← ✅ String path (NO Uri)
                                android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                            )

                            if (thumbnailBitmap != null) {
                                // Guardar miniatura en Storage
                                val thumbnailPath = "excursions/${currentState.selectedExcursionId}/thumbnails/$photoId.jpg"
                                val thumbnailRef = storage.reference.child(thumbnailPath)

                                val baos = java.io.ByteArrayOutputStream()
                                thumbnailBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                                val thumbnailBytes = baos.toByteArray()

                                thumbnailRef.putBytes(thumbnailBytes).await()
                                thumbnailRef.downloadUrl.await().toString()
                            } else {
                                android.util.Log.w("THUMBNAIL", "⚠️ thumbnailBitmap es null")
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("THUMBNAIL", "❌ Error generando miniatura: ${e.message}", e)
                            null
                        }

                    } else {
                        // ✅ PROCESAR IMAGEN (como antes)
                        val compressedFile = ImageCompressor.compressPhoto(context, uri)

                        val storageRef = storage.reference.child(storagePath)
                        storageRef.putFile(Uri.fromFile(compressedFile)).await()

                        downloadUrl = storageRef.downloadUrl.await().toString()
                        compressedFile.delete()
                        thumbnailUrl = null  // Las imágenes no necesitan thumbnail separado
                    }

                    // ✅ Guardar en Firestore con mediaType
                    val photoData = hashMapOf(
                        "id" to photoId,
                        "excursionId" to currentState.selectedExcursionId,
                        "imageUrl" to downloadUrl,
                        "storagePath" to storagePath,
                        "uploadedBy" to "admin",
                        "uploadedAt" to Timestamp.now(),
                        "authorizedUsers" to emptyList<String>(),
                        "batchId" to batchId,
                        "mediaType" to if (isVideo) "video" else "image",  // ✅ NUEVO
                        "thumbnailUrl" to thumbnailUrl  // ✅ NUEVO
                    )

                    firestore.collection("photos")
                        .document(photoId)
                        .set(photoData)
                        .await()
                }

                // ← NUEVO: Marcar batch como completado
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

                // Pequeño delay y volver a Idle
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
    /**
     * Seleccionar todos los socios
     */
    fun selectAllUsers() {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            val allSocioIds = _users.value.map { it.id }
            _uiState.value = currentState.copy(selectedUsers = allSocioIds)
        }
    }

    fun deselectAllUsers() {
        val currentState = _uiState.value
        if (currentState is PhotoUploadUiState.PhotosSelected) {  // ← Cambio
            _uiState.value = currentState.copy(selectedUsers = emptyList())
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
}

/**
 * Data classes auxiliares
 */
data class ExcursionOption(
    val id: String,
    val title: String
)

data class UserOption(
    val id: String,
    val displayName: String,
    val email: String
)