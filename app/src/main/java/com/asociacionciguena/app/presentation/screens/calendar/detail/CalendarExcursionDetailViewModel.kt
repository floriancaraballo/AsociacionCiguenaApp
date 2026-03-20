package com.asociacionciguena.app.presentation.screens.calendar.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.User
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
import com.asociacionciguena.app.data.repository.PaymentRepository
import android.net.Uri
import com.asociacionciguena.app.data.repository.SignedAuthorizationRepository
import com.asociacionciguena.app.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.graphics.Path
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import java.util.UUID


@HiltViewModel
class CalendarExcursionDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val paymentRepository: PaymentRepository,
    private val signedAuthorizationRepository: SignedAuthorizationRepository
) : ViewModel() {

    private val excursionId: String = checkNotNull(savedStateHandle["excursionId"])

    private val _uiState = MutableStateFlow<CalendarExcursionDetailUiState>(
        CalendarExcursionDetailUiState.Loading
    )
    val uiState: StateFlow<CalendarExcursionDetailUiState> = _uiState.asStateFlow()

    // ───────── NUEVO: Estado para mensaje de éxito de autorización ─────────
    private val _showAuthSuccess = MutableStateFlow(false)
    val showAuthSuccess: StateFlow<Boolean> = _showAuthSuccess.asStateFlow()

    fun dismissAuthSuccess() {
        _showAuthSuccess.value = false
    }

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
                    authorizationPdfUrl = excursionDoc.getString("authorizationPdfUrl"),
                    price = excursionDoc.getDouble("price")  // ← NUEVO
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

    /**
     * Obtener estado de pago del usuario actual
     */
    fun getPaymentStatus(userId: String): Flow<Payment?> {
        return if (userId.isNotEmpty() && excursionId.isNotEmpty()) {
            paymentRepository.getPaymentStatus(excursionId, userId)
        } else {
            flow { emit(null) }
        }
    }

    /**
     * Subir comprobante de pago
     */
    fun uploadPaymentProof(
        excursionId: String,
        amount: Double,
        photoUri: Uri
    ) {
        viewModelScope.launch {
            try {
                // Mantener el estado actual mientras se sube
                val currentState = _uiState.value

                val result = paymentRepository.uploadPaymentProof(
                    excursionId = excursionId,
                    amount = amount,
                    photoUri = photoUri
                )

                if (result.isSuccess) {
                    // Recargar datos
                    loadExcursionDetail()
                    android.util.Log.d("Payment", "✅ Comprobante subido correctamente")
                } else {
                    _uiState.value = CalendarExcursionDetailUiState.Error(
                        "Error al subir comprobante: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CalendarExcursionDetailUiState.Error(
                    "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Obtener usuario actual
     */
    val currentUser: StateFlow<User?> = flow {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try {
                val userDoc = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (userDoc.exists()) {
                    val user = User(
                        id = userDoc.id,
                        email = userDoc.getString("email") ?: "",
                        displayName = userDoc.getString("displayName") ?: "",
                        role = userDoc.getString("role") ?: "socio",
                        photoUrl = userDoc.getString("photoUrl"),
                        createdAt = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                        photoConsents = emptyList()
                    )
                    emit(user)
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
        } else {
            emit(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Verificar si el usuario ya firmó la autorización
     */
    fun hasUserSignedAuthorization(userId: String): Flow<Boolean> = flow {
        if (userId.isEmpty()) {
            emit(false)
        } else {
            val hasSigned = signedAuthorizationRepository.hasUserSigned(excursionId, userId)
            emit(hasSigned)
        }
    }

    /**
     * Firmar autorización
     */
    fun signAuthorization(
        excursionTitle: String,
        excursionDate: String,
        tutorName: String,
        tutorDni: String,
        tutorPhone: String,
        tutorEmail: String,
        minorName: String?,
        signaturePaths: List<androidx.compose.ui.graphics.Path>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = signedAuthorizationRepository.signAuthorization(
                    excursionId = excursionId,
                    excursionTitle = excursionTitle,
                    excursionDate = excursionDate,
                    tutorName = tutorName,
                    tutorDni = tutorDni,
                    tutorPhone = tutorPhone,
                    tutorEmail = tutorEmail,
                    minorName = minorName,
                    signaturePaths = signaturePaths
                )

                if (result.isSuccess) {
                    android.util.Log.d("AuthDebug", "✅ FIRMA ÉXITO - Activando showAuthSuccess")

                    android.util.Log.d("SignAuthorization", "✅ Autorización firmada correctamente")
                    onSuccess()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Error desconocido"
                    android.util.Log.e("SignAuthorization", "❌ Error: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                android.util.Log.e("SignAuthorization", "❌ Exception: ${e.message}", e)
                onError(e.message ?: "Error al firmar autorización")
            }
        }
    }
    /**
     * Firmar MÚLTIPLES autorizaciones (hermanos) + enviar email batch
     */
    fun signMultipleAuthorizations(
        excursionId: String,
        excursionTitle: String,
        excursionDate: String,
        minors: List<MinorAuth>,
        tutorEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val authorizationIds = mutableListOf<String>()

                // 1. Firmar cada autorización CON isBatchEmail = true
                for (minor in minors) {
                    val result = signedAuthorizationRepository.signAuthorization(
                        excursionId = excursionId,
                        excursionTitle = excursionTitle,
                        excursionDate = excursionDate,
                        tutorName = minor.tutorName,
                        tutorDni = minor.tutorDni,
                        tutorPhone = minor.tutorPhone,
                        tutorEmail = tutorEmail,
                        minorName = minor.minorName,
                        signaturePaths = minor.signaturePaths,
                        isBatchEmail = true  // ← IMPORTANTE: marcar como batch
                    )

                    if (result.isSuccess) {
                        authorizationIds.add(result.getOrNull() ?: "")
                    } else {
                        throw Exception("Error al firmar: ${result.exceptionOrNull()?.message}")
                    }
                }

                // 2. ✅ LLAMAR A CLOUD FUNCTION PARA EMAIL BATCH (SOLO UNA VEZ)
                if (authorizationIds.isNotEmpty()) {
                    try {
                        val functions = Firebase.functions("europe-west1")  // ← Región correcta
                        val sendBatchEmail = functions.getHttpsCallable("sendBatchAuthorizationEmail")

                        android.util.Log.d("BatchDebug", "🔹 Llamando a sendBatchAuthorizationEmail")
                        android.util.Log.d("BatchDebug", "🔹 authorizationIds: $authorizationIds")

                        // ✅ UNA SOLA LLAMADA:
                        val callableResult = sendBatchEmail.call(
                            mapOf("authorizationIds" to authorizationIds)
                        ).await()

                        val responseData = callableResult.getData() as? Map<*, *>
                        android.util.Log.d("BatchEmail", "✅ Email batch enviado: $responseData")

                    } catch (e: Exception) {
                        android.util.Log.e("BatchDebug", "❌ Error llamando a Cloud Function: ${e.message}", e)
                        android.util.Log.e("BatchEmail", "⚠️ Error enviando email batch: ${e.message}")
                        // No fallamos: las autorizaciones ya están guardadas
                    }
                }

                // 3. ✅ Activar mensaje de éxito (SOLO UNA VEZ, al final)
                android.util.Log.d("BatchDebug", "🔹 Activando _showAuthSuccess = true")
                _showAuthSuccess.value = true

                android.util.Log.d("SignAuthorization", "✅ ${minors.size} autorizaciones firmadas correctamente")
                onSuccess()

            } catch (e: Exception) {
                android.util.Log.e("SignAuthorization", "❌ Exception: ${e.message}", e)
                onError(e.message ?: "Error al firmar autorizaciones")
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