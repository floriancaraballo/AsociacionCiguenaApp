package com.asociacionciguena.app.presentation.screens.admin.photos

import android.net.Uri

sealed class PhotoUploadUiState {
    object Idle : PhotoUploadUiState()

    data class PhotosSelected(
        val uris: List<Uri>,
        val selectedExcursionId: String? = null
    ) : PhotoUploadUiState()

    data class Uploading(
        val progress: Float,
        val currentPhotoIndex: Int,
        val totalPhotos: Int
    ) : PhotoUploadUiState()

    object Success : PhotoUploadUiState()

    data class Error(
        val message: String
    ) : PhotoUploadUiState()
}
