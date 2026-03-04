package com.asociacionciguena.app.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // Launcher para solicitar permiso de notificaciones
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.enableNotifications()
        } else {
            viewModel.skipNotifications()
        }
    }

    // Observar estado y completar onboarding
    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Completed) {
            onComplete()
        }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pager de slides
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPage(
                    page = when (page) {
                        0 -> OnboardingPageData(
                            icon = Icons.Default.Article,
                            title = "Mantente Informado",
                            description = "Recibe las últimas noticias y novedades de la asociación"
                        )
                        1 -> OnboardingPageData(
                            icon = Icons.Default.CalendarMonth,
                            title = "Próximas Excursiones",
                            description = "Consulta el calendario y no te pierdas ninguna actividad"
                        )
                        else -> OnboardingPageData(
                            icon = Icons.Default.PhotoLibrary,
                            title = "Galería de Fotos",
                            description = "Revive los mejores momentos en nuestra galería"
                        )
                    }
                )
            }

            // Indicadores de página
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Box(modifier = Modifier.size(8.dp))
                        }
                    }
                }
            }

            // Botones de acción
            if (pagerState.currentPage == 2) {
                // Última página: mostrar opciones de notificaciones
                NotificationsPermissionPage(
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.enableNotifications()
                        }
                    },
                    onSkip = {
                        viewModel.skipNotifications()
                    },
                    isLoading = uiState is OnboardingUiState.NotificationsRequesting
                )
            } else {
                // Otras páginas: botón siguiente
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            // Ir a siguiente página
                            kotlinx.coroutines.MainScope().launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Siguiente")
                }

                TextButton(
                    onClick = { viewModel.skipNotifications() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Omitir")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPageData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotificationsPermissionPage(
    onEnableNotifications: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¿Activar notificaciones?",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Recibe avisos cuando haya nuevas noticias, excursiones o fotos",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onEnableNotifications,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activar Notificaciones")
            }
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Ahora no")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Podrás activarlas más tarde desde los ajustes de tu dispositivo",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class OnboardingPageData(
    val icon: ImageVector,
    val title: String,
    val description: String
)