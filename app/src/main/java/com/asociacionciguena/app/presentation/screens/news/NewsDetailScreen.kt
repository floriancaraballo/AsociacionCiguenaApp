package com.asociacionciguena.app.presentation.screens.news

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.ImageLoadingPlaceholder
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.components.ZoomableImage
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.filled.Share
import android.content.Context

@Composable
fun NewsDetailScreen(
    newsId: String,
    viewModel: NewsDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is NewsDetailUiState.Success -> {
                            Text(
                                text = state.news.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> Text("Noticia")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {  // ← NUEVO
                    when (val state = uiState) {
                        is NewsDetailUiState.Success -> {
                            val context = LocalContext.current
                            IconButton(
                                onClick = {
                                    shareNews(context, state.news)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Compartir",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is NewsDetailUiState.Loading -> {
                LoadingIndicator()
            }

            is NewsDetailUiState.Success -> {
                NewsDetailContent(
                    news = state.news,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is NewsDetailUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadNews(newsId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewsDetailContent(
    news: com.asociacionciguena.app.domain.model.News,
    modifier: Modifier = Modifier
) {
    var showFullscreenGallery by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableStateOf(0) }

    // Crear lista de TODAS las fotos (principal + adicionales)
    val allPhotos = buildList {
        news.imageUrl?.let { add(it) }
        addAll(news.additionalPhotos)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (news.imageUrl != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedPhotoIndex = 0  // Foto principal es índice 0
                        showFullscreenGallery = true
                    },
                shape = MaterialTheme.shapes.large
            ) {
                SubcomposeAsyncImage(
                    model = news.imageUrl,
                    contentDescription = news.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            ZoomableImage(
                                imageUrl = news.imageUrl,
                                contentDescription = news.title,
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = news.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDate(news.createdAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            Text(
                text = news.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (news.additionalPhotos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Más Fotos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                PhotoCarousel(
                    photos = news.additionalPhotos,
                    onPhotoClick = { index ->
                        // +1 porque la foto principal es el índice 0
                        selectedPhotoIndex = index + 1
                        showFullscreenGallery = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // NUEVO: Galería fullscreen navegable
    if (showFullscreenGallery && allPhotos.isNotEmpty()) {
        FullscreenGalleryDialog(
            photos = allPhotos,
            initialPage = selectedPhotoIndex,
            onDismiss = { showFullscreenGallery = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCarousel(
    photos: List<String>,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { photos.size })

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPhotoClick(page) }
            ) {
                SubcomposeAsyncImage(
                    model = photos[page],
                    contentDescription = "Foto ${page + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        ImageLoadingPlaceholder()
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(photos.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

// NUEVO: Galería fullscreen con HorizontalPager para navegar
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenGalleryDialog(
    photos: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    var isZoomed by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // HorizontalPager para swipe entre fotos
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed  // ← DESHABILITAR swipe si hay zoom
            ) { page ->
                ZoomableImage(
                    imageUrl = photos[page],
                    contentDescription = "Foto ${page + 1}",
                    contentScale = ContentScale.Fit,
                    onScaleChange = { scale ->
                        isZoomed = scale > 1f  // ← Actualizar estado de zoom
                    }
                )
            }

            // Botón cerrar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.3f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Contador de fotos (ej: 2/5)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Indicadores de página (puntos)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(photos.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected)
                                    Color.White
                                else
                                    Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Compartir noticia por WhatsApp, Email, etc.
 */
private fun shareNews(context: Context, news: com.asociacionciguena.app.domain.model.News) {
    val shareText = buildString {
        append("📰 ${news.title}\n")
        append("━━━━━━━━━━━━━━━━━━━━\n\n")
        append(news.content)  // ← CONTENIDO COMPLETO
        append("\n\n")
        append("━━━━━━━━━━━━━━━━━━━━\n")
        append("📱 Descarga la app de Asociación Cigüeña para más noticias y fotos de nuestras excursiones.")
    }

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, news.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Compartir noticia"))
}

private fun formatDate(date: kotlinx.datetime.LocalDateTime): String {
    val javaDate = date.toJavaLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    return javaDate.format(formatter).replaceFirstChar { it.uppercase() }
}
