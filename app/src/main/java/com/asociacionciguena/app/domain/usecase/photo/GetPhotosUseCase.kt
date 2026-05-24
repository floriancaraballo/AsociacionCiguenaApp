package com.asociacionciguena.app.domain.usecase.photo

import com.asociacionciguena.app.domain.model.Photo
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case: Obtener fotos de una excursión
 *
 * La pantalla que invoca este caso de uso debe validar la visibilidad de la excursion.
 */
class GetPhotosUseCase @Inject constructor(
    private val repository: PhotoRepository
) {
    operator fun invoke(excursionId: String, userId: String): Flow<Result<List<Photo>>> {
        return repository.getPhotosByExcursion(excursionId, userId)
    }
}
