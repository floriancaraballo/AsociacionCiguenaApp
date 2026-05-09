package com.asociacionciguena.app.data.repository

import com.asociacionciguena.app.data.datasource.remote.FirebaseNewsDataSource
import com.asociacionciguena.app.data.mapper.toDomain
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.NewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class NewsRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseNewsDataSource
) : NewsRepository {

    override fun getNews(): Flow<Result<List<News>>> = flow {
        // ✅ 1️⃣ Emitir Loading PRIMERO (crucial para el spinner)
        android.util.Log.d("NewsRepo", "🔄 Emitiendo Result.Loading")
        emit(Result.Loading)

        try {
            android.util.Log.d("NewsRepo", "📥 Llamando a remoteDataSource.getNews()")
            remoteDataSource.getNews().collect { dtoList ->
                android.util.Log.d("NewsRepo", "✅ Recibidos ${dtoList.size} DTOs, emitiendo Success")

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val newsList = dtoList.map { it.toDomain() }
                emit(Result.Success(newsList))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NewsRepo", "❌ Error: ${e.message}")
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            emit(Result.Error(
                message = e.message ?: "Error al cargar publicaciones",
                exception = e
            ))
        }
    }

    override suspend fun getNewsById(id: String): Result<News> {
        return try {
            val newsDto = remoteDataSource.getNewsById(id)
            if (newsDto != null) {
                Result.Success(newsDto.toDomain())
            } else {
                Result.Error("Publicación no encontrada")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al obtener publicación",
                exception = e
            )
        }
    }
}
