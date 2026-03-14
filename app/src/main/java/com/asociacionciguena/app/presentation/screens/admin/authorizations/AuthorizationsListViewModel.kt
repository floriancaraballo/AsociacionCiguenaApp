package com.asociacionciguena.app.presentation.screens.admin.authorizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asociacionciguena.app.data.repository.SignedAuthorizationRepository
import com.asociacionciguena.app.domain.model.SignedAuthorization
import com.asociacionciguena.app.domain.model.AuthorizationStatus
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
}