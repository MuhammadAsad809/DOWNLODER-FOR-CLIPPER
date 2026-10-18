package com.vitre.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vitre.downloader.core.auth.CookieManager
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.database.HistoryEntity
import com.vitre.downloader.core.downloader.DownloadService
import com.vitre.downloader.core.downloader.YtDlpEngine
import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.core.models.MediaInfo
import com.vitre.downloader.core.storage.MediaResolver
import com.vitre.downloader.ui.components.AdaptivePillNavBar
import com.vitre.downloader.ui.components.NavDestination
import com.vitre.downloader.ui.screens.*
import com.vitre.downloader.ui.theme.OledBlack
import com.vitre.downloader.ui.theme.VitreTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val ytDlpEngine by lazy { YtDlpEngine() }
    private val cookieManager by lazy { CookieManager(this) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request storage write permission on Android 8-9 (API 26-28)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Handle shared URL from external apps (e.g. YouTube / Browser)
        val sharedUrl = extractSharedUrl(intent)

        setContent {
            val app = application as VitreApplication
            val scope = rememberCoroutineScope()

            // Database Flows
            val downloads by app.database.downloadDao().getAllDownloads().collectAsState(initial = emptyList())
            val historyList by app.database.historyDao().getAllHistory().collectAsState(initial = emptyList())

            // Preferences Flows
            val defaultQuality by app.preferences.defaultQuality.collectAsState(initial = "best")
            val defaultAudioFormat by app.preferences.defaultAudioFormat.collectAsState(initial = "mp3")
            val glassIntensity by app.preferences.glassIntensity.collectAsState(initial = 0.7f)
            val enableAgsl by app.preferences.enableAgsl.collectAsState(initial = true)
            val customDownloadUri by app.preferences.customDownloadUri.collectAsState(initial = null)

            // Navigation State
            var currentNav by remember { mutableStateOf(NavDestination.HOME) }
            var activeSubScreen by remember { mutableStateOf<String?>(null) }
            var activePlayerUri by remember { mutableStateOf<Pair<String, Uri>?>(null) }

            // Inspection State
            var urlInput by remember { mutableStateOf(sharedUrl ?: "") }
            var isInspecting by remember { mutableStateOf(false) }
            var inspectedMedia by remember { mutableStateOf<MediaInfo?>(null) }
            var inspectionError by remember { mutableStateOf<String?>(null) }

            val activeDownloadsCount = downloads.count {
                it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name
            }

            VitreTheme {
                Scaffold(
                    containerColor = OledBlack,
                    bottomBar = {
                        val isBottomBarVisible = activeSubScreen == null &&
                                activePlayerUri == null &&
                                inspectedMedia == null

                        AnimatedVisibility(
                            visible = isBottomBarVisible,
                            enter = slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { it } + fadeIn(tween(200)),
                            exit = slideOutVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { it } + fadeOut(tween(150))
                        ) {
                            AdaptivePillNavBar(
                                currentDestination = currentNav,
                                onNavigate = { destination ->
                                    currentNav = destination
                                },
                                activeDownloadsCount = activeDownloadsCount,
                                onActionOrbClick = {
                                    currentNav = NavDestination.HOME
                                },
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            activePlayerUri != null -> {
                                val (title, uri) = activePlayerUri!!
                                MediaPlayerScreen(
                                    title = title,
                                    mediaUri = uri,
                                    onNavigateBack = { activePlayerUri = null }
                                )
                            }
                            activeSubScreen == "cookies" -> {
                                CookieAuthScreen(
                                    onNavigateBack = { activeSubScreen = null }
                                )
                            }
                            activeSubScreen == "diagnostics" -> {
                                DiagnosticsScreen(
                                    onNavigateBack = { activeSubScreen = null }
                                )
                            }
                            currentNav == NavDestination.HOME -> {
                                HomeScreen(
                                    urlInput = urlInput,
                                    onUrlInputChange = { urlInput = it },
                                    isInspecting = isInspecting,
                                    onInspectClick = { url ->
                                        if (url.isNotBlank()) {
                                            isInspecting = true
                                            inspectionError = null
                                            scope.launch {
                                                val cookiesPath = cookieManager.getCookieFilePath()
                                                val res = ytDlpEngine.inspectUrl(url.trim(), cookiesPath)
                                                isInspecting = false
                                                if (res.isSuccess) {
                                                    val info = res.getOrNull()!!
                                                    inspectedMedia = info
                                                    // Save to History
                                                    app.database.historyDao().insertHistory(
                                                        HistoryEntity(
                                                            url = info.url,
                                                            title = info.title,
                                                            thumbnail = info.thumbnail,
                                                            duration = info.duration
                                                        )
                                                    )
                                                } else {
                                                    inspectionError = res.exceptionOrNull()?.message ?: "Failed to inspect link."
                                                }
                                            }
                                        }
                                    },
                                    mediaInfo = inspectedMedia,
                                    onDismissInspection = { inspectedMedia = null },
                                    onStartDownload = { info, format, isAudioOnly, audioFormat, clipStart, clipEnd ->
                                        inspectedMedia = null
                                        val downloadId = System.currentTimeMillis()

                                        scope.launch {
                                            val clipRangeStr = if (clipStart != null && clipEnd != null) {
                                                val sM = clipStart / 60
                                                val sS = clipStart % 60
                                                val eM = clipEnd / 60
                                                val eS = clipEnd % 60
                                                String.format("%02d:%02d - %02d:%02d", sM, sS, eM, eS)
                                            } else null

                                            val displayTitle = if (clipRangeStr != null) "${info.title} [Clip $clipRangeStr]" else info.title

                                            // 1. Insert download record in DB
                                            app.database.downloadDao().insertDownload(
                                                DownloadEntity(
                                                    id = downloadId,
                                                    url = info.url,
                                                    title = displayTitle,
                                                    thumbnail = info.thumbnail,
                                                    formatId = format.formatId,
                                                    resolution = if (isAudioOnly) "Audio (${audioFormat.uppercase()})" else format.resolution,
                                                    filesize = format.filesize,
                                                    status = DownloadStatus.QUEUED.name,
                                                    progress = 0f,
                                                    clipRange = clipRangeStr
                                                )
                                            )

                                            // 2. Start Foreground Download Service
                                            DownloadService.startDownload(
                                                context = this@MainActivity,
                                                id = downloadId,
                                                url = info.url,
                                                title = displayTitle,
                                                formatId = format.formatId,
                                                isAudioOnly = isAudioOnly,
                                                audioFormat = audioFormat,
                                                clipStart = clipStart,
                                                clipEnd = clipEnd,
                                                isImage = format.isImage
                                            )

                                            // 3. Switch tab to Downloads
                                            currentNav = NavDestination.DOWNLOADS
                                        }
                                    },
                                    recentDownloads = downloads,
                                    errorMessage = inspectionError,
                                    onClearError = { inspectionError = null },
                                    defaultQuality = defaultQuality,
                                    defaultAudioFormat = defaultAudioFormat
                                )
                            }
                            currentNav == NavDestination.DOWNLOADS -> {
                                DownloadsScreen(
                                    downloads = downloads,
                                    onDeleteDownload = { item ->
                                        scope.launch { app.database.downloadDao().deleteDownload(item) }
                                    },
                                    onPlayMedia = { item ->
                                        scope.launch {
                                            val resolved = MediaResolver.resolveMediaUri(
                                                context = this@MainActivity,
                                                item = item,
                                                downloadDao = app.database.downloadDao()
                                            )
                                            if (resolved != null) {
                                                activePlayerUri = Pair(item.title, resolved)
                                            } else {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    "Media file not found on device storage",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                            }
                            currentNav == NavDestination.HISTORY -> {
                                HistoryScreen(
                                    historyList = historyList,
                                    onSelectHistory = { selectedUrl ->
                                        urlInput = selectedUrl
                                        currentNav = NavDestination.HOME
                                    },
                                    onClearHistory = {
                                        scope.launch { app.database.historyDao().clearHistory() }
                                    }
                                )
                            }
                            currentNav == NavDestination.SETTINGS -> {
                                SettingsScreen(
                                    currentQuality = defaultQuality,
                                    onQualityChange = { q -> scope.launch { app.preferences.setQuality(q) } },
                                    currentAudioFormat = defaultAudioFormat,
                                    onAudioFormatChange = { af -> scope.launch { app.preferences.setAudioFormat(af) } },
                                    customDownloadUri = customDownloadUri,
                                    onCustomFolderChange = { uri -> scope.launch { app.preferences.setCustomDownloadPath(uri) } },
                                    glassIntensity = glassIntensity,
                                    onGlassIntensityChange = { gi -> scope.launch { app.preferences.setGlassIntensity(gi) } },
                                    enableAgsl = enableAgsl,
                                    onEnableAgslChange = { ea -> scope.launch { app.preferences.setEnableAgsl(ea) } },
                                    hasCookies = cookieManager.hasCookies(),
                                    onNavigateCookies = { activeSubScreen = "cookies" },
                                    onNavigateDiagnostics = { activeSubScreen = "diagnostics" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
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
}
