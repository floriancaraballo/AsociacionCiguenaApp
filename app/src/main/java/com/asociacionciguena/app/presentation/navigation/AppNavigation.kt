package com.asociacionciguena.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asociacionciguena.app.presentation.screens.calendar.detail.SignatureScreen
import com.asociacionciguena.app.presentation.screens.main.MainScreen
import com.asociacionciguena.app.presentation.screens.news.NewsDetailScreen
import com.asociacionciguena.app.presentation.screens.splash.SplashScreen
import com.asociacionciguena.app.presentation.theme.ThemeViewModel

@Composable
fun AppNavigation(
    notificationType: String? = null,
    itemId: String? = null
) {
    val navController = rememberNavController()
    val themeViewModel: ThemeViewModel = hiltViewModel()

    // Deep linking - ejecutar cuando cambien los valores
    LaunchedEffect(notificationType, itemId) {
        android.util.Log.d("DEEP_LINK", "AppNavigation - Type: $notificationType, ID: $itemId")

        // Solo navegar si AMBOS valores son no-nulos
        if (!notificationType.isNullOrEmpty() && !itemId.isNullOrEmpty()) {
            android.util.Log.d("DEEP_LINK", "Esperando a que NavHost esté listo...")

            // Esperar a que splash termine y NavHost esté completamente inicializado
            kotlinx.coroutines.delay(2500)

            android.util.Log.d("DEEP_LINK", "Intentando navegar a $notificationType: $itemId")

            when (notificationType) {
                "news" -> {
                    android.util.Log.d("DEEP_LINK", "✅ Navegando a NewsDetail: $itemId")
                    navController.navigate(Screen.NewsDetail.createRoute(itemId))
                }
                "excursion", "photo" -> {
                    android.util.Log.d("DEEP_LINK", "✅ Tipo $notificationType - MainScreen se encargará")
                    // No navegar aquí, MainScreen tiene el NavHost interno
                }
                else -> {
                    android.util.Log.d("DEEP_LINK", "❌ Tipo desconocido: $notificationType")
                }
            }
        } else {
            android.util.Log.d("DEEP_LINK", "❌ No hay datos de notificación válidos")
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.News.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Main Screen (con bottom navigation)
        composable(Screen.News.route) {
            MainScreen(
                themeViewModel = themeViewModel,
                notificationType = notificationType,
                itemId = itemId,
                onNavigateToNewsDetail = { newsId ->
                    navController.navigate(Screen.NewsDetail.createRoute(newsId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.NewsDetail.route,
            arguments = listOf(
                navArgument("newsId") { type = NavType.StringType }
            )
        ) {
            val newsId = it.arguments?.getString("newsId") ?: ""
            NewsDetailScreen(
                newsId = newsId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}