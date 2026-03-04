package com.asociacionciguena.app.presentation.screens.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.asociacionciguena.app.presentation.navigation.Screen
import com.asociacionciguena.app.presentation.navigation.bottomNavItems
import com.asociacionciguena.app.presentation.screens.news.NewsScreen
import com.asociacionciguena.app.presentation.screens.calendar.CalendarScreen
import com.asociacionciguena.app.presentation.screens.auth.LoginScreen
import com.asociacionciguena.app.presentation.screens.gallery.GalleryScreen
import com.asociacionciguena.app.presentation.screens.profile.ProfileScreen
import com.asociacionciguena.app.presentation.screens.admin.dashboard.AdminDashboardScreen
import com.asociacionciguena.app.presentation.screens.admin.news.NewsFormScreen
import com.asociacionciguena.app.presentation.screens.admin.news.NewsManagementScreen
import com.asociacionciguena.app.presentation.screens.admin.excursions.ExcursionFormScreen
import com.asociacionciguena.app.presentation.screens.admin.excursions.ExcursionManagementScreen
import com.asociacionciguena.app.presentation.screens.admin.photos.PhotoUploadScreen
import com.asociacionciguena.app.presentation.screens.admin.users.UserManagementScreen
import com.asociacionciguena.app.presentation.screens.gallery.detail.ExcursionDetailScreen
import com.asociacionciguena.app.presentation.screens.onboarding.OnboardingScreen

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToNewsDetail: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState(initial = false)

    // Determinar pantalla inicial
    val startDestination = if (isOnboardingCompleted) {
        Screen.News.route
    } else {
        Screen.Onboarding.route
    }

    Scaffold(
        bottomBar = {
            // Solo mostrar bottom bar si NO estamos en onboarding
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            if (currentRoute != Screen.Onboarding.route) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            // NUEVO: Onboarding
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        // Navegar a pantalla principal después del onboarding
                        navController.navigate(Screen.News.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Noticias
            composable(Screen.News.route) {
                NewsScreen(
                    onNavigateToDetail = onNavigateToNewsDetail
                )
            }

            // Calendario
            composable(Screen.Calendar.route) {
                CalendarScreen()
            }

            // Login
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Gallery.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Perfil
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Profile.route) { inclusive = true }
                        }
                    },
                    onNavigateToAdminPanel = {
                        navController.navigate(Screen.AdminDashboard.route)
                    }
                )
            }

            // Admin Dashboard
            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    onNavigateToNews = {
                        navController.navigate(Screen.NewsManagement.route)
                    },
                    onNavigateToExcursions = {
                        navController.navigate(Screen.ExcursionManagement.route)
                    },
                    onNavigateToPhotos = {
                        navController.navigate(Screen.PhotoUpload.route)
                    },
                    onNavigateToUsers = {
                        navController.navigate(Screen.UserManagement.route)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.NewsManagement.route) {
                NewsManagementScreen(
                    onNavigateToForm = { newsId ->
                        navController.navigate(Screen.NewsForm.createRoute(newsId))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.NewsForm.route,
                arguments = listOf(
                    navArgument("newsId") { type = NavType.StringType }
                )
            ) {
                NewsFormScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.ExcursionManagement.route) {
                ExcursionManagementScreen(
                    onNavigateToForm = { excursionId ->
                        navController.navigate(Screen.ExcursionForm.createRoute(excursionId))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.ExcursionForm.route,
                arguments = listOf(
                    navArgument("excursionId") { type = NavType.StringType }
                )
            ) {
                ExcursionFormScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.PhotoUpload.route,
                arguments = listOf(
                    navArgument("excursionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                PhotoUploadScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.UserManagement.route) {
                UserManagementScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onNavigateToExcursionDetail = { excursionId ->
                        navController.navigate(Screen.ExcursionDetail.createRoute(excursionId))
                    }
                )
            }

            composable(
                route = Screen.ExcursionDetail.route,
                arguments = listOf(
                    navArgument("excursionId") { type = NavType.StringType }
                )
            ) {
                ExcursionDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUpload = { excursionId ->
                        navController.navigate(Screen.PhotoUpload.createRoute(excursionId))
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.route
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                }
            )
        }
    }
}