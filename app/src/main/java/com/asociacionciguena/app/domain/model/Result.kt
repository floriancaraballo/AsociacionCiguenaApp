package com.asociacionciguena.app.domain.model

/**
 * Wrapper genérico para resultados de operaciones
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}