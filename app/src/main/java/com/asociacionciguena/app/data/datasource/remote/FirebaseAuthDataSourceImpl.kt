package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.UserDto
import com.asociacionciguena.app.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthDataSourceImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : FirebaseAuthDataSource {

    override suspend fun login(email: String, password: String): FirebaseUser? {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    override suspend fun getUserData(userId: String): UserDto? {
        return try {
            // PASO 1: Intentar buscar por UID (FORZAR SERVIDOR)
            var doc = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get(com.google.firebase.firestore.Source.SERVER)  // ← FORZAR SERVIDOR
                .await()

            // PASO 2: Si no existe, buscar por email y migrar
            if (!doc.exists()) {
                val userEmail = auth.currentUser?.email

                if (userEmail != null) {
                    // Buscar documento por email
                    val querySnapshot = firestore.collection(Constants.COLLECTION_USERS)
                        .whereEqualTo("email", userEmail)
                        .limit(1)
                        .get()
                        .await()

                    if (!querySnapshot.isEmpty) {
                        val tempDoc = querySnapshot.documents[0]
                        val userData = tempDoc.data

                        if (userData != null) {
                            // Migrar documento al UID correcto
                            firestore.collection(Constants.COLLECTION_USERS)
                                .document(userId)
                                .set(userData)
                                .await()

                            // Eliminar documento temporal
                            tempDoc.reference.delete().await()

                            // Obtener el documento recién migrado
                            doc = firestore.collection(Constants.COLLECTION_USERS)
                                .document(userId)
                                .get(Source.SERVER)
                                .await()
                        }
                    }
                }
            }

            // PASO 3: Retornar el usuario
            doc.toObject(UserDto::class.java)?.copy(id = doc.id)

        } catch (e: Exception) {
            null
        }
    }
}
