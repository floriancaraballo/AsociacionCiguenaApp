package com.asociacionciguena.app.presentation.screens.admin.excursions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
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
class ExcursionManagementViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExcursionManagementUiState>(ExcursionManagementUiState.Loading)
    val uiState: StateFlow<ExcursionManagementUiState> = _uiState.asStateFlow()

    init {
        loadExcursions()
    }

    fun loadExcursions() {
        viewModelScope.launch {
            try {
                _uiState.value = ExcursionManagementUiState.Loading

                val snapshot = firestore.collection("excursions")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                val excursionsList = snapshot.documents.mapNotNull { doc ->
                    try {
                        Excursion(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            date = doc.getTimestamp("date")?.let {
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            } ?: kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                            location = doc.getString("location") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            // SIN maxParticipants y currentParticipants
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                _uiState.value = ExcursionManagementUiState.Success(excursionsList)

            } catch (e: Exception) {
                _uiState.value = ExcursionManagementUiState.Error(
                    message = "Error al cargar excursiones: ${e.message}"
                )
            }
        }
    }

    fun deleteExcursion(excursionId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("excursions")
                    .document(excursionId)
                    .delete()
                    .await()

                onSuccess()
                loadExcursions()

            } catch (e: Exception) {
                _uiState.value = ExcursionManagementUiState.Error(
                    message = "Error al eliminar: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadExcursions()
    }
}
