package com.asociacionciguena.app.presentation.screens.news

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.asociacionciguena.app.presentation.components.ErrorMessage
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
import com.asociacionciguena.app.presentation.components.ImageLoadingPlaceholder
import com.asociacionciguena.app.presentation.components.LoadingIndicator
import com.asociacionciguena.app.presentation.components.ZoomableImage
import com.asociacionciguena.app.util.formatDate

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
                    Text("Publicación")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
                LoadingIndicator(modifier = Modifier.fillMaxSize().padding(paddingValues))
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

@Composable
private fun NewsDetailContent(
    news: com.asociacionciguena.app.domain.model.News,
    modifier: Modifier = Modifier
) {
    var showFullscreenGallery by remember(news.id) { mutableStateOf(false) }
    var selectedPhotoIndex by remember(news.id) { mutableStateOf(0) }
    val allPhotos = remember(news.imageUrl, news.additionalPhotos) {
        buildList {
            news.imageUrl?.let { add(it) }
            addAll(news.additionalPhotos)
        }.filter { it.isNotBlank() }.distinct()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ASOCIACIÓN CIGÜEÑA",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = news.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (news.shortDescription.isNotBlank() && news.shortDescription.trim() != news.content.trim()) {
                        Text(
                            text = news.shortDescription,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = news.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (allPhotos.isNotEmpty()) {
                PhotoThumbnails(
                    photos = allPhotos,
                    onPhotoClick = { index ->
                        selectedPhotoIndex = index
                        showFullscreenGallery = true
                    }
                )
            }
        }
    }

    if (showFullscreenGallery && allPhotos.isNotEmpty()) {
        FullscreenGalleryDialog(
            photos = allPhotos,
            initialPage = selectedPhotoIndex.coerceIn(allPhotos.indices),
            onDismiss = { showFullscreenGallery = false }
        )
    }
}

@Composable
private fun PhotoThumbnails(
    photos: List<String>,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            Text(
                text = "Fotos de la publicación (${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(photos, key = { _, photo -> photo }) { index, photo ->
                    Card(
                        onClick = { onPhotoClick(index) },
                        modifier = Modifier.size(104.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        CachedSubcomposeAsyncImage(
                            imageUrl = photo,
                            contentDescription = "Ampliar foto ${index + 1} de ${photos.size}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = { ImageLoadingPlaceholder() }
                        )
                    }
                }
            }
            Text(
                text = "Toca una foto para ampliarla",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
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

    context.startActivity(Intent.createChooser(shareIntent, "Compartir publicación"))
}
