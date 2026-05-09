package com.asociacionciguena.app.data.repository

import com.asociacionciguena.app.data.datasource.remote.FirebaseExcursionDataSource
import com.asociacionciguena.app.data.mapper.toDomain
import com.asociacionciguena.app.domain.model.Excursion
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.repository.ExcursionRepository
import com.asociacionciguena.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ExcursionRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseExcursionDataSource,
    private val firestore: FirebaseFirestore
) : ExcursionRepository {

    override fun getExcursions(): Flow<Result<List<Excursion>>> = flow {
        // ✅ Emitir Loading primero
        emit(Result.Loading)

        try {
            remoteDataSource.getExcursions().collect { dtoList ->
                val excursions = dtoList.map { it.toDomain() }
                emit(Result.Success(excursions))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Result.Error(
                message = e.message ?: "Error al cargar excursiones",
                exception = e
            ))
        }
    }

    override suspend fun getExcursionById(id: String): Result<Excursion> {
        return try {
            val excursionDto = remoteDataSource.getExcursionById(id)
            if (excursionDto != null) {
                Result.Success(excursionDto.toDomain())
            } else {
                Result.Error("Excursión no encontrada")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al obtener excursión",
                exception = e
            )
        }
    }

    override suspend fun registerToExcursion(
        excursionId: String,
        userId: String
    ): Result<Unit> {
        return try {
            val registration = hashMapOf(
                "userId" to userId,
                "excursionId" to excursionId,
                "registeredAt" to com.google.firebase.Timestamp.now()
            )

            firestore.collection(Constants.COLLECTION_EXCURSIONS)
                .document(excursionId)
                .collection("registrations")
                .document(userId)
                .set(registration)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al registrarse",
                exception = e
            )
        }
    }
}
