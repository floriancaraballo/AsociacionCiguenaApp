package com.asociacionciguena.app.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface PreferencesDataSource {

    suspend fun setLoggedIn(isLoggedIn: Boolean)
    fun isLoggedIn(): Flow<Boolean>

    suspend fun setUserId(userId: String)
    fun getUserId(): Flow<String?>

    suspend fun clear()

    suspend fun setOnboardingCompleted(completed: Boolean)
    fun isOnboardingCompleted(): Flow<Boolean>
}
