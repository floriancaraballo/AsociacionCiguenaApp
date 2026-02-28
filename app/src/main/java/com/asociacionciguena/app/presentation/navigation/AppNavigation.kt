package com.asociacionciguena.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asociacionciguena.app.presentation.screens.main.MainScreen
import com.asociacionciguena.app.presentation.screens.news.NewsDetailScreen
import com.asociacionciguena.app.presentation.screens.splash.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

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
                onNavigateToNewsDetail = { newsId ->
                    navController.navigate(Screen.NewsDetail.createRoute(newsId))
                }
            )
        }

        // News Detail Screen (pantalla completa)
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