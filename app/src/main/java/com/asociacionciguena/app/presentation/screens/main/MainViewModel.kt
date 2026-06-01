package com.asociacionciguena.app.presentation.screens.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.asociacionciguena.app.data.datasource.local.PreferencesDataSource
import com.asociacionciguena.app.data.manager.FCMTokenManager
import com.asociacionciguena.app.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
        val isUserLoggedIn: Boolean,
        val currentUserId: String?
    ) : AppInitState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val fcmTokenManager: FCMTokenManager,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val context: Context,
    val networkMonitor: NetworkMonitor
) : ViewModel() {

    companion object {
        private const val STARTUP_PRELOAD_TIMEOUT_MS = 25_000L
        private const val BACKGROUND_PRELOAD_TIMEOUT_MS = 20_000L
        private const val STARTUP_PRELOAD_REQUIRED_PERCENT = 85
        private const val CRITICAL_PRELOAD_REQUIRED_PERCENT = 100
        private const val NEWS_PRELOAD_LIMIT = 15L
        private const val EXCURSION_PRELOAD_LIMIT = 50L
        private const val GALLERY_EXCURSION_PRELOAD_LIMIT = 4
        private const val GALLERY_EXCURSION_SCAN_LIMIT = 20L
        private const val IMAGE_PRELOAD_RETRY_COUNT = 3
        private const val IMAGE_PRELOAD_RETRY_DELAY_MS = 400L
    }

    private val _initState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val initState: StateFlow<AppInitState> = _initState.asStateFlow()

    private data class StartupPreloadUrls(
        val criticalUrls: List<String>,
        val secondaryUrls: List<String>
    ) {
        val allUrls: List<String> = (criticalUrls + secondaryUrls).distinct()
    }

    init {
        checkInitialState()
        observeOnboardingCompletion()
        observeAuthChanges()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            val isOnboardingCompleted = preferencesDataSource.isOnboardingCompleted().first()
            val isUserLoggedIn = auth.currentUser != null

            if (isUserLoggedIn) {
                refreshNotificationToken()
            }

            val remainingPreloadUrls = preloadStartupContent(auth.currentUser?.uid)

            _initState.value = AppInitState.Ready(
                isOnboardingCompleted = isOnboardingCompleted,
                isUserLoggedIn = isUserLoggedIn,
                currentUserId = auth.currentUser?.uid
            )

            continuePreloadingInBackground(remainingPreloadUrls)
        }
    }

    private suspend fun preloadStartupContent(currentUserId: String?): List<String> {
        val preloadUrls = withTimeoutOrNull(STARTUP_PRELOAD_TIMEOUT_MS) {
            coroutineScope {
                val galleryExcursionIds = async {
                    fetchRecentPastExcursionIdsForGallery(currentUserId)
                }
                val recentNewsUrls = async { fetchRecentNewsUrls() }
                val recentExcursionUrls = async { fetchRecentExcursionImageUrls() }
                val galleryPhotoUrls = async {
                    fetchGalleryPhotoUrls(galleryExcursionIds.await(), currentUserId)
                }

                val criticalUrls = (recentNewsUrls.await() + recentExcursionUrls.await()).distinct()
                val secondaryUrls = galleryPhotoUrls.await()
                    .distinct()
                    .filterNot { it in criticalUrls }

                StartupPreloadUrls(
                    criticalUrls = criticalUrls,
                    secondaryUrls = secondaryUrls
                )
            }
        } ?: StartupPreloadUrls(emptyList(), emptyList())

        if (preloadUrls.allUrls.isEmpty()) return emptyList()

        return preloadUntilMostImagesAreCached(preloadUrls)
    }

    private suspend fun preloadUntilMostImagesAreCached(preloadUrls: StartupPreloadUrls): List<String> {
        val criticalRemainingUrls = preloadUntilRequiredImagesAreCached(
            urls = preloadUrls.criticalUrls,
            requiredSuccessCount = requiredSuccessCount(
                totalUrls = preloadUrls.criticalUrls.size,
                requiredPercent = CRITICAL_PRELOAD_REQUIRED_PERCENT
            )
        )
        val cachedCriticalCount = preloadUrls.criticalUrls.size - criticalRemainingUrls.size
        val requiredTotalSuccessCount = requiredSuccessCount(
            totalUrls = preloadUrls.allUrls.size,
            requiredPercent = STARTUP_PRELOAD_REQUIRED_PERCENT
        )
        val requiredSecondarySuccessCount = (requiredTotalSuccessCount - cachedCriticalCount)
            .coerceAtLeast(0)
            .coerceAtMost(preloadUrls.secondaryUrls.size)

        val secondaryRemainingUrls = preloadUntilRequiredImagesAreCached(
            urls = preloadUrls.secondaryUrls,
            requiredSuccessCount = requiredSecondarySuccessCount
        )

        return (criticalRemainingUrls + secondaryRemainingUrls).distinct()
    }

    private suspend fun preloadUntilRequiredImagesAreCached(
        urls: List<String>,
        requiredSuccessCount: Int
    ): List<String> {
        if (urls.isEmpty() || requiredSuccessCount <= 0) return urls

        return withTimeoutOrNull(STARTUP_PRELOAD_TIMEOUT_MS) {
            supervisorScope {
                val remainingUrls = urls.toMutableSet()
                val results = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)
                val jobs = urls.map { url ->
                    launch {
                        results.send(url to preloadImageWithRetry(url))
                    }
                }

                var successfulPreloads = 0
                var completedPreloads = 0

                while (completedPreloads < urls.size && successfulPreloads < requiredSuccessCount) {
                    val (url, success) = results.receive()
                    completedPreloads++

                    if (success) {
                        successfulPreloads++
                        remainingUrls.remove(url)
                    }
                }

                if (successfulPreloads >= requiredSuccessCount) {
                    jobs.filter { it.isActive }.forEach { it.cancelAndJoin() }
                } else {
                    jobs.forEach { it.join() }
                }

                remainingUrls.toList()
            }
        } ?: urls
    }

    private fun continuePreloadingInBackground(urls: List<String>) {
        if (urls.isEmpty()) return

        viewModelScope.launch {
            withTimeoutOrNull(BACKGROUND_PRELOAD_TIMEOUT_MS) {
                coroutineScope {
                    urls.map { url ->
                        async { preloadImageWithRetry(url) }
                    }.awaitAll()
                }
            }
        }
    }

    private fun requiredSuccessCount(totalUrls: Int, requiredPercent: Int): Int {
        if (totalUrls <= 0) return 0
        return ((totalUrls * requiredPercent) + 99) / 100
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
            val userDoc = firestore.collection("users")
                .document(currentUserId)
                .get()
                .await()
            val role = userDoc.getString("role")
            val isAdmin = role == "admin" || role == "superadmin"
            val registrationYear = userDoc.registrationYear()

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

                    doc.id.takeIf {
                        excursionDate.date <= now.date &&
                            (isAdmin || excursionDate.year == registrationYear)
                    }
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

    private fun DocumentSnapshot.registrationYear(): Int? {
        return getTimestamp("createdAt")?.let {
            Instant.fromEpochMilliseconds(it.toDate().time)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
        } ?: auth.currentUser?.metadata?.creationTimestamp?.let {
            Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
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
                val currentUserId = firebaseAuth.currentUser?.uid

                if (
                    currentState.isUserLoggedIn != isUserLoggedIn ||
                    currentState.currentUserId != currentUserId
                ) {
                    if (isUserLoggedIn) {
                        refreshNotificationToken()
                    }

                    _initState.value = currentState.copy(
                        isUserLoggedIn = isUserLoggedIn,
                        currentUserId = currentUserId
                    )
                }
            }
        }
    }

    private fun refreshNotificationToken() {
        viewModelScope.launch {
            fcmTokenManager.refreshToken()
                .onFailure { error ->
                    android.util.Log.w(
                        "MainViewModel",
                        "No se pudo actualizar el token FCM: ${error.message}"
                    )
                }
        }
    }

    private fun observeOnboardingCompletion() {
        viewModelScope.launch {
            preferencesDataSource.isOnboardingCompleted()
                .distinctUntilChanged()
                .collect { isOnboardingCompleted ->
                    val currentState = _initState.value
                    if (
                        currentState is AppInitState.Ready &&
                        currentState.isOnboardingCompleted != isOnboardingCompleted
                    ) {
                        _initState.value = currentState.copy(
                            isOnboardingCompleted = isOnboardingCompleted
                        )
                    }
                }
        }
    }
}
