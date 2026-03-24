package com.asociacionciguena.app.data.datasource.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asociacionciguena.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesDataSource {

    companion object {
        val KEY_IS_LOGGED_IN = booleanPreferencesKey(Constants.PREF_IS_LOGGED_IN)
        val KEY_USER_ID = stringPreferencesKey(Constants.PREF_USER_ID)
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")  // ← NUEVO
    }

    override suspend fun setLoggedIn(isLoggedIn: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = isLoggedIn
        }
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[KEY_IS_LOGGED_IN] ?: false
        }
    }

    override suspend fun setUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
        }
    }

    override fun getUserId(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[KEY_USER_ID]
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    // ← NUEVOS MÉTODOS

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    override fun isOnboardingCompleted(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            val value = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            android.util.Log.d("PREF_DEBUG", "🔑 onboarding_completed = $value")
            android.util.Log.d("PREF_DEBUG", "📦 Package: ${javaClass.`package`?.name}")
            value
        }
    }
}