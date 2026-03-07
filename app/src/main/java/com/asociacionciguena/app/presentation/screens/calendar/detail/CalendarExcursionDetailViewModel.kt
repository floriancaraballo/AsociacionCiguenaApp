package com.asociacionciguena.app.presentation.screens.calendar.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class CalendarExcursionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val excursionId: String = checkNotNull(savedStateHandle["excursionId"])

    private val _uiState = MutableStateFlow<CalendarExcursionDetailUiState>(
        CalendarExcursionDetailUiState.Loading
    )
    val uiState: StateFlow<CalendarExcursionDetailUiState> = _uiState.asStateFlow()

    init {
        loadExcursionDetail()
    }

    private fun loadExcursionDetail() {
        viewModelScope.launch {
            try {
                _uiState.value = CalendarExcursionDetailUiState.Loading

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())

                // Cargar excursión
                val excursionDoc = firestore.collection("excursions")
                    .document(excursionId)
                    .get()
                    .await()

                if (!excursionDoc.exists()) {
                    _uiState.value = CalendarExcursionDetailUiState.Error("Excursión no encontrada")
                    return@launch
                }

                val excursion = Excursion(
                    id = excursionDoc.id,
                    title = excursionDoc.getString("title") ?: "",
                    description = excursionDoc.getString("description") ?: "",
                    date = excursionDoc.getTimestamp("date")?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it.toDate().time)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                    } ?: now,
                    location = excursionDoc.getString("location") ?: "",
                    imageUrl = excursionDoc.getString("imageUrl"),
                    authorizationPdfUrl = excursionDoc.getString("authorizationPdfUrl")
                )

                // Verificar si es admin
                val currentUserUid = auth.currentUser?.uid
                val isAdmin = if (currentUserUid != null) {
                    val userDoc = firestore.collection("users")
                        .document(currentUserUid)
                        .get()
                        .await()
                    val role = userDoc.getString("role")
                    role == "admin" || role == "superadmin"
                } else {
                    false
                }

                _uiState.value = CalendarExcursionDetailUiState.Success(
                    excursion = excursion,
                    isAdmin = isAdmin
                )

            } catch (e: Exception) {
                _uiState.value = CalendarExcursionDetailUiState.Error(
                    "Error al cargar detalles: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadExcursionDetail()
    }
}

sealed class CalendarExcursionDetailUiState {
    object Loading : CalendarExcursionDetailUiState()
    data class Success(
        val excursion: Excursion,
        val isAdmin: Boolean
    ) : CalendarExcursionDetailUiState()
    data class Error(val message: String) : CalendarExcursionDetailUiState()
}