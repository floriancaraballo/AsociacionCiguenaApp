package com.asociacionciguena.app.domain.usecase.photo

import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.PhotoRepository
import javax.inject.Inject

/**
 * Use Case: Descargar foto
 *
 * Descarga los bytes de la imagen desde Firebase Storage
 */
class DownloadPhotoUseCase @Inject constructor(
    private val repository: PhotoRepository
) {
    suspend operator fun invoke(photoId: String): Result<ByteArray> {
        return repository.downloadPhoto(photoId)
    }
}