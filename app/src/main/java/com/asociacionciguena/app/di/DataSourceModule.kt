package com.asociacionciguena.app.di

import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSourceImpl
import com.asociacionciguena.app.data.datasource.remote.FirebaseAuthDataSource
import com.asociacionciguena.app.data.datasource.remote.FirebaseAuthDataSourceImpl
import com.asociacionciguena.app.data.datasource.remote.FirebaseExcursionDataSource
import com.asociacionciguena.app.data.datasource.remote.FirebaseExcursionDataSourceImpl
import com.asociacionciguena.app.data.datasource.remote.FirebaseNewsDataSource
import com.asociacionciguena.app.data.datasource.remote.FirebaseNewsDataSourceImpl
import com.asociacionciguena.app.data.datasource.remote.FirebasePhotoDataSource
import com.asociacionciguena.app.data.datasource.remote.FirebasePhotoDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt que conecta DataSources
 *
 * Los DataSources son la capa más baja:
 * acceso directo a Firebase, Room, etc.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    // ========================================
    // REMOTE DATA SOURCES (Firebase)
    // ========================================

    @Binds
    @Singleton
    abstract fun bindFirebaseNewsDataSource(
        impl: FirebaseNewsDataSourceImpl
    ): FirebaseNewsDataSource

    @Binds
    @Singleton
    abstract fun bindFirebaseExcursionDataSource(
        impl: FirebaseExcursionDataSourceImpl
    ): FirebaseExcursionDataSource

    @Binds
    @Singleton
    abstract fun bindFirebaseAuthDataSource(
        impl: FirebaseAuthDataSourceImpl
    ): FirebaseAuthDataSource

    @Binds
    @Singleton
    abstract fun bindFirebasePhotoDataSource(
        impl: FirebasePhotoDataSourceImpl
    ): FirebasePhotoDataSource

    // ========================================
    // LOCAL DATA SOURCES (DataStore)
    // ========================================

    @Binds
    @Singleton
    abstract fun bindPreferencesDataSource(
        impl: PreferencesDataSourceImpl
    ): PreferencesDataSource
}