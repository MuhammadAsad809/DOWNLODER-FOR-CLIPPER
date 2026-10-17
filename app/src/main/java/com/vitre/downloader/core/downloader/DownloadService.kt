package com.vitre.downloader.core.downloader

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vitre.downloader.MainActivity
import com.vitre.downloader.R
import com.vitre.downloader.VitreApplication
import com.vitre.downloader.core.database.DownloadEntity
import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.core.storage.MediaStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.net.Uri
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var ytDlpEngine: YtDlpEngine
    private lateinit var ffmpegEngine: FFmpegEngine
    private lateinit var mediaStoreManager: MediaStoreManager

    private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ytDlpEngine = YtDlpEngine()
        ffmpegEngine = FFmpegEngine()
        mediaStoreManager = MediaStoreManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL) {
            isCancelled = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading Media"
        val formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: ""
        val isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
        val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT) ?: "mp3"
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, System.currentTimeMillis())
        val clipStart = if (intent.hasExtra(EXTRA_CLIP_START)) intent.getLongExtra(EXTRA_CLIP_START, -1L).takeIf { it >= 0 } else null
        val clipEnd = if (intent.hasExtra(EXTRA_CLIP_END)) intent.getLongExtra(EXTRA_CLIP_END, -1L).takeIf { it >= 0 } else null

        startForegroundServiceNotification(title)

        serviceScope.launch {
            processDownload(downloadId, url, title, formatId, isAudioOnly, audioFormat, clipStart, clipEnd)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification(title: String) {
        val notification = createNotification(title, "Starting download...", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun processDownload(
        id: Long,
        url: String,
        title: String,
        formatId: String,
        isAudioOnly: Boolean,
        audioFormat: String,
        clipStart: Long?,
        clipEnd: Long?
    ) {
        val db = (application as VitreApplication).database
        val downloadDao = db.downloadDao()

        val safeTitle = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val tempDir = File(cacheDir, "downloads").apply { mkdirs() }
        val tempFile = File(tempDir, "$safeTitle.tmp")

        // 1. Download via yt-dlp
        val dlResult = ytDlpEngine.downloadMedia(
            url = url,
            formatId = formatId,
            outputPath = tempFile.absolutePath,
            cookiesPath = null,
            clipStart = clipStart,
            clipEnd = clipEnd
        ) { progress ->
            if (!isCancelled) {
                val progressText = "${progress.formattedSpeed} • ETA ${progress.formattedEta}"
                val notif = createNotification(title, progressText, (progress.percentage * 100).toInt(), false)
                notificationManager.notify(NOTIFICATION_ID, notif)

                serviceScope.launch {
                    downloadDao.updateProgress(id, progress.percentage, DownloadStatus.DOWNLOADING.name)
                }
            }
        }

        if (dlResult.isFailure || isCancelled) {
            val errorNotif = createNotification(title, "Download failed or was cancelled", 0, false)
            notificationManager.notify(NOTIFICATION_ID, errorNotif)
            downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
            stopSelf()
            return
        }

        val engineRes = dlResult.getOrNull()!!
        var finalFile = tempFile

        // 2. Mux video and audio if downloaded as separate DASH streams
        if (engineRes.needsMuxing && engineRes.videoPath != null && engineRes.audioPath != null) {
            val muxNotif = createNotification(title, "Synchronizing audio & video tracks...", 95, false)
            notificationManager.notify(NOTIFICATION_ID, muxNotif)

            val muxResult = ffmpegEngine.muxVideoAndAudio(
                videoPath = engineRes.videoPath,
                audioPath = engineRes.audioPath,
                outputPath = tempFile.absolutePath
            )

            // Clean up temporary audio/video parts
            try { File(engineRes.videoPath).delete() } catch (_: Exception) {}
            try { File(engineRes.audioPath).delete() } catch (_: Exception) {}
        }

        // 3. Audio extraction if user requested audio-only mode
        if (isAudioOnly) {
            val audioTempFile = File(tempDir, "$safeTitle.$audioFormat")
            val extractResult = ffmpegEngine.extractAudio(
                inputPath = finalFile.absolutePath,
                outputPath = audioTempFile.absolutePath,
                audioFormat = audioFormat
            )
            if (extractResult.isSuccess) {
                finalFile.delete()
                finalFile = audioTempFile
            }
        }

        // 4. Video Clipper: Trim file if user requested a specific time slice
        if (clipStart != null && clipEnd != null && clipEnd > clipStart && !isAudioOnly) {
            val trimNotif = createNotification(title, "Clipping video segment...", 98, false)
            notificationManager.notify(NOTIFICATION_ID, trimNotif)

            val clippedFile = File(tempDir, "${safeTitle}_clip_${clipStart}s_${clipEnd}s.mp4")
            val trimResult = ffmpegEngine.trimMedia(
                inputPath = finalFile.absolutePath,
                outputPath = clippedFile.absolutePath,
                startMs = clipStart * 1000L,
                endMs = clipEnd * 1000L
            )
            if (trimResult.isSuccess && clippedFile.exists() && clippedFile.length() > 0) {
                finalFile.delete()
                finalFile = clippedFile
            }
        }

        // 5. Detect container type to ensure correct extension and MIME type
        val isWebMFile = isWebM(finalFile)
        val ext = when {
            isAudioOnly -> audioFormat
            isWebMFile -> "webm"
            else -> "mp4"
        }
        val mimeType = when {
            isAudioOnly -> "audio/$audioFormat"
            isWebMFile -> "video/webm"
            else -> "video/mp4"
        }

        val clipSuffix = if (clipStart != null && clipEnd != null) "_clip" else ""
        val finalDisplayName = "$safeTitle$clipSuffix.$ext"

        val prefs = (application as VitreApplication).preferences
        val customUriStr = prefs.customDownloadUri.firstOrNull()
        val customUri = customUriStr?.let { Uri.parse(it) }

        val saveResult = if (customUri != null) {
            val customRes = mediaStoreManager.saveToCustomFolder(
                treeUri = customUri,
                displayName = finalDisplayName,
                mimeType = mimeType,
                sourceFile = finalFile
            )
            // Fallback to MediaStore if custom folder access fails (e.g. unmounted SD card)
            if (customRes.isFailure) {
                mediaStoreManager.saveMediaToPublicStorage(
                    sourceFile = finalFile,
                    displayName = finalDisplayName,
                    mimeType = mimeType,
                    isVideo = !isAudioOnly
                )
            } else {
                customRes
            }
        } else {
            mediaStoreManager.saveMediaToPublicStorage(
                sourceFile = finalFile,
                displayName = finalDisplayName,
                mimeType = mimeType,
                isVideo = !isAudioOnly
            )
        }

        finalFile.delete()

        if (saveResult.isSuccess) {
            val savedUri = saveResult.getOrNull()
            if (savedUri != null) {
                downloadDao.updateMediaUri(id, savedUri.toString())
            }
            val completeNotif = createNotification(title, "Saved to device storage", 100, false)
            notificationManager.notify(NOTIFICATION_ID, completeNotif)
            downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
        } else {
            downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
        }

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun isWebM(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 4) return false
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header)
                header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createNotification(
        title: String,
        contentText: String,
        progress: Int,
        isIndeterminate: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancel = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VitreApplication.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress < 100 && progress > 0)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, progress, isIndeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.vitre.downloader.ACTION_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_FORMAT_ID = "EXTRA_FORMAT_ID"
        const val EXTRA_IS_AUDIO = "EXTRA_IS_AUDIO"
        const val EXTRA_AUDIO_FORMAT = "EXTRA_AUDIO_FORMAT"
        const val EXTRA_CLIP_START = "EXTRA_CLIP_START"
        const val EXTRA_CLIP_END = "EXTRA_CLIP_END"

        fun startDownload(
            context: Context,
            id: Long,
            url: String,
            title: String,
            formatId: String,
            isAudioOnly: Boolean,
            audioFormat: String,
            clipStart: Long? = null,
            clipEnd: Long? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, id)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_ID, formatId)
                putExtra(EXTRA_IS_AUDIO, isAudioOnly)
                putExtra(EXTRA_AUDIO_FORMAT, audioFormat)
                if (clipStart != null) putExtra(EXTRA_CLIP_START, clipStart)
                if (clipEnd != null) putExtra(EXTRA_CLIP_END, clipEnd)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
