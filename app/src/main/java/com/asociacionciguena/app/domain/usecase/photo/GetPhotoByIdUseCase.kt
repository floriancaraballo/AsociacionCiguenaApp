package com.asociacionciguena.app.domain.usecase.photo

import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.PhotoRepository
import javax.inject.Inject

class GetPhotoByIdUseCase @Inject constructor(
    private val repository: PhotoRepository
) {
    suspend operator fun invoke(photoId: String, userId: String): Result<Photo> {
        return repository.getPhotoById(photoId, userId)
    }
}