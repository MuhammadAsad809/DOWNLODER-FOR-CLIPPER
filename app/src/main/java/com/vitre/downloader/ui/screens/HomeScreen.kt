package com.vitre.downloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.models.FormatOption
import com.vitre.downloader.core.models.MediaInfo
import com.vitre.downloader.ui.components.*
import com.vitre.downloader.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    isInspecting: Boolean,
    onInspectClick: (String) -> Unit,
    mediaInfo: MediaInfo?,
    onDismissInspection: () -> Unit,
    onStartDownload: (MediaInfo, FormatOption, Boolean, String) -> Unit,
    recentDownloads: List<DownloadEntity>,
    errorMessage: String?,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val supportedPlatforms = listOf("YouTube", "TikTok", "Instagram", "X / Twitter", "Facebook", "Reddit", "Pinterest", "SoundCloud", "Vimeo")

    Box(modifier = modifier.fillMaxSize().background(OledBlack)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp)
        ) {
            // Hero Title
            item {
                Text(
                    text = "Vitre",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricBlue,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Save what moves you.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // URL Input Card
            item {
                GlassTextField(
                    value = urlInput,
                    onValueChange = onUrlInputChange,
                    placeholder = "Paste supported media link...",
                    onPasteClick = {
                        val clip = clipboardManager.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text?.toString() ?: ""
                            onUrlInputChange(text)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(
                    text = if (isInspecting) "Inspecting..." else "Find Media",
                    onClick = { onInspectClick(urlInput) },
                    enabled = urlInput.isNotBlank(),
                    isLoading = isInspecting,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = OledBlack)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Error Notice
            if (errorMessage != null) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearError) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Supported Platforms Chips
            item {
                Text(
                    text = "Supported Sources",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supportedPlatforms) { platform ->
                        GlassSurface(cornerRadius = 12.dp, elevation = 1.dp) {
                            Text(
                                text = platform,
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Recent Downloads
            item {
                Text(
                    text = "Recent Downloads",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (recentDownloads.isEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        cornerRadius = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No downloads yet. Paste a link to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            } else {
                items(recentDownloads.take(5)) { item ->
                    RecentDownloadItem(item)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Inspection Bottom Sheet
        if (mediaInfo != null) {
            MediaInspectionSheet(
                mediaInfo = mediaInfo,
                onDismiss = onDismissInspection,
                onConfirm = onStartDownload
            )
        }
    }
}

@Composable
fun RecentDownloadItem(item: DownloadEntity) {
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
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(CardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ElectricBlue)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.resolution} • ${item.status}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInspectionSheet(
    mediaInfo: MediaInfo,
    onDismiss: () -> Unit,
    onConfirm: (MediaInfo, FormatOption, Boolean, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isAudioOnly by remember { mutableStateOf(false) }

    val videoFormats = remember(mediaInfo) { mediaInfo.formats.filter { it.isVideo } }
    val audioFormats = remember(mediaInfo) { mediaInfo.formats.filter { it.isAudio && !it.isVideo } }

    var selectedFormat by remember {
        mutableStateOf(videoFormats.firstOrNull() ?: mediaInfo.formats.firstOrNull())
    }
    var selectedAudioFormat by remember { mutableStateOf("mp3") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Media Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mediaInfo.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = mediaInfo.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${mediaInfo.uploader} • ${mediaInfo.extractor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Video vs Audio Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isAudioOnly) ElectricBlue else Color.Transparent)
                        .clickable { isAudioOnly = false }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video",
                        color = if (!isAudioOnly) OledBlack else TextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAudioOnly) ElectricBlue else Color.Transparent)
                        .clickable { isAudioOnly = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Audio Only",
                        color = if (isAudioOnly) OledBlack else TextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Format Options Selection
            Text(
                text = if (isAudioOnly) "Select Audio Format" else "Select Resolution",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!isAudioOnly) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videoFormats) { fmt ->
                        val isSelected = selectedFormat == fmt
                        GlassSurface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedFormat = fmt },
                            cornerRadius = 14.dp,
                            elevation = if (isSelected) 4.dp else 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(if (isSelected) ElectricBlueGlow else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = fmt.resolution,
                                    color = if (isSelected) ElectricBlue else TextPrimary,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = fmt.displaySize,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            } else {
                val formats = listOf("mp3", "m4a", "opus")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    formats.forEach { fmt ->
                        val isSelected = selectedAudioFormat == fmt
                        GlassSurface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedAudioFormat = fmt },
                            cornerRadius = 14.dp
                        ) {
                            Text(
                                text = fmt.uppercase(),
                                color = if (isSelected) ElectricBlue else TextPrimary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .background(if (isSelected) ElectricBlueGlow else Color.Transparent)
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Download Button
            GlassButton(
                text = "Start Download",
                onClick = {
                    val fmt = selectedFormat ?: mediaInfo.formats.first()
                    onConfirm(mediaInfo, fmt, isAudioOnly, selectedAudioFormat)
                },
                leadingIcon = {
                    Icon(Icons.Default.Download, contentDescription = null, tint = OledBlack)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
