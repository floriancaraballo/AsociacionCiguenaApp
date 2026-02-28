package com.asociacionciguena.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt que provee instancias de Firebase
 *
 * @InstallIn(SingletonComponent::class)
 * Significa que estas dependencias vivirán mientras viva la app
 *
 * @Singleton
 * Solo habrá una instancia de cada objeto en toda la app
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provee la instancia de FirebaseAuth
     * Usada para login, logout, registro, etc.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    /**
     * Provee la instancia de FirebaseFirestore
     * Usada para acceder a la base de datos
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    /**
     * Provee la instancia de FirebaseStorage
     * Usada para subir/descargar archivos (fotos)
     */
    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }
}