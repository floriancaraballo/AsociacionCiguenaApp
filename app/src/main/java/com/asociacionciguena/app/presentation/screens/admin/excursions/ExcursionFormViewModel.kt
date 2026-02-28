package com.asociacionciguena.app.presentation.screens.admin.excursions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
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
import javax.inject.Inject

@HiltViewModel
class ExcursionFormViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    savedStateHandle: SavedStateHandle
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

    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _date = MutableStateFlow<LocalDateTime?>(null)
    val date: StateFlow<LocalDateTime?> = _date.asStateFlow()

    // ELIMINADO: maxParticipants

    val isEditMode = excursionId != null && excursionId != "new"

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
                    _imageUrl.value = doc.getString("imageUrl") ?: ""

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

    fun onImageUrlChange(newUrl: String) {
        _imageUrl.value = newUrl
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

    fun saveExcursion(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
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

                if (_imageUrl.value.isNotBlank() && !isValidImageUrl(_imageUrl.value)) {
                    _uiState.value = ExcursionFormUiState.Error(
                        "URL de imagen inválida"
                    )
                    return@launch
                }

                _uiState.value = ExcursionFormUiState.Saving

                val timestamp = _date.value?.let { localDateTime ->
                    val instant = localDateTime.toInstant(TimeZone.currentSystemDefault())
                    Timestamp(Date(instant.toEpochMilliseconds()))
                } ?: Timestamp.now()

                val excursionData = hashMapOf(
                    "title" to _title.value,
                    "description" to _description.value,
                    "location" to _location.value,
                    "date" to timestamp,
                    "imageUrl" to _imageUrl.value.ifBlank { null }
                )

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

    fun clearError() {
        if (_uiState.value is ExcursionFormUiState.Error) {
            _uiState.value = ExcursionFormUiState.Idle
        }
    }
}
