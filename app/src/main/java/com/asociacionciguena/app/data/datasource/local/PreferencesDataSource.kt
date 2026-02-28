package com.asociacionciguena.app.data.datasource.local

import kotlinx.coroutines.flow.Flow

/**
 * DataSource para preferencias locales (DataStore)
 */
interface PreferencesDataSource {

    /**
     * Guardar si el usuario está logueado
     */
    suspend fun setLoggedIn(isLoggedIn: Boolean)

    /**
     * Obtener si el usuario está logueado
     */
    fun isLoggedIn(): Flow<Boolean>

    /**
     * Guardar ID del usuario
     */
    suspend fun setUserId(userId: String)

    /**
     * Obtener ID del usuario
     */
    fun getUserId(): Flow<String?>

    /**
     * Limpiar todas las preferencias (logout)
     */
    suspend fun clear()
}