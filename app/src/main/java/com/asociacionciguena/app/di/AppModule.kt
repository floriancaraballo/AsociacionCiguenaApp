package com.asociacionciguena.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Extension para crear DataStore (almacenamiento local de preferencias)
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "asociacion_ciguena_preferences"
)

/**
 * Módulo de Hilt que provee dependencias generales de la app
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provee el Context de la aplicación
     *
     * @ApplicationContext asegura que es el Context de Application
     * (no de Activity, que podría ser destruido)
     */
    @Provides
    @Singleton
    fun provideContext(
        @ApplicationContext context: Context
    ): Context {
        return context
    }

    /**
     * Provee DataStore para guardar preferencias
     * Por ejemplo: usuario logueado, tema oscuro, etc.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }
}