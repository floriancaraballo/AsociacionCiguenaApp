package com.asociacionciguena.app.domain.repository

import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Excursiones
 */
interface ExcursionRepository {

    /**
     * Obtiene todas las excursiones futuras
     */
    fun getExcursions(): Flow<Result<List<Excursion>>>

    /**
     * Obtiene una excursión específica
     */
    suspend fun getExcursionById(id: String): Result<Excursion>

    /**
     * Registra un usuario a una excursión
     */
    suspend fun registerToExcursion(excursionId: String, userId: String): Result<Unit>
}