package com.asociacionciguena.app.presentation.screens.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

    companion object {
        private const val STARTUP_PRELOAD_TIMEOUT_MS = 15_000L
        private const val NEWS_PRELOAD_LIMIT = 5L
        private const val EXCURSION_PRELOAD_LIMIT = 5L
        private const val GALLERY_EXCURSION_PRELOAD_LIMIT = 2
        private const val GALLERY_EXCURSION_SCAN_LIMIT = 12L
        private const val IMAGE_PRELOAD_RETRY_COUNT = 3
        private const val IMAGE_PRELOAD_RETRY_DELAY_MS = 400L
    }

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
        val preloadUrls = withTimeoutOrNull(STARTUP_PRELOAD_TIMEOUT_MS) {
            coroutineScope {
                val galleryExcursionIds = async {
                    fetchRecentPastExcursionIdsForGallery(currentUserId)
                }

                listOf(
                    async { fetchRecentNewsUrls() },
                    async { fetchRecentExcursionImageUrls() },
                    async { fetchGalleryPhotoUrls(galleryExcursionIds.await(), currentUserId) }
                ).awaitAll().flatten().distinct()
            }
        } ?: emptyList()

        if (preloadUrls.isEmpty()) return

        withTimeoutOrNull(STARTUP_PRELOAD_TIMEOUT_MS) {
            coroutineScope {
                preloadUrls.map { url ->
                    async { preloadImageWithRetry(url) }
                }.awaitAll()
            }
        }
    }

    private suspend fun fetchRecentNewsUrls(): List<String> {
        return runCatching {
            firestore.collection("news")
                .whereEqualTo("isPublic", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(NEWS_PRELOAD_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.getString("imageUrl")?.takeIf { it.isNotBlank() }
                }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRecentExcursionImageUrls(): List<String> {
        return runCatching {
            firestore.collection("excursions")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(EXCURSION_PRELOAD_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("imageUrl")?.takeIf { url -> url.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRecentPastExcursionIdsForGallery(currentUserId: String?): List<String> {
        if (currentUserId.isNullOrBlank()) return emptyList()

        return runCatching {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            firestore.collection("excursions")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(GALLERY_EXCURSION_SCAN_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val dateMillis = doc.getTimestamp("date")?.toDate()?.time ?: return@mapNotNull null
                    val excursionDate = Instant
                        .fromEpochMilliseconds(dateMillis)
                        .toLocalDateTime(TimeZone.currentSystemDefault())

                    doc.id.takeIf { excursionDate < now }
                }
                .take(GALLERY_EXCURSION_PRELOAD_LIMIT)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchGalleryPhotoUrls(
        excursionIds: List<String>,
        currentUserId: String?
    ): List<String> {
        if (currentUserId.isNullOrBlank() || excursionIds.isEmpty()) return emptyList()

        return coroutineScope {
            excursionIds.map { excursionId ->
                async {
                    runCatching {
                        firestore.collection("photos")
                            .whereEqualTo("excursionId", excursionId)
                            .whereArrayContains("authorizedUsers", currentUserId)
                            .get()
                            .await()
                            .documents
                            .flatMap { doc ->
                                buildList {
                                    val mediaType = doc.getString("mediaType") ?: "image"
                                    val imageUrl = doc.getString("imageUrl")?.takeIf { it.isNotBlank() }
                                    val thumbnailUrl = doc.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }

                                    if (mediaType == "video") {
                                        thumbnailUrl?.let(::add)
                                        imageUrl?.let(::add)
                                    } else {
                                        imageUrl?.let(::add)
                                        thumbnailUrl?.let(::add)
                                    }
                                }
                            }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun preloadImageWithRetry(url: String): Boolean {
        repeat(IMAGE_PRELOAD_RETRY_COUNT - 1) { attempt ->
            if (preloadImage(url)) return true
            if (attempt < IMAGE_PRELOAD_RETRY_COUNT - 1) {
                delay(IMAGE_PRELOAD_RETRY_DELAY_MS)
            }
        }

        return preloadImage(url)
    }

    private suspend fun preloadImage(url: String): Boolean {
        val result = runCatching {
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build()
            )
        }.getOrNull()

        return when (result) {
            is SuccessResult -> true
            is ErrorResult -> false
            else -> false
        }
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
