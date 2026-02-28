package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.ExcursionDto
import kotlinx.coroutines.flow.Flow

interface FirebaseExcursionDataSource {
    fun getExcursions(): Flow<List<ExcursionDto>>
    suspend fun getExcursionById(id: String): ExcursionDto?
}