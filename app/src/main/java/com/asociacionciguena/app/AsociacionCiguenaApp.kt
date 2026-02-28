package com.asociacionciguena.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class principal de la app
 * Anotada con @HiltAndroidApp para habilitar Hilt
 */
@HiltAndroidApp
class AsociacionCiguenaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Inicializaciones globales aquí si son necesarias
    }
}