package com.asociacionciguena.app.presentation.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.asociacionciguena.app.presentation.screens.splash.SplashScreen
import androidx.compose.runtime.LaunchedEffect
import com.asociacionciguena.app.presentation.screens.calendar.detail.CalendarExcursionDetailScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.presentation.theme.ThemeViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.asociacionciguena.app.presentation.screens.admin.authorizations.AuthorizationsListScreen
import android.net.Uri
import com.asociacionciguena.app.presentation.screens.calendar.detail.CalendarExcursionDetailViewModel
import com.asociacionciguena.app.presentation.screens.calendar.detail.SignatureScreen
import com.asociacionciguena.app.presentation.components.PaymentWebView
import com.asociacionciguena.app.presentation.screens.payment.PaymentScreen

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToNewsDetail: (String) -> Unit = {},
    themeViewModel: ThemeViewModel, // ← NUEVO
    notificationType: String? = null,  // ← NUEVO
    itemId: String? = null
) {
    val navController = rememberNavController()
    val initState by viewModel.initState.collectAsState()

    // Mostrar splash mientras carga
    when (val state = initState) {
        is AppInitState.Loading -> {
            SplashScreen(onNavigateToMain = {})
        }

        is AppInitState.Ready -> {
            val startDestination = if (state.isOnboardingCompleted) {
                Screen.News.route
            } else {
                Screen.Onboarding.route
            }

            // Deep linking interno (para excursiones y fotos)
            LaunchedEffect(notificationType, itemId) {
                if (!notificationType.isNullOrEmpty() && !itemId.isNullOrEmpty()) {
                    android.util.Log.d("DEEP_LINK_MAIN", "MainScreen - Type: $notificationType, ID: $itemId")

                    when (notificationType) {
                        "excursion" -> {
                            android.util.Log.d("DEEP_LINK_MAIN", "Navegando a CalendarExcursionDetail: $itemId")
                            navController.navigate(Screen.CalendarExcursionDetail.createRoute(itemId))
                        }
                        "photo" -> {
                            android.util.Log.d("DEEP_LINK_MAIN", "Navegando a ExcursionDetail: $itemId")
                            if (state.isUserLoggedIn) {
                                navController.navigate(Screen.ExcursionDetail.createRoute(itemId))
                            } else {
                                navController.navigate(Screen.Login.createRoute(returnTo = "gallery"))
                            }
                        }
                    }
                }
            }

            Scaffold(
                topBar = {
                    // Banner de sin conexión
                    val networkMonitor: com.asociacionciguena.app.util.NetworkMonitor = hiltViewModel<MainViewModel>().networkMonitor
                    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // No mostrar en onboarding
                    if (currentRoute != Screen.Onboarding.route && !isOnline) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sin conexión - Mostrando contenido guardado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    if (currentRoute != Screen.Onboarding.route) {
                        BottomNavigationBar(
                            navController = navController,
                            isUserLoggedIn = state.isUserLoggedIn
                        )
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

                    composable(
                        route = Screen.Login.route,
                        arguments = listOf(
                            navArgument("returnTo") {
                                type = NavType.StringType
                                defaultValue = "profile"
                            }
                        )
                    ) { backStackEntry ->
                        val returnTo = backStackEntry.arguments?.getString("returnTo") ?: "profile"

                        LoginScreen(
                            onLoginSuccess = {
                                // Navegar según de dónde vino
                                val destination = when (returnTo) {
                                    "gallery" -> Screen.Gallery.route
                                    else -> Screen.Profile.route
                                }

                                navController.navigate(destination) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
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
                        CalendarScreen(
                            onNavigateToExcursionDetail = { excursionId ->
                                navController.navigate(Screen.CalendarExcursionDetail.createRoute(excursionId))
                            }
                        )
                    }

                    // Detalle de excursión desde calendario (PÚBLICO)
                    composable(
                        route = Screen.CalendarExcursionDetail.route,
                        arguments = listOf(
                            navArgument("excursionId") { type = NavType.StringType }
                        )
                    ) {
                        CalendarExcursionDetailScreen(
                            navController = navController,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEditExcursion = { excursionId ->
                                navController.navigate(Screen.ExcursionForm.createRoute(excursionId))
                            },
                            onNavigateToAuthorizations = { excursionId, excursionTitle ->
                                navController.navigate(Screen.AuthorizationsList.createRoute(excursionId, excursionTitle))
                            }
                        )
                    }
                    // ───────── NUEVA RUTA: Pago con TPV (Redsys) ─────────
                    composable(
                        route = Screen.Payment.route,
                        arguments = listOf(
                            navArgument("excursionId") { type = NavType.StringType },
                            navArgument("amount") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val excursionId = backStackEntry.arguments?.getString("excursionId") ?: run {
                            navController.popBackStack()
                            return@composable
                        }

                        val amountStr = backStackEntry.arguments?.getString("amount") ?: "0"
                        val amount = amountStr.toDoubleOrNull() ?: 0.0

                        if (amount <= 0) {
                            navController.popBackStack()
                            return@composable
                        }

                        PaymentScreen(
                            excursionId = excursionId,
                            amount = amount,
                            onPaymentSuccess = {
                                navController.popBackStack()
                                android.widget.Toast.makeText(
                                    navController.context,
                                    "✅ Pago realizado correctamente",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            onPaymentError = { error ->
                                navController.popBackStack()
                                android.widget.Toast.makeText(
                                    navController.context,
                                    "❌ $error",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // ───────── NUEVA RUTA: Firma de autorización ─────────
                    composable(
                        route = Screen.Signature.route,  // "signature/{excursionId}"
                        arguments = listOf(
                            navArgument("excursionId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val excursionId = backStackEntry.arguments?.getString("excursionId")
                            ?: return@composable  // Si no hay ID, salir

                        val parentEntry = remember(backStackEntry) {
                            navController.getBackStackEntry(Screen.CalendarExcursionDetail.route)
                        }

                        val viewModel: CalendarExcursionDetailViewModel = hiltViewModel(parentEntry)

                        SignatureScreen(
                            excursionId = excursionId,
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }  // ← Este navController es el de MainScreen
                        )
                    }

                    composable(
                        route = Screen.AuthorizationsList.route,
                        arguments = listOf(
                            navArgument("excursionId") { type = NavType.StringType },
                            navArgument("excursionTitle") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val excursionId = backStackEntry.arguments?.getString("excursionId") ?: return@composable
                        val excursionTitle = backStackEntry.arguments?.getString("excursionTitle") ?: return@composable

                        AuthorizationsListScreen(
                            excursionId = excursionId,
                            excursionTitle = Uri.decode(excursionTitle),
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }

                    // Login
                    composable(
                        route = Screen.Gallery.route
                    ) { backStackEntry ->
                        if (state.isUserLoggedIn) {
                            // Forzar recarga cuando se vuelve a la pantalla
                            val viewModel: com.asociacionciguena.app.presentation.screens.gallery.GalleryViewModel = hiltViewModel()

                            LaunchedEffect(backStackEntry) {
                                viewModel.retry()
                            }

                            GalleryScreen(
                                viewModel = viewModel,
                                onNavigateToExcursionDetail = { excursionId ->
                                    navController.navigate(Screen.ExcursionDetail.createRoute(excursionId))
                                }
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Login.createRoute(returnTo = "gallery")) {
                                    popUpTo(Screen.Gallery.route) { inclusive = true }
                                }
                            }
                        }
                    }

                    // Perfil
                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            themeViewModel = themeViewModel,
                            onNavigateToLogin = {
                                navController.navigate(Screen.Login.createRoute(returnTo = "profile")) {
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

                    composable(
                        route = Screen.ExcursionDetail.route,
                        arguments = listOf(
                            navArgument("excursionId") { type = NavType.StringType }
                        )
                    ) {
                        // Proteger detalle de excursión también
                        if (state.isUserLoggedIn) {
                            ExcursionDetailScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToUpload = { excursionId ->
                                    navController.navigate(Screen.PhotoUpload.createRoute(excursionId))
                                }
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.ExcursionDetail.route) { inclusive = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    isUserLoggedIn: Boolean  // ← NUEVO PARÁMETRO
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            // Determinar ruta real según autenticación
            val actualRoute = if (item.route == Screen.Gallery.route && !isUserLoggedIn) {
                Screen.Login.createRoute(returnTo = "gallery")  // Pasar parámetro
            } else {
                item.route
            }

            val selected = currentDestination?.hierarchy?.any {
                it.route == item.route ||
                        (item.route == Screen.Gallery.route && it.route == Screen.Login.route)
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(actualRoute) {
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
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            )
        }
    }
}
