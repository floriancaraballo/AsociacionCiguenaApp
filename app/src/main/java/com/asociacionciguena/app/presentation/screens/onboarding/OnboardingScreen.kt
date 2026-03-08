package com.asociacionciguena.app.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // Estado para aceptación de términos
    var legalTermsAccepted by remember { mutableStateOf(false) }

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

    // 4 páginas: LEGAL + Intro + Galería + Notificaciones
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pager de slides
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = if (pagerState.currentPage == 0) legalTermsAccepted else true  // Bloquear scroll en primera página (legal)
            ) { page ->
                when (page) {
                    0 -> LegalTermsPage(  // ← PRIMERO: Aviso Legal
                        accepted = legalTermsAccepted,
                        onAcceptedChange = { legalTermsAccepted = it }
                    )
                    1 -> OnboardingPage(  // ← SEGUNDO: Noticias
                        page = OnboardingPageData(
                            icon = Icons.Default.Article,
                            title = "Mantente Informado",
                            description = "Recibe las últimas noticias y novedades de la asociación"
                        )
                    )
                    2 -> OnboardingPage(  // ← TERCERO: Excursiones
                        page = OnboardingPageData(
                            icon = Icons.Default.CalendarMonth,
                            title = "Próximas Excursiones",
                            description = "Consulta el calendario y no te pierdas ninguna actividad"
                        )
                    )
                    else -> NotificationsPermissionPageContent(  // ← CUARTO: Notificaciones
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
                }
            }

            // Indicadores de página
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
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
            if (pagerState.currentPage < 3) {
                // Páginas normales y legal: botón siguiente
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = if (pagerState.currentPage == 0) legalTermsAccepted else true  // Bloquear si es página legal (0) y no acepta
                ) {
                    Text("Siguiente")
                }

                // NO mostrar "Omitir" en página legal (0)
                if (pagerState.currentPage != 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(3)  // Saltar a notificaciones
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Omitir")
                    }
                }
            }
            // La página de notificaciones (página 3) tiene sus propios botones internos
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
private fun LegalTermsPage(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono
        Icon(
            imageVector = Icons.Default.Gavel,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Aviso Legal",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Contenido legal con scroll
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Aviso sobre Propiedad Intelectual y Derechos de Imagen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Todo el contenido fotográfico y visual disponible en esta aplicación es propiedad exclusiva de Asociación Cigüeña o cuenta con las autorizaciones pertinentes para su uso interno.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Queda estrictamente prohibida la reproducción, distribución, captura de pantalla o compartición de este material fuera del entorno de la aplicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "El acceso a estas imágenes es personal e intransferible. Al utilizar esta aplicación, el usuario acepta respetar la confidencialidad y los derechos de imagen aquí protegidos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checkbox de aceptación
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAcceptedChange(!accepted) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAcceptedChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "He leído y acepto no compartir el contenido multimedia",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NotificationsPermissionPageContent(
    onEnableNotifications: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "¿Activar notificaciones?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Recibe avisos cuando haya nuevas noticias, excursiones o fotos",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onEnableNotifications,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activar Notificaciones")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Ahora no")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Podrás activarlas más tarde desde los ajustes de tu dispositivo",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class OnboardingPageData(
    val icon: ImageVector,
    val title: String,
    val description: String
)