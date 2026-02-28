package com.asociacionciguena.app.domain.usecase.news

import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case: Obtener todas las noticias
 *
 * Responsabilidad única: Obtener noticias del repository
 *
 * Aquí podrías añadir lógica adicional como:
 * - Filtrar noticias antiguas
 * - Ordenar por relevancia
 * - Cachear resultados
 * - etc.
 */
class GetNewsUseCase @Inject constructor(
    private val repository: NewsRepository
) {
    /**
     * operator fun invoke() permite llamar al use case como función:
     * getNewsUseCase() en vez de getNewsUseCase.execute()
     */
    operator fun invoke(): Flow<Result<List<News>>> {
        return repository.getNews()
    }
}