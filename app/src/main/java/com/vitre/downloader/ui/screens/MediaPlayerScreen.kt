package com.vitre.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vitre.downloader.ui.components.GlassButton
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.vitre.downloader.core.storage.MediaResolver
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    title: String,
    mediaUri: Uri,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    val uriStr = mediaUri.toString().lowercase()
    val isImage = uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".png") ||
                  uriStr.endsWith(".webp") || uriStr.contains("image")
    val isAudio = uriStr.endsWith(".mp3") || uriStr.endsWith(".m4a") || uriStr.endsWith(".opus") || uriStr.contains("audio")

    val exoPlayer = remember(mediaUri, isImage) {
        if (!isImage) {
            ExoPlayer.Builder(context).build().apply {
                val item = MediaItem.fromUri(mediaUri)
                setMediaItem(item)
                prepare()
                playWhenReady = true
            }
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer != null) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.localizedMessage ?: "Unable to play video directly in player"
                    isBuffering = false
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }
            }
            exoPlayer.addListener(listener)

            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        } else {
            isBuffering = false
            onDispose {}
        }
    }

    fun getSafeUri(): Uri {
        return if (mediaUri.scheme == "file") {
            val file = File(mediaUri.path ?: "")
            if (file.exists()) MediaResolver.getShareableUri(context, file) else mediaUri
        } else {
            mediaUri
        }
    }

    fun openInExternalApp() {
        try {
            val safeUri = getSafeUri()
            val mime = when {
                isImage -> "image/*"
                isAudio -> "audio/*"
                else -> "video/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(safeUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {}
    }

    fun shareMedia() {
        try {
            val safeUri = getSafeUri()
            val mime = when {
                isImage -> "image/*"
                isAudio -> "audio/*"
                else -> "video/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, safeUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share media"))
        } catch (_: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize().background(OledBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { openInExternalApp() }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open in external app", tint = TextPrimary)
                }

                IconButton(onClick = { shareMedia() }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                }
            }

            // Player View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(OledBlack),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isBuffering && playbackError == null) {
                        CircularProgressIndicator(color = ElectricBlue)
                    }
                }

                if (playbackError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .background(OledBlack.copy(alpha = 0.85f))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playback Error",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playbackError ?: "Media format requires system player",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        GlassButton(
                            text = "Play in Gallery / External App",
                            onClick = { openInExternalApp() },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = OledBlack)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
