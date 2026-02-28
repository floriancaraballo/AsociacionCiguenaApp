package com.asociacionciguena.app.data.repository

import com.asociacionciguena.app.data.datasource.remote.FirebasePhotoDataSource
import com.asociacionciguena.app.data.mapper.toDomain
import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.PhotoRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PhotoRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebasePhotoDataSource,
    private val storage: FirebaseStorage
) : PhotoRepository {

    override fun getPhotosByExcursion(
        excursionId: String,
        userId: String
    ): Flow<Result<List<Photo>>> = flow {
        try {
            remoteDataSource.getPhotosByExcursion(excursionId).collect { dtoList ->
                val authorizedPhotos = dtoList
                    .filter { it.authorizedUsers.contains(userId) }
                    .map { it.toDomain() }

                emit(Result.Success(authorizedPhotos))
            }
        } catch (e: Exception) {
            emit(Result.Error(
                message = e.message ?: "Error al cargar fotos",
                exception = e
            ))
        }
    }

    override suspend fun getPhotoById(photoId: String, userId: String): Result<Photo> {
        return try {
            val photoDto = remoteDataSource.getPhotoById(photoId)

            if (photoDto != null) {
                if (photoDto.authorizedUsers.contains(userId)) {
                    Result.Success(photoDto.toDomain())
                } else {
                    Result.Error("No tienes permiso para ver esta foto")
                }
            } else {
                Result.Error("Foto no encontrada")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al obtener foto",
                exception = e
            )
        }
    }

    override suspend fun downloadPhoto(photoId: String): Result<ByteArray> {
        return try {
            val photoDto = remoteDataSource.getPhotoById(photoId)

            if (photoDto != null) {
                val storageRef = storage.reference.child(photoDto.storagePath)
                val maxDownloadSize = 10L * 1024 * 1024  // 10MB
                val bytes = storageRef.getBytes(maxDownloadSize).await()

                Result.Success(bytes)
            } else {
                Result.Error("Foto no encontrada")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al descargar foto",
                exception = e
            )
        }
    }
}