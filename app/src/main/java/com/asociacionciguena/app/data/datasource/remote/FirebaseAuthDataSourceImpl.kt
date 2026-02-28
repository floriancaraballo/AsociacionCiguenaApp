package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.UserDto
import com.asociacionciguena.app.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
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
            val doc = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            doc.toObject(UserDto::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }
}