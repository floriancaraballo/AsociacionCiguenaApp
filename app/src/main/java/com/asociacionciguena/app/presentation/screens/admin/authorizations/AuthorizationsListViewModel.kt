package com.asociacionciguena.app.presentation.screens.admin.authorizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.repository.SignedAuthorizationRepository
import com.asociacionciguena.app.domain.model.SignedAuthorization
import com.asociacionciguena.app.domain.model.AuthorizationStatus
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.functions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


@HiltViewModel
class AuthorizationsListViewModel @Inject constructor(
    private val signedAuthorizationRepository: SignedAuthorizationRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    /**
     * Obtener autorizaciones de una excursión
     */
    fun getAuthorizations(excursionId: String): Flow<List<SignedAuthorization>> {
        return signedAuthorizationRepository.getExcursionAuthorizations(excursionId)
    }

    /**
     * Aprobar autorización
     */
    fun approveAuthorization(authorizationId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("signedAuthorizations")
                    .document(authorizationId)
                    .update(
                        mapOf(
                            "status" to AuthorizationStatus.APPROVED.name,
                            "validatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener {
                        android.util.Log.d("AuthViewModel", "✅ Autorización aprobada: $authorizationId")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("AuthViewModel", "❌ Error aprobando: ${e.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "❌ Exception: ${e.message}")
            }
        }
    }

    /**
     * Rechazar autorización
     */
    fun rejectAuthorization(authorizationId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("signedAuthorizations")
                    .document(authorizationId)
                    .update(
                        mapOf(
                            "status" to AuthorizationStatus.REJECTED.name,
                            "validatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener {
                        android.util.Log.d("AuthViewModel", "✅ Autorización rechazada: $authorizationId")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("AuthViewModel", "❌ Error rechazando: ${e.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "❌ Exception: ${e.message}")
            }
        }
    }

    /**
     * Generar documento Word con lista de menores
     */
    fun generateMinorsListDocx(
        excursionId: String,
        excursionTitle: String,
        excursionDate: String,
        onSuccess: (downloadUrl: String, fileName: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val functions = Firebase.functions("europe-west1")
                val generateDoc = functions.getHttpsCallable("generateMinorsListDocx")

                val result = generateDoc.call(
                    mapOf(
                        "excursionId" to excursionId,
                        "excursionTitle" to excursionTitle,
                        "excursionDate" to excursionDate
                    )
                ).await()

                val responseData = result.getData() as? Map<*, *>
                val downloadUrl = responseData?.get("downloadUrl") as? String
                val fileName = responseData?.get("fileName") as? String ?: "lista.docx"

                if (downloadUrl != null) {
                    onSuccess(downloadUrl, fileName)
                } else {
                    onError("No se pudo obtener la URL de descarga")
                }

            } catch (e: Exception) {
                onError("Error al generar documento: ${e.message}")
            }
        }
    }
}