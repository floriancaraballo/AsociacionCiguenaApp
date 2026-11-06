package com.asociacionciguena.app.presentation.screens.admin.pins

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PinManagementViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<PinManagementUiState>(PinManagementUiState.Loading)
    val uiState: StateFlow<PinManagementUiState> = _uiState.asStateFlow()

    init {
        loadPins()
    }

    fun loadPins() {
        viewModelScope.launch {
            _uiState.value = PinManagementUiState.Loading
            try {
                requireContentManagerRole()
                val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply {
                    strength = Collator.SECONDARY
                }
                val children = firestore.collection(PINS_COLLECTION).get().await().documents
                    .mapNotNull { document ->
                        val name = document.getString("name")?.trim().orEmpty()
                        if (name.isBlank()) null else ChildPin(
                            id = document.id,
                            name = name,
                            pinCount = (document.getLong("pinCount") ?: 0L).coerceAtLeast(0L)
                        )
                    }
                    .sortedWith { first, second -> collator.compare(first.name, second.name) }
                _uiState.value = PinManagementUiState.Success(children)
            } catch (exception: Exception) {
                _uiState.value = PinManagementUiState.Error(
                    "No se pudo cargar la lista de pines. ${exception.userMessage()}"
                )
            }
        }
    }

    fun addChild(name: String, onResult: (String?) -> Unit) {
        val normalizedName = name.trim().replace(WHITESPACE_REGEX, " ")
        if (normalizedName.isBlank()) {
            onResult("El nombre es obligatorio.")
            return
        }
        if (normalizedName.length > MAX_NAME_LENGTH) {
            onResult("El nombre no puede superar los $MAX_NAME_LENGTH caracteres.")
            return
        }

        viewModelScope.launch {
            try {
                requireContentManagerRole()
                val reference = firestore.collection(PINS_COLLECTION)
                    .document(documentIdFor(normalizedName))
                firestore.runTransaction { transaction ->
                    if (transaction.get(reference).exists()) {
                        error("Ya existe un niño o niña con ese nombre.")
                    }
                    transaction.set(reference, mapOf(
                        "name" to normalizedName,
                        "pinCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }.await()
                onResult(null)
                loadPins()
            } catch (exception: Exception) {
                onResult("No se pudo añadir el nombre. ${exception.userMessage()}")
            }
        }
    }

    fun changePinCount(childId: String, delta: Long, onResult: (String?) -> Unit) {
        if (delta == 0L) return
        viewModelScope.launch {
            try {
                requireContentManagerRole()
                val reference = firestore.collection(PINS_COLLECTION).document(childId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(reference)
                    if (!snapshot.exists()) error("El niño o niña ya no existe.")
                    val currentCount = (snapshot.getLong("pinCount") ?: 0L).coerceAtLeast(0L)
                    transaction.update(
                        reference,
                        mapOf(
                            "pinCount" to (currentCount + delta).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
                onResult(null)
                loadPins()
            } catch (exception: Exception) {
                onResult("No se pudo actualizar el contador. ${exception.userMessage()}")
            }
        }
    }

    fun deleteChild(childId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                requireContentManagerRole()
                firestore.collection(PINS_COLLECTION).document(childId).delete().await()
                onResult(null)
                loadPins()
            } catch (exception: Exception) {
                onResult("No se pudo eliminar el nombre. ${exception.userMessage()}")
            }
        }
    }

    private suspend fun requireContentManagerRole() {
        val userId = auth.currentUser?.uid ?: error("Debes iniciar sesión.")
        val role = firestore.collection("users").document(userId).get().await().getString("role")
        check(role in CONTENT_MANAGER_ROLES) { "No tienes permisos para gestionar pines." }
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Inténtalo de nuevo."

    private fun documentIdFor(name: String): String {
        val normalized = name.lowercase(Locale.ROOT)
        val encoded = Base64.encodeToString(
            normalized.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "child_$encoded"
    }

    private companion object {
        const val PINS_COLLECTION = "pins"
        const val MAX_NAME_LENGTH = 100
        val CONTENT_MANAGER_ROLES = setOf("admin", "superadmin", "monitor")
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
