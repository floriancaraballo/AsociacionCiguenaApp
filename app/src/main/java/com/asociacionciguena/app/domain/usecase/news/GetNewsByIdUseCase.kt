package com.asociacionciguena.app.domain.usecase.news

import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.NewsRepository
import javax.inject.Inject

/**
 * Use Case: Obtener una noticia específica por ID
 */
class GetNewsByIdUseCase @Inject constructor(
    private val repository: NewsRepository
) {
    suspend operator fun invoke(newsId: String): Result<News> {
        return repository.getNewsById(newsId)
    }
}