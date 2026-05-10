package com.asociacionciguena.app.presentation.screens.admin.excursions

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import android.content.Context
import com.asociacionciguena.app.util.ImageCompressor
import com.asociacionciguena.app.util.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class ExcursionFormViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,  // ← AÑADIDO
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val excursionId: String? = savedStateHandle.get<String>("excursionId")

    private val _uiState = MutableStateFlow<ExcursionFormUiState>(ExcursionFormUiState.Idle)
    val uiState: StateFlow<ExcursionFormUiState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location.asStateFlow()

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price.asStateFlow()

    private val _maxParticipants = MutableStateFlow("")
    val maxParticipants: StateFlow<String> = _maxParticipants.asStateFlow()

    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _uploadedImageUrl = MutableStateFlow<String?>(null)  // ← NUEVO
    val uploadedImageUrl: StateFlow<String?> = _uploadedImageUrl.asStateFlow()

    private val _date = MutableStateFlow<LocalDateTime?>(null)
    val date: StateFlow<LocalDateTime?> = _date.asStateFlow()

    // ← NUEVO: Estado del PDF de autorización
    private val _authorizationPdfUrl = MutableStateFlow<String?>(null)
    val authorizationPdfUrl: StateFlow<String?> = _authorizationPdfUrl.asStateFlow()

    private val _pdfUploadState = MutableStateFlow<PdfUploadState>(PdfUploadState.Idle)
    val pdfUploadState: StateFlow<PdfUploadState> = _pdfUploadState.asStateFlow()

    val isEditMode = excursionId != null && excursionId != "new"

    // ← NUEVO: Estado de subida de imagen
    private val _imageUploadState = MutableStateFlow<ImageUploadState>(ImageUploadState.Idle)
    val imageUploadState: StateFlow<ImageUploadState> = _imageUploadState.asStateFlow()


    init {
        if (isEditMode && excursionId != null) {
            loadExcursion(excursionId)
        }
    }

    private fun loadExcursion(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ExcursionFormUiState.Loading

                val doc = firestore.collection("excursions")
                    .document(id)
                    .get()
                    .await()

                if (doc.exists()) {
                    _title.value = doc.getString("title") ?: ""
                    _description.value = doc.getString("description") ?: ""
                    _location.value = doc.getString("location") ?: ""
                    _price.value = doc.getDouble("price")?.toString() ?: ""
                    _maxParticipants.value = doc.getLong("maxParticipants")?.toString() ?: ""
                    _imageUrl.value = doc.getString("imageUrl") ?: ""
                    _authorizationPdfUrl.value = doc.getString("authorizationPdfUrl")  // ← NUEVO

                    _date.value = doc.getTimestamp("date")?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                    }

                    _uiState.value = ExcursionFormUiState.Idle
                } else {
                    _uiState.value = ExcursionFormUiState.Error("Excursión no encontrada")
                }

            } catch (e: Exception) {
                _uiState.value = ExcursionFormUiState.Error("Error al cargar: ${e.message}")
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun onDescriptionChange(newDescription: String) {
        _description.value = newDescription
    }

    fun onLocationChange(newLocation: String) {
        _location.value = newLocation
    }

    fun onPriceChange(newPrice: String) {
        // Solo permitir números y un punto decimal
        if (newPrice.isEmpty() || newPrice.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _price.value = newPrice
        }
    }

    fun onImageUrlChange(newUrl: String) {
        _imageUrl.value = newUrl
    }

    fun onMaxParticipantsChange(newMaxParticipants: String) {
        if (newMaxParticipants.isEmpty() || newMaxParticipants.matches(Regex("^\\d{0,4}$"))) {
            _maxParticipants.value = newMaxParticipants
        }
    }

    fun onDateChange(newDate: LocalDateTime) {
        _date.value = newDate
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return true
        return url.matches(
            Regex(
                "^https?://.+?(\\.(jpg|jpeg|png|gif|webp))?(\\?.*)?$",
                RegexOption.IGNORE_CASE
            )
        )
    }

    // ← NUEVO: Subir PDF de autorización
    fun uploadAuthorizationPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                _pdfUploadState.value = PdfUploadState.Uploading(0f)

                // Generar nombre único
                val pdfId = UUID.randomUUID().toString()
                val fileName = "authorization_${pdfId}.pdf"
                val storagePath = "excursions/authorizations/$fileName"

                // Subir a Storage
                val storageRef = storage.reference.child(storagePath)
                val uploadTask = storageRef.putFile(uri)

                // Monitorear progreso
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                    _pdfUploadState.value = PdfUploadState.Uploading(progress / 100f)
                }

                // Esperar a que termine
                uploadTask.await()

                // Obtener URL de descarga
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Guardar URL
                _authorizationPdfUrl.value = downloadUrl
                _pdfUploadState.value = PdfUploadState.Success

            } catch (e: Exception) {
                _pdfUploadState.value = PdfUploadState.Error("Error al subir PDF: ${e.message}")
            }
        }
    }

    // ← NUEVO: Eliminar PDF de autorización
    fun removeAuthorizationPdf() {
        _authorizationPdfUrl.value = null
        _pdfUploadState.value = PdfUploadState.Idle
    }

    fun clearPdfUploadError() {
        if (_pdfUploadState.value is PdfUploadState.Error) {
            _pdfUploadState.value = PdfUploadState.Idle
        }
    }

    fun saveExcursion(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Verificar conexión
                if (!networkMonitor.isCurrentlyOnline()) {
                    _uiState.value = ExcursionFormUiState.Error("Sin conexión a internet. No se puede guardar.")
                    return@launch
                }

                if (!isCurrentUserAdmin()) {
                    _uiState.value = ExcursionFormUiState.Error("Solo administradores pueden guardar excursiones")
                    return@launch
                }

                if (_title.value.isBlank()) {
                    _uiState.value = ExcursionFormUiState.Error("El título es obligatorio")
                    return@launch
                }

                if (_description.value.isBlank()) {
                    _uiState.value = ExcursionFormUiState.Error("La descripción es obligatoria")
                    return@launch
                }

                if (_location.value.isBlank()) {
                    _uiState.value = ExcursionFormUiState.Error("La ubicación es obligatoria")
                    return@launch
                }

                if (_date.value == null) {
                    _uiState.value = ExcursionFormUiState.Error("La fecha es obligatoria")
                    return@launch
                }

                val maxParticipantsValue = _maxParticipants.value.toIntOrNull()
                if (maxParticipantsValue == null || maxParticipantsValue <= 0) {
                    _uiState.value = ExcursionFormUiState.Error("El máximo de participantes debe ser mayor que 0")
                    return@launch
                }

                if (_imageUrl.value.isNotBlank() && !isValidImageUrl(_imageUrl.value)) {
                    _uiState.value = ExcursionFormUiState.Error("URL de imagen inválida")
                    return@launch
                }

                _uiState.value = ExcursionFormUiState.Saving

                val timestamp = _date.value?.let { localDateTime ->
                    val instant = localDateTime.toInstant(TimeZone.currentSystemDefault())
                    Timestamp(Date(instant.toEpochMilliseconds()))
                } ?: Timestamp.now()

                val excursionData = hashMapOf<String, Any?>(
                    "title" to _title.value,
                    "description" to _description.value,
                    "location" to _location.value,
                    "date" to timestamp,
                    "imageUrl" to _imageUrl.value.ifBlank { null },
                    "authorizationPdfUrl" to _authorizationPdfUrl.value,
                    "maxParticipants" to maxParticipantsValue
                )

// Solo añadir precio si tiene valor
                val priceValue = _price.value.toDoubleOrNull()
                if (priceValue != null && priceValue > 0) {
                    excursionData["price"] = priceValue
                }

                if (isEditMode && excursionId != null) {
                    firestore.collection("excursions")
                        .document(excursionId)
                        .update(excursionData as Map<String, Any>)
                        .await()
                } else {
                    firestore.collection("excursions")
                        .add(excursionData)
                        .await()
                }

                _uiState.value = ExcursionFormUiState.Saved
                onSuccess()

            } catch (e: Exception) {
                _uiState.value = ExcursionFormUiState.Error("Error al guardar: ${e.message}")
            }
        }
    }

    fun uploadExcursionImage(uri: Uri) {
        viewModelScope.launch {
            try {
                _imageUploadState.value = ImageUploadState.Uploading(0f)

                // COMPRIMIR antes de subir
                val compressedFile = ImageCompressor.compressPhoto(context, uri)

                val imageId = UUID.randomUUID().toString()
                val fileName = "excursion_$imageId.jpg"
                val storagePath = "excursions/images/$fileName"

                val storageRef = storage.reference.child(storagePath)
                val uploadTask = storageRef.putFile(Uri.fromFile(compressedFile))

                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                    _imageUploadState.value = ImageUploadState.Uploading(progress / 100f)
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Limpiar archivo temporal
                compressedFile.delete()

                _uploadedImageUrl.value = downloadUrl
                _imageUrl.value = downloadUrl
                _imageUploadState.value = ImageUploadState.Success

            } catch (e: Exception) {
                _imageUploadState.value = ImageUploadState.Error(e.message ?: "Error al subir imagen")
            }
        }
    }

    // ← NUEVO: Eliminar imagen
    fun removeExcursionImage() {
        _imageUrl.value = ""
        _imageUploadState.value = ImageUploadState.Idle
    }

    fun clearImageUploadError() {
        if (_imageUploadState.value is ImageUploadState.Error) {
            _imageUploadState.value = ImageUploadState.Idle
        }
    }

    fun clearError() {
        if (_uiState.value is ExcursionFormUiState.Error) {
            _uiState.value = ExcursionFormUiState.Idle
        }
    }

    private suspend fun isCurrentUserAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val userDoc = firestore.collection("users").document(uid).get().await()
        val role = userDoc.getString("role")
        return role == "admin" || role == "superadmin"
    }
}

// ← NUEVO: Estados de subida de PDF
sealed class PdfUploadState {
    object Idle : PdfUploadState()
    data class Uploading(val progress: Float) : PdfUploadState()
    object Success : PdfUploadState()
    data class Error(val message: String) : PdfUploadState()
}

sealed class ImageUploadState {
    object Idle : ImageUploadState()
    data class Uploading(val progress: Float) : ImageUploadState()
    object Success : ImageUploadState()
    data class Error(val message: String) : ImageUploadState()
}
