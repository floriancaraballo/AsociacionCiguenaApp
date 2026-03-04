package com.asociacionciguena.app.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface PreferencesDataSource {

    suspend fun setLoggedIn(isLoggedIn: Boolean)
    fun isLoggedIn(): Flow<Boolean>

    suspend fun setUserId(userId: String)
    fun getUserId(): Flow<String?>

    suspend fun clear()

    // ← AÑADIR ESTOS MÉTODOS

    /**
     * Guardar que el onboarding fue completado
     */
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * Verificar si el onboarding fue completado
     */
    fun isOnboardingCompleted(): Flow<Boolean>
}