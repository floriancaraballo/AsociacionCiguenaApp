package com.asociacionciguena.app.domain.usecase.auth

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.model.User
import com.asociacionciguena.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use Case: Iniciar sesión
 *
 * Lógica adicional: Validar formato de email
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        // Validar formato de email
        if (!isValidEmail(email)) {
            return Result.Error("Email inválido")
        }

        // Validar que la contraseña no esté vacía
        if (password.isBlank()) {
            return Result.Error("La contraseña no puede estar vacía")
        }

        // Validar longitud mínima de contraseña
        if (password.length < 6) {
            return Result.Error("La contraseña debe tener al menos 6 caracteres")
        }

        return repository.login(email.trim(), password)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}