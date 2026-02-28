package com.asociacionciguena.app.domain.usecase.excursion

import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Use Case: Obtener excursiones futuras
 *
 * Lógica adicional: Filtrar solo excursiones futuras
 */
class GetExcursionsUseCase @Inject constructor(
    private val repository: ExcursionRepository
) {
    operator fun invoke(): Flow<Result<List<Excursion>>> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return repository.getExcursions().map { result ->
            when (result) {
                is Result.Success -> {
                    // Filtrar solo excursiones futuras
                    val futureExcursions = result.data.filter { excursion ->
                        excursion.date >= now
                    }
                    Result.Success(futureExcursions)
                }
                is Result.Error -> result
                is Result.Loading -> result
            }
        }
    }
}