package com.asociacionciguena.app.presentation.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun CachedSubcomposeAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    loading: @Composable () -> Unit = { ImageLoadingPlaceholder() }
) {
    val context = LocalContext.current
    val imageRequest = remember(context, imageUrl) {
        preloadedImageRequest(context, imageUrl)
    }
    val isCachedInMemory = context.imageLoader.memoryCache?.get(MemoryCache.Key(imageUrl)) != null
    val isCachedOnDisk by produceState<Boolean?>(initialValue = null, imageUrl, isCachedInMemory) {
        val cached = if (isCachedInMemory) {
            true
        } else {
            withContext(Dispatchers.IO) {
                context.imageLoader.diskCache?.openSnapshot(imageUrl)?.use { true } ?: false
            }
        }
        value = cached
    }

    if (isCachedInMemory || isCachedOnDisk == true || isCachedOnDisk == null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = { loading() }
        )
    }
}

fun preloadedImageRequest(
    context: android.content.Context,
    imageUrl: String
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .memoryCacheKey(imageUrl)
        .diskCacheKey(imageUrl)
        .crossfade(false)
        .build()
}
