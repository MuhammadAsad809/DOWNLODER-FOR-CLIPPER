package com.vitre.downloader.core.models

data class MediaInfo(
    val title: String,
    val thumbnail: String,
    val duration: Long,
    val uploader: String,
    val extractor: String,
    val url: String,
    val formats: List<FormatOption>,
    val isImagePost: Boolean = false
)

data class FormatOption(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val height: Int = 0,
    val filesize: Long = 0L,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val vcodec: String = "none",
    val acodec: String = "none",
    val fps: Double = 0.0,
    val tbr: Double = 0.0,
    val directUrl: String = "",
    val needsAudio: Boolean = false,
    val isImage: Boolean = false
) {
    val displaySize: String
        get() = if (filesize > 0) {
            val mb = filesize / (1024.0 * 1024.0)
            if (mb >= 1000) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        } else {
            "Stream"
        }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgress(
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Double,
    val eta: Long,
    val progressFraction: Float? = null,
    val phase: String = "single"
) {
    val percentage: Float
        get() = progressFraction ?: if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) else 0f

    val formattedDownloaded: String
        get() {
            val mb = downloadedBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }

    val formattedTotal: String
        get() {
            if (totalBytes <= 0) return "--"
            val mb = totalBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }

    val formattedSpeed: String
        get() = if (speed > 0) {
            val mb = speed / (1024.0 * 1024.0)
            String.format("%.2f MB/s", mb)
        } else {
            "--"
        }

    val formattedEta: String
        get() = if (eta > 0) {
            val mins = eta / 60
            val secs = eta % 60
            if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        } else {
            "--"
        }
}
