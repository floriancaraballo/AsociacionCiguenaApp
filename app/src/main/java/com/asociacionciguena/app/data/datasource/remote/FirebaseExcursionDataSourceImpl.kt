package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.ExcursionDto
import com.asociacionciguena.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseExcursionDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirebaseExcursionDataSource {

    override fun getExcursions(): Flow<List<ExcursionDto>> = callbackFlow {
        val listener = firestore.collection(Constants.COLLECTION_EXCURSIONS)
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val excursions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ExcursionDto::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(excursions)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getExcursionById(id: String): ExcursionDto? {
        return try {
            val doc = firestore.collection(Constants.COLLECTION_EXCURSIONS)
                .document(id)
                .get()
                .await()

            doc.toObject(ExcursionDto::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }
}