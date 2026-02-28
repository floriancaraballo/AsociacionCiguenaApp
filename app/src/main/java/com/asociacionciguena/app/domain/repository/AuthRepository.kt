package com.asociacionciguena.app.domain.repository

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Autenticación
 */
interface AuthRepository {

    /**
     * Iniciar sesión con email y contraseña
     */
    suspend fun login(email: String, password: String): Result<User>

    /**
     * Cerrar sesión
     */
    suspend fun logout(): Result<Unit>

    /**
     * Obtener usuario actualmente logueado
     */
    suspend fun getCurrentUser(): Result<User?>

    /**
     * Verificar si hay un usuario logueado
     */
    fun isLoggedIn(): Flow<Boolean>
}