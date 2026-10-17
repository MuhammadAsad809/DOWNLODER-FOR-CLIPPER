package com.vitre.downloader.core.models

data class MediaInfo(
    val title: String,
    val thumbnail: String,
    val duration: Long,
    val uploader: String,
    val extractor: String,
    val url: String,
    val formats: List<FormatOption>
)

data class FormatOption(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val height: Int,
    val filesize: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val vcodec: String,
    val acodec: String,
    val fps: Double,
    val tbr: Double,
    val directUrl: String = "",
    val needsAudio: Boolean = false
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
    val eta: Long
) {
    val percentage: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) else 0f

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
