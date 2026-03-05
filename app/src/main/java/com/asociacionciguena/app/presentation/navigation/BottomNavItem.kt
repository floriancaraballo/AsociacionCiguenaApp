package com.asociacionciguena.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object News : BottomNavItem(
        route = Screen.News.route,
        title = "Publicaciones",
        icon = Icons.Default.Article
    )

    object Calendar : BottomNavItem(
        route = Screen.Calendar.route,
        title = "Calendario",
        icon = Icons.Default.CalendarMonth
    )

    // ACTUALIZADO: Ahora apunta a Gallery en lugar de Login
    object Gallery : BottomNavItem(
        route = Screen.Gallery.route,
        title = "Galería",
        icon = Icons.Default.PhotoLibrary  // Icono cambiado
    )

    object Profile : BottomNavItem(
        route = Screen.Profile.route,
        title = "Perfil",
        icon = Icons.Default.Person
    )
}

val bottomNavItems = listOf(
    BottomNavItem.News,
    BottomNavItem.Calendar,
    BottomNavItem.Gallery,  // Actualizado
    BottomNavItem.Profile
)