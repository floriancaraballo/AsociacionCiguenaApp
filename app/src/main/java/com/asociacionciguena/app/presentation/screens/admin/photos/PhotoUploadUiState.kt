package com.asociacionciguena.app.presentation.screens.admin.photos

import android.net.Uri

/**
 * Estados del upload de fotos
 */
sealed class PhotoUploadUiState {
    object Idle : PhotoUploadUiState()

    data class PhotoSelected(
        val uri: Uri,
        val selectedExcursionId: String? = null,
        val selectedUsers: List<String> = emptyList()
    ) : PhotoUploadUiState()

    data class Uploading(
        val progress: Float
    ) : PhotoUploadUiState()

    object Success : PhotoUploadUiState()

    data class Error(
        val message: String
    ) : PhotoUploadUiState()
}