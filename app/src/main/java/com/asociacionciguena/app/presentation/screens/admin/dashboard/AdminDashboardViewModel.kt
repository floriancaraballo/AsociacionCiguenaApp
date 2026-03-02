package com.asociacionciguena.app.presentation.screens.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _isLoadingStats = MutableStateFlow(false)
    val isLoadingStats: StateFlow<Boolean> = _isLoadingStats.asStateFlow()

    init {
        checkAdminStatus()
        loadStats()
    }

    private fun checkAdminStatus() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userDoc = firestore.collection("users")
                        .document(currentUser.uid)
                        .get()
                        .await()

                    val role = userDoc.getString("role")
                    _isAdmin.value = role == "admin" || role == "superadmin"
                }
            } catch (e: Exception) {
                _isAdmin.value = false
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            try {
                _isLoadingStats.value = true

                // Contar noticias
                val newsSnapshot = firestore.collection("news").get().await()
                val totalNews = newsSnapshot.size()
                val publicNews = newsSnapshot.documents.count {
                    it.getBoolean("isPublic") == true
                }

                // Contar excursiones
                val excursionsSnapshot = firestore.collection("excursions").get().await()
                val totalExcursions = excursionsSnapshot.size()

                // Contar fotos
                val photosSnapshot = firestore.collection("photos").get().await()
                val totalPhotos = photosSnapshot.size()

                // Contar usuarios
                val usersSnapshot = firestore.collection("users").get().await()
                val totalUsers = usersSnapshot.size()
                val adminUsers = usersSnapshot.documents.count {
                    val role = it.getString("role")
                    role == "admin" || role == "superadmin"
                }

                _stats.value = DashboardStats(
                    totalNews = totalNews,
                    publicNews = publicNews,
                    privateNews = totalNews - publicNews,
                    totalExcursions = totalExcursions,
                    totalPhotos = totalPhotos,
                    totalUsers = totalUsers,
                    adminUsers = adminUsers
                )

            } catch (e: Exception) {
                // Log error but don't crash
            } finally {
                _isLoadingStats.value = false
            }
        }
    }
}

data class DashboardStats(
    val totalNews: Int = 0,
    val publicNews: Int = 0,
    val privateNews: Int = 0,
    val totalExcursions: Int = 0,
    val totalPhotos: Int = 0,
    val totalUsers: Int = 0,
    val adminUsers: Int = 0
)
