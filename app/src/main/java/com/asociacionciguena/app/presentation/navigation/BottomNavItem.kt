package com.asociacionciguena.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Items de la barra de navegación inferior
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object News : BottomNavItem(
        route = Screen.News.route,
        title = "Noticias",
        icon = Icons.Default.Article
    )

    object Calendar : BottomNavItem(
        route = Screen.Calendar.route,
        title = "Calendario",
        icon = Icons.Default.CalendarMonth
    )

    object MembersArea : BottomNavItem(
        route = Screen.Login.route,
        title = "Área Privada",
        icon = Icons.Default.Lock
    )

    object Profile : BottomNavItem(
        route = Screen.Profile.route,
        title = "Perfil",
        icon = Icons.Default.Person
    )
}

/**
 * Lista de items de navegación
 */
val bottomNavItems = listOf(
    BottomNavItem.News,
    BottomNavItem.Calendar,
    BottomNavItem.MembersArea,
    BottomNavItem.Profile
)