package com.asociacionciguena.app.presentation.screens.news.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asociacionciguena.app.domain.model.News
import com.asociacionciguena.app.presentation.components.CachedSubcomposeAsyncImage
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** La portada destaca la última publicación; las demás se muestran como filas compactas. */
@Composable
fun NewsCard(
    news: News,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFeatured: Boolean = false
) {
    if (isFeatured) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            news.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                CachedSubcomposeAsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Más reciente",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                NewsDate(news)
                Text(
                    news.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = news.shortDescription.ifBlank { news.content.take(150) }
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ReadPublicationLabel()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                val imageUrl = news.imageUrl?.takeIf { it.isNotBlank() }
                if (imageUrl != null) {
                    CachedSubcomposeAsyncImage(
                        imageUrl = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    NewsCalendarDate(news)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (imageUrl != null) {
                        NewsDate(news)
                    }
                    Text(
                        news.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    ReadPublicationLabel()
                }
            }
        }
    }
}

@Composable
private fun NewsCalendarDate(news: News) {
    val date = news.createdAt.toJavaLocalDateTime()
    val month = date.format(newsMonthFormatter).removeSuffix(".").uppercase(newsLocale)

    Surface(
        modifier = Modifier
            .width(64.dp)
            .heightIn(min = 64.dp)
            .clearAndSetSemantics {
                contentDescription = "Publicado el ${date.format(newsDateFormatter)}"
            },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = month,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NewsDate(news: News) {
    Text(
        news.createdAt.toJavaLocalDateTime().format(newsDateFormatter),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** La tarjeta completa es un único destino táctil y accesible. */
@Composable
private fun ReadPublicationLabel() {
    Row(
        modifier = Modifier.heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Leer publicación",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val newsLocale = Locale.forLanguageTag("es-ES")
private val newsDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", newsLocale)
private val newsMonthFormatter = DateTimeFormatter.ofPattern("MMM", newsLocale)
