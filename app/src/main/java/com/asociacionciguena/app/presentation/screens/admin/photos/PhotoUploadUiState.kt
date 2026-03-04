package com.asociacionciguena.app.presentation.screens.admin.photos

import android.net.Uri

sealed class PhotoUploadUiState {
    object Idle : PhotoUploadUiState()

    data class PhotosSelected(  // ← Cambio: Photos en plural
        val uris: List<Uri>,  // ← Cambio: Lista de URIs
        val selectedExcursionId: String? = null,
        val selectedUsers: List<String> = emptyList()
    ) : PhotoUploadUiState()

    data class Uploading(
        val progress: Float,
        val currentPhotoIndex: Int,  // ← NUEVO
        val totalPhotos: Int  // ← NUEVO
    ) : PhotoUploadUiState()

    object Success : PhotoUploadUiState()

    data class Error(
        val message: String
    ) : PhotoUploadUiState()
}