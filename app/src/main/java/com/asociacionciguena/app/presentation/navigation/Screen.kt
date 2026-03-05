package com.asociacionciguena.app.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object News : Screen("news")
    object Calendar : Screen("calendar")
    object Login : Screen("login?returnTo={returnTo}") {
        fun createRoute(returnTo: String = "profile") = "login?returnTo=$returnTo"
    }
    object Gallery : Screen("gallery")
    object Profile : Screen("profile")

    // RUTAS ADMIN
    object AdminDashboard : Screen("admin_dashboard")
    object NewsManagement : Screen("admin_news")
    object NewsForm : Screen("admin_news_form/{newsId}") {
        fun createRoute(newsId: String = "new") = "admin_news_form/$newsId"
    }
    object ExcursionManagement : Screen("admin_excursions")
    object ExcursionForm : Screen("admin_excursion_form/{excursionId}") {
        fun createRoute(excursionId: String = "new") = "admin_excursion_form/$excursionId"
    }
    object PhotoUpload : Screen("admin_photo_upload?excursionId={excursionId}") {
        fun createRoute(excursionId: String? = null): String {
            return if (excursionId != null) {
                "admin_photo_upload?excursionId=$excursionId"
            } else {
                "admin_photo_upload"
            }
        }
    }

    object NewsDetail : Screen("news_detail/{newsId}") {
        fun createRoute(newsId: String) = "news_detail/$newsId"
    }
    object UserManagement : Screen("user_management")

    object ExcursionDetail : Screen("excursion_detail/{excursionId}") {
        fun createRoute(excursionId: String) = "excursion_detail/$excursionId"
    }
    object Onboarding : Screen("onboarding")
}