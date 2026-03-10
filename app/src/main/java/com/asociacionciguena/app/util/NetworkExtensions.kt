package com.asociacionciguena.app.util

/**
 * Ejecuta una operación solo si hay conexión a internet
 */
suspend fun <T> NetworkMonitor.executeIfOnline(
    onOffline: () -> T,
    onOnline: suspend () -> T
): T {
    return if (isCurrentlyOnline()) {
        onOnline()
    } else {
        onOffline()
    }
}