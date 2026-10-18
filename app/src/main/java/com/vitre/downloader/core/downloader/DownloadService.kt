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
import com.vitre.downloader.VitreApplication
import com.vitre.downloader.core.auth.CookieManager
import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.core.storage.MediaStoreManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.net.Uri
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var ytDlpEngine: YtDlpEngine
    private lateinit var ffmpegEngine: FFmpegEngine
    private lateinit var mediaStoreManager: MediaStoreManager

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeTempFiles = ConcurrentHashMap<Long, File>()

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
            val cancelId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
            if (cancelId != -1L) {
                handleCancelDownload(cancelId)
            }
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading Media"
        val formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: ""
        val isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
        val isImage = intent.getBooleanExtra(EXTRA_IS_IMAGE, false)
        val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT) ?: "mp3"
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, System.currentTimeMillis())
        val clipStart = if (intent.hasExtra(EXTRA_CLIP_START)) intent.getLongExtra(EXTRA_CLIP_START, -1L).takeIf { it >= 0 } else null
        val clipEnd = if (intent.hasExtra(EXTRA_CLIP_END)) intent.getLongExtra(EXTRA_CLIP_END, -1L).takeIf { it >= 0 } else null

        val isFirstJob = activeJobs.isEmpty()
        if (isFirstJob) {
            startForegroundServiceNotification(title, downloadId)
        } else {
            val notif = createNotification(downloadId, title, "Starting download...", 0, true)
            notificationManager.notify(getNotificationId(downloadId), notif)
        }

        val job = serviceScope.launch {
            processDownload(downloadId, url, title, formatId, isAudioOnly, isImage, audioFormat, clipStart, clipEnd)
        }
        activeJobs[downloadId] = job

        return START_NOT_STICKY
    }

    private fun handleCancelDownload(id: Long) {
        val notifId = getNotificationId(id)
        // 1. Touch cooperative cancel file
        activeTempFiles[id]?.let { file ->
            try {
                File("${file.absolutePath}.cancel").createNewFile()
            } catch (_: Exception) {}
        }

        // 2. Cancel coroutine job
        activeJobs[id]?.cancel()

        // 3. Update database status
        val db = (application as VitreApplication).database
        serviceScope.launch {
            db.downloadDao().updateStatus(id, DownloadStatus.CANCELLED.name)
        }

        // 4. Cancel notification
        notificationManager.cancel(notifId)

        // 5. Clean up tracking
        cleanupAndFinish(id, listOfNotNull(activeTempFiles[id]))
    }

    private fun startForegroundServiceNotification(title: String, downloadId: Long) {
        val notifId = getNotificationId(downloadId)
        val notification = createNotification(downloadId, title, "Starting download...", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notifId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notifId, notification)
        }
    }

    private suspend fun processDownload(
        id: Long,
        url: String,
        title: String,
        formatId: String,
        isAudioOnly: Boolean,
        isImage: Boolean,
        audioFormat: String,
        clipStart: Long?,
        clipEnd: Long?
    ) {
        val db = (application as VitreApplication).database
        val downloadDao = db.downloadDao()
        val notifId = getNotificationId(id)

        val safeTitle = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val tempDir = File(cacheDir, "downloads").apply { mkdirs() }
        val tempFile = File(tempDir, "${id}_${safeTitle}.tmp")
        activeTempFiles[id] = tempFile

        var lastProgressTime = 0L
        var lastReportedPct = -1f

        // 1. Download via yt-dlp with imported cookies support
        val cookiesPath = CookieManager(this).getCookieFilePath()
        val dlResult = ytDlpEngine.downloadMedia(
            url = url,
            formatId = formatId,
            outputPath = tempFile.absolutePath,
            cookiesPath = cookiesPath,
            clipStart = clipStart,
            clipEnd = clipEnd,
            isAudioOnly = isAudioOnly,
            audioFormat = audioFormat
        ) { progress ->
            if (activeJobs.containsKey(id)) {
                val now = System.currentTimeMillis()
                val pct = progress.percentage
                if (now - lastProgressTime >= 400L || Math.abs(pct - lastReportedPct) >= 0.02f || pct >= 0.99f) {
                    lastProgressTime = now
                    lastReportedPct = pct
                    val progressText = "${progress.formattedSpeed} • ETA ${progress.formattedEta}"
                    val notif = createNotification(id, title, progressText, (pct * 100).toInt(), false)
                    notificationManager.notify(notifId, notif)

                    serviceScope.launch {
                        downloadDao.updateProgress(id, pct, DownloadStatus.DOWNLOADING.name)
                    }
                }
            }
        }

        if (dlResult.isFailure || !activeJobs.containsKey(id)) {
            val isCancelled = !activeJobs.containsKey(id) || dlResult.exceptionOrNull() is CancellationException
            val status = if (isCancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED
            val msg = if (isCancelled) "Download cancelled" else "Download failed"
            val errorNotif = createNotification(id, title, msg, 0, false)
            notificationManager.notify(notifId, errorNotif)
            downloadDao.updateStatus(id, status.name)
            cleanupAndFinish(id, listOf(tempFile, File("${tempFile.absolutePath}.video.tmp"), File("${tempFile.absolutePath}.audio.tmp")))
            return
        }

        val engineRes = dlResult.getOrNull()!!
        var finalFile = tempFile

        // 2. Mux video and audio if downloaded as separate DASH streams
        if (engineRes.needsMuxing && engineRes.videoPath != null && engineRes.audioPath != null) {
            val muxNotif = createNotification(id, title, "Synchronizing audio & video tracks...", 95, false)
            notificationManager.notify(notifId, muxNotif)

            val muxResult = ffmpegEngine.muxVideoAndAudio(
                videoPath = engineRes.videoPath,
                audioPath = engineRes.audioPath,
                outputPath = tempFile.absolutePath
            )

            // Clean up temporary audio/video parts
            try { File(engineRes.videoPath).delete() } catch (_: Exception) {}
            try { File(engineRes.audioPath).delete() } catch (_: Exception) {}

            if (muxResult.isFailure || !tempFile.exists() || tempFile.length() < 1024) {
                val failNotif = createNotification(id, title, "Muxing audio & video tracks failed", 0, false)
                notificationManager.notify(notifId, failNotif)
                downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
                cleanupAndFinish(id, listOf(tempFile))
                return
            }
        }

        // 3. Audio extraction if user requested audio-only mode and not already an audio file
        if (isAudioOnly) {
            val audioTempFile = File(tempDir, "${id}_${safeTitle}.$audioFormat")
            val extractResult = ffmpegEngine.extractAudio(
                inputPath = finalFile.absolutePath,
                outputPath = audioTempFile.absolutePath,
                audioFormat = audioFormat
            )
            if (extractResult.isSuccess && audioTempFile.exists() && audioTempFile.length() > 1024) {
                finalFile.delete()
                finalFile = audioTempFile
            } else {
                // If extraction failed because input was already an audio stream, verify it
                if (!finalFile.exists() || finalFile.length() < 1024) {
                    val failNotif = createNotification(id, title, "Audio extraction failed", 0, false)
                    notificationManager.notify(notifId, failNotif)
                    downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
                    cleanupAndFinish(id, listOf(finalFile, audioTempFile))
                    return
                }
            }
        }

        // 4. Video Clipper: Trim file if user requested a specific time slice
        if (clipStart != null && clipEnd != null && clipEnd > clipStart && !isAudioOnly) {
            val trimNotif = createNotification(id, title, "Clipping video segment...", 98, false)
            notificationManager.notify(notifId, trimNotif)

            val clippedFile = File(tempDir, "${id}_${safeTitle}_clip_${clipStart}s_${clipEnd}s.mp4")
            val trimResult = ffmpegEngine.trimMedia(
                inputPath = finalFile.absolutePath,
                outputPath = clippedFile.absolutePath,
                startMs = clipStart * 1000L,
                endMs = clipEnd * 1000L
            )
            if (trimResult.isSuccess && clippedFile.exists() && clippedFile.length() > 1024) {
                finalFile.delete()
                finalFile = clippedFile
            } else {
                val failNotif = createNotification(id, title, "Trimming video clip failed", 0, false)
                notificationManager.notify(notifId, failNotif)
                downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
                cleanupAndFinish(id, listOf(finalFile, clippedFile))
                return
            }
        }

        // 5. Detect container type to ensure correct extension and MIME type
        val isWebMFile = if (!isImage) isWebM(finalFile) else false
        val (imageExt, imageMime) = if (isImage) detectImageExtension(finalFile, formatId, title) else Pair("jpg", "image/jpeg")

        val ext = when {
            isImage -> imageExt
            isAudioOnly -> audioFormat
            isWebMFile -> "webm"
            else -> "mp4"
        }
        val mimeType = when {
            isImage -> imageMime
            isAudioOnly -> when (audioFormat) {
                "m4a", "aac" -> "audio/mp4"
                "opus", "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
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
                    isVideo = !isAudioOnly && !isImage,
                    isImage = isImage
                )
            } else {
                customRes
            }
        } else {
            mediaStoreManager.saveMediaToPublicStorage(
                sourceFile = finalFile,
                displayName = finalDisplayName,
                mimeType = mimeType,
                isVideo = !isAudioOnly && !isImage,
                isImage = isImage
            )
        }

        finalFile.delete()

        if (saveResult.isSuccess) {
            val savedUri = saveResult.getOrNull()
            if (savedUri != null) {
                downloadDao.updateMediaUri(id, savedUri.toString())
            }
            val completeNotif = createNotification(id, title, "Saved to device storage", 100, false)
            notificationManager.notify(notifId, completeNotif)
            downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
        } else {
            val failNotif = createNotification(id, title, "Failed to save file to storage", 0, false)
            notificationManager.notify(notifId, failNotif)
            downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
        }

        cleanupAndFinish(id, listOf(finalFile))
    }

    private fun detectImageExtension(file: File, formatId: String, title: String): Pair<String, String> {
        try {
            if (file.exists() && file.length() >= 12) {
                file.inputStream().use { stream ->
                    val header = ByteArray(12)
                    stream.read(header)
                    // PNG: 89 50 4E 47
                    if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                        header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                        return Pair("png", "image/png")
                    }
                    // GIF: GIF87a or GIF89a
                    if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte()) {
                        return Pair("gif", "image/gif")
                    }
                    // WEBP: RIFF....WEBP
                    if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                        header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                        header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()) {
                        return Pair("webp", "image/webp")
                    }
                    // JPEG: FF D8 FF
                    if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                        return Pair("jpg", "image/jpeg")
                    }
                }
            }
        } catch (_: Exception) {}

        val lower = (formatId + " " + title).lowercase()
        return when {
            lower.contains("png") -> Pair("png", "image/png")
            lower.contains("webp") -> Pair("webp", "image/webp")
            lower.contains("gif") -> Pair("gif", "image/gif")
            else -> Pair("jpg", "image/jpeg")
        }
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

    private fun cleanupAndFinish(id: Long, filesToDelete: List<File> = emptyList()) {
        filesToDelete.forEach { f ->
            try { if (f.exists()) f.delete() } catch (_: Exception) {}
            try {
                val cancelF = File("${f.absolutePath}.cancel")
                if (cancelF.exists()) cancelF.delete()
            } catch (_: Exception) {}
        }
        activeJobs.remove(id)
        activeTempFiles.remove(id)

        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun createNotification(
        downloadId: Long,
        title: String,
        contentText: String,
        progress: Int,
        isIndeterminate: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, (downloadId % 10000).toInt(), openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        val pendingCancel = PendingIntent.getService(
            this, (downloadId % 10000).toInt() + 20000, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, VitreApplication.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingOpenApp)
            .setSilent(true)

        if (progress in 1..99) {
            builder.setOngoing(true)
            builder.setProgress(100, progress, isIndeterminate)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
        } else if (progress == 100) {
            builder.setOngoing(false)
            builder.setProgress(0, 0, false)
        } else {
            builder.setOngoing(isIndeterminate)
            builder.setProgress(100, 0, isIndeterminate)
            if (isIndeterminate) {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            }
        }

        return builder.build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun getNotificationId(downloadId: Long): Int = (downloadId % 100000).toInt() + 1000

        const val ACTION_CANCEL = "com.vitre.downloader.ACTION_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_FORMAT_ID = "EXTRA_FORMAT_ID"
        const val EXTRA_IS_AUDIO = "EXTRA_IS_AUDIO"
        const val EXTRA_IS_IMAGE = "EXTRA_IS_IMAGE"
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
            clipEnd: Long? = null,
            isImage: Boolean = false
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, id)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_ID, formatId)
                putExtra(EXTRA_IS_AUDIO, isAudioOnly)
                putExtra(EXTRA_IS_IMAGE, isImage)
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
