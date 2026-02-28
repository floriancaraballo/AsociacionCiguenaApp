package com.asociacionciguena.app.presentation.screens.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asociacionciguena.app.presentation.navigation.BottomNavItem
import com.asociacionciguena.app.presentation.navigation.Screen
import com.asociacionciguena.app.presentation.navigation.bottomNavItems
import com.asociacionciguena.app.presentation.screens.news.NewsScreen
import com.asociacionciguena.app.presentation.screens.calendar.CalendarScreen
import com.asociacionciguena.app.presentation.screens.auth.LoginScreen
import com.asociacionciguena.app.presentation.screens.gallery.GalleryScreen
import com.asociacionciguena.app.presentation.screens.profile.ProfileScreen
import com.asociacionciguena.app.presentation.screens.admin.dashboard.AdminDashboardScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.asociacionciguena.app.presentation.screens.admin.news.NewsFormScreen
import com.asociacionciguena.app.presentation.screens.admin.news.NewsManagementScreen
import com.asociacionciguena.app.presentation.screens.admin.excursions.ExcursionFormScreen
import com.asociacionciguena.app.presentation.screens.admin.excursions.ExcursionManagementScreen
import com.asociacionciguena.app.presentation.screens.admin.photos.PhotoUploadScreen

/**
 * Pantalla principal con Bottom Navigation Bar
 */
@Composable
fun MainScreen(
    onNavigateToNewsDetail: (String) -> Unit = {}) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.News.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Noticias
            composable(Screen.News.route) {
                NewsScreen(
                    onNavigateToDetail = onNavigateToNewsDetail
                )
            }

            // Calendario (ACTUALIZADO)
            composable(Screen.Calendar.route) {
                CalendarScreen()  // ← Cambia de PlaceholderScreen a CalendarScreen
            }

            // Área Privada / Login (temporal - placeholder)
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        // Navegar a galería cuando login exitoso
                        navController.navigate(Screen.Gallery.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Perfil (ACTUALIZADO con navegación admin)
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
            // Admin Dashboard (NUEVO)
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

            // Admin News Form
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

            // Admin Excursion Management
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

// Admin Excursion Form
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

            // Admin Photo Upload
            composable(Screen.PhotoUpload.route) {
                PhotoUploadScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            // Gallery (ACTUALIZADO)
            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Gallery.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Barra de navegación inferior
 */
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
                        // Pop hasta el start destination
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Evitar múltiples copias de la misma pantalla
                        launchSingleTop = true
                        // Restaurar estado al volver
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

/**
 * Pantalla placeholder temporal
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = "Pantalla de $title\n(Próximamente)",
                style = MaterialTheme.typography.titleLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}