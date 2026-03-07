package com.asociacionciguena.app.domain.usecase.excursion

import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case: Obtener TODAS las excursiones (pasadas y futuras)
 */
class GetAllExcursionsUseCase @Inject constructor(
    private val repository: ExcursionRepository
) {
    operator fun invoke(): Flow<Result<List<Excursion>>> {
        // Sin filtro, devolver todas
        return repository.getExcursions()
    }
}