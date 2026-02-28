package com.asociacionciguena.app.domain.usecase.auth

import com.asociacionciguena.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case: Verificar si hay sesión activa
 */
class IsLoggedInUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Flow<Boolean> {
        return repository.isLoggedIn()
    }
}