package com.asociacionciguena.app.domain.repository

import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del Repository de Noticias
 *
 * Define QUÉ operaciones se pueden hacer,
 * pero NO cómo se implementan
 */
interface NewsRepository {

    /**
     * Obtiene todas las noticias públicas
     *
     * @return Flow que emite Result<List<News>>
     *         Flow = stream de datos que se actualiza automáticamente
     *         Result = wrapper que indica éxito o error
     */
    fun getNews(): Flow<Result<List<News>>>

    /**
     * Obtiene una noticia específica por ID
     *
     * @param id ID de la noticia
     * @return Result con la noticia o error
     */
    suspend fun getNewsById(id: String): Result<News>
}