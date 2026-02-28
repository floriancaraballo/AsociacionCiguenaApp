package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.PhotoDto
import kotlinx.coroutines.flow.Flow

interface FirebasePhotoDataSource {

    /**
     * Obtiene fotos de una excursión específica
     */
    fun getPhotosByExcursion(excursionId: String): Flow<List<PhotoDto>>

    /**
     * Obtiene una foto específica por ID
     */
    suspend fun getPhotoById(photoId: String): PhotoDto?
}