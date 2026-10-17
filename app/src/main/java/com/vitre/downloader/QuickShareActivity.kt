package com.vitre.downloader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitre.downloader.core.auth.CookieManager
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.downloader.DownloadService
import com.vitre.downloader.core.downloader.YtDlpEngine
import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.core.models.FormatOption
import com.vitre.downloader.core.models.MediaInfo
import com.vitre.downloader.ui.components.GlassButton
import com.vitre.downloader.ui.components.GlassSurface
import com.vitre.downloader.ui.theme.*
import kotlinx.coroutines.launch

class QuickShareActivity : ComponentActivity() {

    private val ytDlpEngine by lazy { YtDlpEngine() }
    private val cookieManager by lazy { CookieManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native iOS-like background blur on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 45
            }
        }

        val sharedUrl = extractUrlFromIntent(intent)

        setContent {
            VitreTheme {
                QuickShareModalSheet(
                    sharedUrl = sharedUrl,
                    onDismiss = { finish() },
                    onStartDownload = { info, format, isAudio, audioFormat ->
                        startBackgroundDownload(info, format, isAudio, audioFormat)
                    },
                    onOpenInApp = { url ->
                        val appIntent = Intent(this, MainActivity::class.java).apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(appIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+[/#?]?.*)".toRegex()
                val match = urlRegex.find(text)
                return match?.value ?: text.trim()
            }
        }
        return null
    }

    private fun startBackgroundDownload(
        info: MediaInfo,
        format: FormatOption,
        isAudio: Boolean,
        audioFormat: String
    ) {
        val app = application as VitreApplication
        val downloadId = System.currentTimeMillis()

        val displayTitle = if (info.title.isNotBlank()) info.title else "Quick_Download_$downloadId"
        val resolutionLabel = when {
            format.isImage -> "Image"
            isAudio -> "Audio (${audioFormat.uppercase()})"
            else -> format.resolution
        }

        // 1. Insert into Room Database
        val entity = DownloadEntity(
            id = downloadId,
            url = info.url,
            title = displayTitle,
            thumbnail = info.thumbnail,
            formatId = format.formatId,
            resolution = resolutionLabel,
            filesize = format.filesize,
            status = DownloadStatus.QUEUED.name,
            progress = 0f
        )

        // Launch async insert and service
        val scope = (application as VitreApplication).applicationScope
        scope.launch {
            app.database.downloadDao().insertDownload(entity)

            DownloadService.startDownload(
                context = this@QuickShareActivity,
                id = downloadId,
                url = info.url,
                title = displayTitle,
                formatId = format.formatId,
                isAudioOnly = isAudio,
                audioFormat = audioFormat,
                isImage = format.isImage
            )
        }

        Toast.makeText(this, "Downloading \"$displayTitle\" in background...", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun QuickShareModalSheet(
    sharedUrl: String?,
    onDismiss: () -> Unit,
    onStartDownload: (MediaInfo, FormatOption, Boolean, String) -> Unit,
    onOpenInApp: (String) -> Unit
) {
    var isInspecting by remember { mutableStateOf(true) }
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val cookieManager = remember { CookieManager(context) }
    val ytDlpEngine = remember { YtDlpEngine() }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl.isNullOrBlank()) {
            isInspecting = false
            errorMessage = "No link detected in share"
            return@LaunchedEffect
        }

        isInspecting = true
        errorMessage = null
        val cookiesPath = cookieManager.getCookieFilePath()
        val result = ytDlpEngine.inspectUrl(sharedUrl, cookiesPath)
        isInspecting = false
        if (result.isSuccess) {
            mediaInfo = result.getOrNull()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Unable to inspect shared media"
        }
    }

    // Outer Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x77000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Translucent Liquid Glass Card
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click-through */ }
                ),
            cornerRadius = 24.dp,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextTertiary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vitre Quick Download",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isInspecting -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Extracting media streams...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!sharedUrl.isNullOrBlank()) {
                                    GlassButton(
                                        text = "Open in Vitre App",
                                        onClick = { onOpenInApp(sharedUrl) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                GlassButton(
                                    text = "Cancel",
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    mediaInfo != null -> {
                        val info = mediaInfo!!
                        val isImageOnly = info.isImagePost || (info.formats.any { it.isImage } && !info.formats.any { it.isVideo })
                        val videoFormats = remember(info) { info.formats.filter { it.isVideo } }
                        val audioFormats = remember(info) { info.formats.filter { it.isAudio && !it.isVideo } }
                        val imageFormats = remember(info) { info.formats.filter { it.isImage } }

                        var isAudioOnly by remember { mutableStateOf(false) }
                        var selectedFormat by remember {
                            mutableStateOf(
                                if (isImageOnly) (imageFormats.firstOrNull() ?: info.formats.firstOrNull())
                                else (videoFormats.firstOrNull() ?: info.formats.firstOrNull())
                            )
                        }
                        var selectedAudioFormat by remember { mutableStateOf("mp3") }

                        // Preview
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (info.thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${info.uploader} • ${info.extractor.replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Video vs Audio Toggle (if not image)
                        if (!isImageOnly && videoFormats.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardSurface)
                                    .padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (!isAudioOnly) ElectricBlue else Color.Transparent)
                                        .clickable { isAudioOnly = false }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Video",
                                        color = if (!isAudioOnly) OledBlack else TextSecondary,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isAudioOnly) ElectricBlue else Color.Transparent)
                                        .clickable { isAudioOnly = true }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Audio Only",
                                        color = if (isAudioOnly) OledBlack else TextSecondary,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Quality Chips
                        Text(
                            text = when {
                                isImageOnly -> "Image Quality"
                                isAudioOnly -> "Audio Format"
                                else -> "Select Quality"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isImageOnly) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(imageFormats) { fmt ->
                                    val isSelected = selectedFormat == fmt
                                    GlassSurface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedFormat = fmt },
                                        cornerRadius = 12.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(if (isSelected) ElectricBlueGlow else Color.Transparent)
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = fmt.resolution,
                                                color = if (isSelected) ElectricBlue else TextPrimary,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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
                        } else if (!isAudioOnly) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(videoFormats) { fmt ->
                                    val isSelected = selectedFormat == fmt
                                    GlassSurface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedFormat = fmt },
                                        cornerRadius = 12.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(if (isSelected) ElectricBlueGlow else Color.Transparent)
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = fmt.resolution,
                                                color = if (isSelected) ElectricBlue else TextPrimary,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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
                            val audioChoices = listOf("mp3", "m4a", "opus")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                audioChoices.forEach { fmt ->
                                    val isSelected = selectedAudioFormat == fmt
                                    GlassSurface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedAudioFormat = fmt },
                                        cornerRadius = 12.dp
                                    ) {
                                        Text(
                                            text = fmt.uppercase(),
                                            color = if (isSelected) ElectricBlue else TextPrimary,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier
                                                .background(if (isSelected) ElectricBlueGlow else Color.Transparent)
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // One-tap Download Action
                        GlassButton(
                            text = when {
                                isImageOnly -> "Save Image to Device"
                                isAudioOnly -> "Download Audio (${selectedAudioFormat.uppercase()})"
                                else -> "Download ${selectedFormat?.resolution ?: ""}"
                            },
                            onClick = {
                                val fmt = selectedFormat ?: info.formats.first()
                                onStartDownload(info, fmt, isAudioOnly, selectedAudioFormat)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isImageOnly) Icons.Default.Image else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = OledBlack
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
