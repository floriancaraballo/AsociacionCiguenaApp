package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.NewsDto
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz para acceso a s en Firebase
 */
interface FirebaseNewsDataSource {

    /**
     * Obtiene todas las noticias ordenadas por fecha
     */
    fun getNews(): Flow<List<NewsDto>>

    /**
     * Obtiene una noticia específica por ID
     */
    suspend fun getNewsById(id: String): NewsDto?
}