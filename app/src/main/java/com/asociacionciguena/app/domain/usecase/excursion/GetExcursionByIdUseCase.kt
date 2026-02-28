package com.asociacionciguena.app.domain.usecase.excursion

import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import javax.inject.Inject

class GetExcursionByIdUseCase @Inject constructor(
    private val repository: ExcursionRepository
) {
    suspend operator fun invoke(excursionId: String): Result<Excursion> {
        return repository.getExcursionById(excursionId)
    }
}