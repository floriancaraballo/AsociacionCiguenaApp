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

class FirebaseNewsDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirebaseNewsDataSource {

    override fun getNews(): Flow<List<NewsDto>> = callbackFlow {
        val listener = firestore.collection(Constants.COLLECTION_NEWS)
            .whereEqualTo("isPublic", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val news = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Mapeo manual para compatibilidad con datos antiguos
                        NewsDto(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            shortDescription = doc.getString("shortDescription") ?: "",
                            content = doc.getString("content") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            // COMPATIBILIDAD: Usar createdAt si existe, sino publishedDate
                            createdAt = doc.getTimestamp("createdAt")
                                ?: doc.getTimestamp("publishedDate"),
                            updatedAt = doc.getTimestamp("updatedAt"),
                            isPublic = doc.getBoolean("isPublic") ?: true,
                            additionalPhotos = (doc.get("additionalPhotos") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                // Ordenar por createdAt (más recientes primero)
                val sortedNews = news.sortedByDescending { it.createdAt }

                trySend(sortedNews)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getNewsById(id: String): NewsDto? {
        return try {
            val doc = firestore.collection(Constants.COLLECTION_NEWS)
                .document(id)
                .get()
                .await()

            // Mapeo manual para compatibilidad
            NewsDto(
                id = doc.id,
                title = doc.getString("title") ?: "",
                shortDescription = doc.getString("shortDescription") ?: "",
                content = doc.getString("content") ?: "",
                imageUrl = doc.getString("imageUrl"),
                // COMPATIBILIDAD: Usar createdAt si existe, sino publishedDate
                createdAt = doc.getTimestamp("createdAt")
                    ?: doc.getTimestamp("publishedDate"),
                updatedAt = doc.getTimestamp("updatedAt"),
                isPublic = doc.getBoolean("isPublic") ?: true,
                additionalPhotos = (doc.get("additionalPhotos") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
}