package com.asociacionciguena.app.domain.usecase.auth

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use Case: Cerrar sesión
 */
class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.logout()
    }
}