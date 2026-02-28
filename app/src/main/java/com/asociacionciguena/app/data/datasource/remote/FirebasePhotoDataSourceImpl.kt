package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.PhotoDto
import com.asociacionciguena.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebasePhotoDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirebasePhotoDataSource {

    override fun getPhotosByExcursion(excursionId: String): Flow<List<PhotoDto>> = callbackFlow {
        val listener = firestore.collection(Constants.COLLECTION_PHOTOS)
            .whereEqualTo("excursionId", excursionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val photos = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PhotoDto::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(photos)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getPhotoById(photoId: String): PhotoDto? {
        return try {
            val doc = firestore.collection(Constants.COLLECTION_PHOTOS)
                .document(photoId)
                .get()
                .await()

            doc.toObject(PhotoDto::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }
}