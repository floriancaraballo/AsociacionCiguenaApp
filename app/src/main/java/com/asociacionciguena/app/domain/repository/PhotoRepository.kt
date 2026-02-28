package com.asociacionciguena.app.domain.repository

import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Fotos
 */
interface PhotoRepository {

    /**
     * Obtiene fotos de una excursión específica
     * Solo devuelve fotos a las que el usuario tiene acceso
     */
    fun getPhotosByExcursion(
        excursionId: String,
        userId: String
    ): Flow<Result<List<Photo>>>

    /**
     * Obtiene una foto específica si el usuario tiene permiso
     */
    suspend fun getPhotoById(
        photoId: String,
        userId: String
    ): Result<Photo>

    /**
     * Descarga una foto desde Storage
     */
    suspend fun downloadPhoto(photoId: String): Result<ByteArray>
}