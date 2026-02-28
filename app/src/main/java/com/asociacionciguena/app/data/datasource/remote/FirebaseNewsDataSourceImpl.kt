package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.NewsDto
import com.asociacionciguena.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Implementación de acceso a Noticias en Firebase
 */
class FirebaseNewsDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirebaseNewsDataSource {

    override fun getNews(): Flow<List<NewsDto>> = callbackFlow {
        val listener = firestore.collection(Constants.COLLECTION_NEWS)
            .whereEqualTo("isPublic", true)
            .orderBy("publishedDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val news = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(NewsDto::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(news)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getNewsById(id: String): NewsDto? {
        return try {
            val doc = firestore.collection(Constants.COLLECTION_NEWS)
                .document(id)
                .get()
                .await()

            doc.toObject(NewsDto::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }
}