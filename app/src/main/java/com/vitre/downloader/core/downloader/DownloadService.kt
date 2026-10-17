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

        startForegroundServiceNotification(title)

        serviceScope.launch {
            processDownload(downloadId, url, title, formatId, isAudioOnly, audioFormat)
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
        audioFormat: String
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
            cookiesPath = null
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

        // 2. Audio extraction if needed
        var finalFile = tempFile
        if (isAudioOnly) {
            val audioTempFile = File(tempDir, "$safeTitle.$audioFormat")
            val extractResult = ffmpegEngine.extractAudio(
                inputPath = tempFile.absolutePath,
                outputPath = audioTempFile.absolutePath,
                audioFormat = audioFormat
            )
            if (extractResult.isSuccess) {
                tempFile.delete()
                finalFile = audioTempFile
            }
        }

        // 3. Save to Public MediaStore
        val finalDisplayName = if (isAudioOnly) "$safeTitle.$audioFormat" else "$safeTitle.mp4"
        val mimeType = if (isAudioOnly) "audio/$audioFormat" else "video/mp4"

        val saveResult = mediaStoreManager.saveMediaToPublicStorage(
            sourceFile = finalFile,
            displayName = finalDisplayName,
            mimeType = mimeType,
            isVideo = !isAudioOnly
        )

        finalFile.delete()

        if (saveResult.isSuccess) {
            val completeNotif = createNotification(title, "Saved to device storage", 100, false)
            notificationManager.notify(NOTIFICATION_ID, completeNotif)
            downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
        } else {
            downloadDao.updateStatus(id, DownloadStatus.FAILED.name)
        }

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
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

        fun startDownload(
            context: Context,
            id: Long,
            url: String,
            title: String,
            formatId: String,
            isAudioOnly: Boolean,
            audioFormat: String
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, id)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_ID, formatId)
                putExtra(EXTRA_IS_AUDIO, isAudioOnly)
                putExtra(EXTRA_AUDIO_FORMAT, audioFormat)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
