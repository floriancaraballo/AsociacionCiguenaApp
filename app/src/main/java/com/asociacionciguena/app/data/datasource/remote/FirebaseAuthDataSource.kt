package com.asociacionciguena.app.data.datasource.remote

import com.asociacionciguena.app.data.dto.UserDto
import com.google.firebase.auth.FirebaseUser

interface FirebaseAuthDataSource {

    /**
     * Iniciar sesión con email y contraseña
     */
    suspend fun login(email: String, password: String): FirebaseUser?

    /**
     * Cerrar sesión
     */
    suspend fun logout()

    /**
     * Obtener usuario actual
     */
    fun getCurrentUser(): FirebaseUser?

    /**
     * Obtener datos del usuario desde Firestore
     */
    suspend fun getUserData(userId: String): UserDto?
}