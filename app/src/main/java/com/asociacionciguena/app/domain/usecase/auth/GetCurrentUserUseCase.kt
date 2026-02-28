package com.asociacionciguena.app.domain.usecase.auth

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.model.User
import com.asociacionciguena.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use Case: Obtener usuario actual
 */
class GetCurrentUserUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<User?> {
        return repository.getCurrentUser()
    }
}