package com.asociacionciguena.app.di

import com.asociacionciguena.app.data.repository.AuthRepositoryImpl
import com.asociacionciguena.app.data.repository.ExcursionRepositoryImpl
import com.asociacionciguena.app.data.repository.NewsRepositoryImpl
import com.asociacionciguena.app.data.repository.PhotoRepositoryImpl
import com.asociacionciguena.app.domain.repository.AuthRepository
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import com.asociacionciguena.app.domain.repository.NewsRepository
import com.asociacionciguena.app.domain.repository.PhotoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt que conecta interfaces con implementaciones
 *
 * Usa @Binds en vez de @Provides cuando solo necesitas
 * conectar una interfaz con su implementación
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Conecta NewsRepository (interfaz) con NewsRepositoryImpl (implementación)
     *
     * Cuando alguien pida NewsRepository, Hilt le dará NewsRepositoryImpl
     */
    @Binds
    @Singleton
    abstract fun bindNewsRepository(
        impl: NewsRepositoryImpl
    ): NewsRepository

    /**
     * Conecta ExcursionRepository con su implementación
     */
    @Binds
    @Singleton
    abstract fun bindExcursionRepository(
        impl: ExcursionRepositoryImpl
    ): ExcursionRepository

    /**
     * Conecta AuthRepository con su implementación
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    /**
     * Conecta PhotoRepository con su implementación
     */
    @Binds
    @Singleton
    abstract fun bindPhotoRepository(
        impl: PhotoRepositoryImpl
    ): PhotoRepository
}