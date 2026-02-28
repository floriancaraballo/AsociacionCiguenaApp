package com.asociacionciguena.app.domain.usecase.excursion

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import javax.inject.Inject

/**
 * Use Case: Registrarse a una excursión
 *
 * Aquí podrías añadir validaciones:
 * - Verificar que hay plazas disponibles
 * - Verificar que el usuario no está ya registrado
 * - Verificar que la excursión no ha pasado
 */
class RegisterToExcursionUseCase @Inject constructor(
    private val repository: ExcursionRepository
) {
    suspend operator fun invoke(
        excursionId: String,
        userId: String
    ): Result<Unit> {
        // Aquí podrías añadir validaciones antes de llamar al repository
        return repository.registerToExcursion(excursionId, userId)
    }
}