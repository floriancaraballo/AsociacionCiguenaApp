package com.asociacionciguena.app.util

/**
 * Constantes globales de la aplicación
 */
object Constants {

    // Firebase Collections
    const val COLLECTION_NEWS = "news"
    const val COLLECTION_EXCURSIONS = "excursions"
    const val COLLECTION_USERS = "users"
    const val COLLECTION_PHOTOS = "photos"

    // Firebase Storage Paths
    const val STORAGE_NEWS_IMAGES = "news"
    const val STORAGE_EXCURSION_PHOTOS = "excursions"
    const val STORAGE_PROFILE_PICTURES = "profile_pictures"

    // Preferences Keys
    const val PREF_USER_ID = "user_id"
    const val PREF_IS_LOGGED_IN = "is_logged_in"

    // Timeouts
    const val NETWORK_TIMEOUT = 30_000L // 30 segundos

    // Pagination
    const val PAGE_SIZE = 20
}