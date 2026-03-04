package com.asociacionciguena.app.data.repository

import com.asociacionciguena.app.data.datasource.remote.FirebaseNewsDataSource
import com.asociacionciguena.app.data.mapper.toDomain
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class NewsRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseNewsDataSource
) : NewsRepository {

    override fun getNews(): Flow<Result<List<News>>> = flow {
        try {
            remoteDataSource.getNews().collect { dtoList ->
                val newsList = dtoList.map { it.toDomain() }
                emit(Result.Success(newsList))
            }
        } catch (e: Exception) {
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
                Result.Error("Noticia no encontrada")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al obtener noticia",
                exception = e
            )
        }
    }
}