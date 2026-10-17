package com.vitre.downloader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.downloader.DownloadService
import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.ui.components.GlassButton
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onPlayMedia: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Video", "Audio")

    val activeDownloads = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
    }

    val completedDownloads = remember(downloads, selectedTab) {
        downloads.filter { it.status == DownloadStatus.COMPLETED.name }.filter {
            when (selectedTab) {
                1 -> it.resolution.contains("p", ignoreCase = true)
                2 -> it.resolution.contains("audio", ignoreCase = true) || it.formatId.contains("audio", ignoreCase = true)
                else -> true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(OledBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp)
        ) {
            item {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Queue (${activeDownloads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(activeDownloads) { item ->
                    ActiveDownloadCard(
                        item = item,
                        onCancel = {
                            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_CANCEL
                            }
                            context.startService(cancelIntent)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Completed Library Section
            item {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Filter Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = OledBlack,
                                containerColor = CardSurface,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (completedDownloads.isEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        cornerRadius = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No completed downloads in this category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            } else {
                items(completedDownloads) { item ->
                    CompletedDownloadCard(
                        item = item,
                        onPlay = { onPlayMedia(item) },
                        onDelete = { onDeleteDownload(item) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    item: DownloadEntity,
    onCancel: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ErrorRed)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = ElectricBlue,
                trackColor = CardSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue
                )
                Text(
                    text = item.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(CardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = ElectricBlue)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val subtitle = if (item.clipRange != null) {
                    "${item.resolution} • Clip ${item.clipRange}"
                } else {
                    item.resolution
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.clipRange != null) ElectricBlue else TextSecondary
                )
            }

            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = ElectricBlue)
            }

            IconButton(onClick = {
                val uri = item.mediaUri?.let { Uri.parse(it) }
                if (uri != null) {
                    try {
                        val isAudio = item.resolution.contains("audio", ignoreCase = true)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open with"))
                    } catch (_: Exception) {}
                } else {
                    onPlay()
                }
            }) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open In External App", tint = TextSecondary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextTertiary)
            }
        }
    }
}
