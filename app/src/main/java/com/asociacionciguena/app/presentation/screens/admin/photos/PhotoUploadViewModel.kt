package com.asociacionciguena.app.presentation.screens.admin.photos

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
import kotlinx.coroutines.flow.collect

@HiltViewModel
class PhotoUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private var activeWorkId: UUID? = null

    private val preselectedExcursionId: String? = savedStateHandle["excursionId"]
    private val _uiState = MutableStateFlow<PhotoUploadUiState>(PhotoUploadUiState.Idle)
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _excursions = MutableStateFlow<List<ExcursionOption>>(emptyList())
    val excursions: StateFlow<List<ExcursionOption>> = _excursions.asStateFlow()

    init {
        loadExcursions()
        observeUploadWork()
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

    fun uploadPhotos() {
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

                val batchId = UUID.randomUUID().toString()
                val stagedFiles = stageSelectedMedia(currentState.uris, batchId)
                val data = Data.Builder()
                    .putString(PhotoUploadWorker.KEY_EXCURSION_ID, selectedExcursionId)
                    .putString(PhotoUploadWorker.KEY_UPLOADER_ID, uploaderId)
                    .putString(PhotoUploadWorker.KEY_BATCH_ID, batchId)
                    .putStringArray(PhotoUploadWorker.KEY_FILE_PATHS, stagedFiles.toTypedArray())
                    .putStringArray(PhotoUploadWorker.KEY_MIME_TYPES, currentState.uris.map {
                        context.contentResolver.getType(it).orEmpty()
                    }.toTypedArray())
                    .build()
                val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                    .setInputData(data)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .addTag(PhotoUploadWorker.TAG)
                    .build()
                activeWorkId = request.id
                _uiState.value = PhotoUploadUiState.Uploading(0f, 1, stagedFiles.size)
                workManager.enqueueUniqueWork("photo_upload_$batchId", ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                _uiState.value = PhotoUploadUiState.Error(
                    message = "Error al subir: ${e.message}"
                )
            }
        }
    }

    private fun observeUploadWork() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(PhotoUploadWorker.TAG).collect { workInfos ->
                val info = activeWorkId?.let { id -> workInfos.firstOrNull { it.id == id } }
                    ?: workInfos.firstOrNull { !it.state.isFinished }
                    ?: workInfos.lastOrNull()
                    ?: return@collect
                activeWorkId = info.id
                when (info.state) {
                    androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.RUNNING, androidx.work.WorkInfo.State.BLOCKED -> {
                        val progress = info.progress.getFloat(PhotoUploadWorker.KEY_PROGRESS, 0f)
                        val total = info.progress.getInt(PhotoUploadWorker.KEY_TOTAL, 1).coerceAtLeast(1)
                        val current = info.progress.getInt(PhotoUploadWorker.KEY_CURRENT, 1).coerceIn(1, total)
                        _uiState.value = PhotoUploadUiState.Uploading(progress, current, total)
                    }
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        _uiState.value = PhotoUploadUiState.Success
                        delay(1500)
                        if (activeWorkId == info.id) _uiState.value = PhotoUploadUiState.Idle
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        _uiState.value = PhotoUploadUiState.Error(info.outputData.getString(PhotoUploadWorker.KEY_ERROR) ?: "Error al subir los archivos")
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun stageSelectedMedia(uris: List<Uri>, batchId: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val batchDir = java.io.File(context.filesDir, "pending-photo-uploads/$batchId")
        if (!batchDir.mkdirs() && !batchDir.isDirectory) error("No se pudo preparar la subida")
        val staged = mutableListOf<String>()
        try {
            uris.forEachIndexed { index, uri ->
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                require(mimeType.matches(Regex("image/.*|video/.*"))) { "Tipo de archivo no soportado: $mimeType" }
                val target = java.io.File(batchDir, "media_$index")
                val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo leer un archivo seleccionado")
                input.use { source ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 100L * 1024L * 1024L) { "El archivo supera el limite de 100MB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                staged += target.absolutePath
            }
            staged
        } catch (e: Exception) {
            batchDir.deleteRecursively()
            throw e
        }
    }

    fun clearError() {
        if (_uiState.value is PhotoUploadUiState.Error) {
            _uiState.value = PhotoUploadUiState.Idle
        }
    }

    fun reset() {
        if (_uiState.value !is PhotoUploadUiState.Uploading) _uiState.value = PhotoUploadUiState.Idle
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
