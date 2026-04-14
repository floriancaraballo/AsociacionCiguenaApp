package com.asociacionciguena.app.presentation.screens.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed class AppInitState {
    object Loading : AppInitState()
    data class Ready(
        val isOnboardingCompleted: Boolean,
        val isUserLoggedIn: Boolean
    ) : AppInitState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val context: Context,
    val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _initState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val initState: StateFlow<AppInitState> = _initState.asStateFlow()

    init {
        checkInitialState()
        observeAuthChanges()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            val isOnboardingCompleted = preferencesDataSource.isOnboardingCompleted().first()
            val isUserLoggedIn = auth.currentUser != null

            preloadStartupContent(auth.currentUser?.uid)

            _initState.value = AppInitState.Ready(
                isOnboardingCompleted = isOnboardingCompleted,
                isUserLoggedIn = isUserLoggedIn
            )
        }
    }

    private suspend fun preloadStartupContent(currentUserId: String?) {
        withTimeoutOrNull(5_000L) {
            val preloadUrls = coroutineScope {
                listOf(
                    async { fetchRecentNewsUrls() },
                    async { fetchRecentExcursionImageUrls() },
                    async { fetchRecentGalleryPreviewUrls(currentUserId) }
                ).awaitAll().flatten().distinct()
            }

            preloadUrls.take(12).forEach { url ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
                                .build()
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchRecentNewsUrls(): List<String> {
        return runCatching {
            firestore.collection("news")
                .whereEqualTo("isPublic", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .await()
                .documents
                .flatMap { doc ->
                    buildList {
                        doc.getString("imageUrl")?.takeIf { it.isNotBlank() }?.let(::add)
                        (doc.get("additionalPhotos") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.filter { it.isNotBlank() }
                            ?.take(2)
                            ?.let(::addAll)
                    }
                }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRecentExcursionImageUrls(): List<String> {
        return runCatching {
            firestore.collection("excursions")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(4)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("imageUrl")?.takeIf { url -> url.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRecentGalleryPreviewUrls(currentUserId: String?): List<String> {
        if (currentUserId.isNullOrBlank()) return emptyList()

        return runCatching {
            firestore.collection("photos")
                .whereArrayContains("authorizedUsers", currentUserId)
                .limit(6)
                .get()
                .await()
                .documents
                .flatMap { doc ->
                    buildList {
                        doc.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }?.let(::add)
                        doc.getString("imageUrl")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
        }.getOrDefault(emptyList())
    }

    private fun observeAuthChanges() {
        auth.addAuthStateListener { firebaseAuth ->
            val currentState = _initState.value
            if (currentState is AppInitState.Ready) {
                val isUserLoggedIn = firebaseAuth.currentUser != null

                if (currentState.isUserLoggedIn != isUserLoggedIn) {
                    _initState.value = currentState.copy(
                        isUserLoggedIn = isUserLoggedIn
                    )
                }
            }
        }
    }
}
