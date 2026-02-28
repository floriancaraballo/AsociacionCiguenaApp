package com.asociacionciguena.app.data.repository

import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.data.datasource.remote.FirebaseAuthDataSource
import com.asociacionciguena.app.data.mapper.toDomain
import com.asociacionciguena.app.domain.model.Result
import com.asociacionciguena.app.domain.model.User
import com.asociacionciguena.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseAuthDataSource,
    private val preferencesDataSource: PreferencesDataSource
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            // Intentar login en Firebase
            val firebaseUser = remoteDataSource.login(email, password)

            if (firebaseUser != null) {
                // Obtener datos del usuario desde Firestore
                val userDto = remoteDataSource.getUserData(firebaseUser.uid)

                if (userDto != null) {
                    // Guardar en preferencias locales
                    preferencesDataSource.setLoggedIn(true)
                    preferencesDataSource.setUserId(firebaseUser.uid)

                    // Retornar usuario del dominio
                    Result.Success(userDto.toDomain())
                } else {
                    Result.Error("Usuario no encontrado en la base de datos")
                }
            } else {
                Result.Error("Email o contraseña incorrectos")
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al iniciar sesión",
                exception = e
            )
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            remoteDataSource.logout()
            preferencesDataSource.clear()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al cerrar sesión",
                exception = e
            )
        }
    }

    override suspend fun getCurrentUser(): Result<User?> {
        return try {
            val firebaseUser = remoteDataSource.getCurrentUser()

            if (firebaseUser != null) {
                val userDto = remoteDataSource.getUserData(firebaseUser.uid)
                if (userDto != null) {
                    Result.Success(userDto.toDomain())
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "Error al obtener usuario",
                exception = e
            )
        }
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return preferencesDataSource.isLoggedIn()
    }
}