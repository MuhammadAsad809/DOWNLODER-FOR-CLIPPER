package com.vitre.downloader.core.downloader

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.vitre.downloader.core.models.DownloadProgress
import com.vitre.downloader.core.models.FormatOption
import com.vitre.downloader.core.models.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import org.json.JSONObject

class YtDlpEngine {

    private val python: Python by lazy { Python.getInstance() }
    private val module: PyObject by lazy { python.getModule("vitre_engine") }

    fun getEngineVersions(): Pair<String, String> {
        return try {
            val resStr = module.callAttr("get_engine_version").toString()
            val json = JSONObject(resStr)
            val ytVer = json.optString("ytdlp_version", "Unknown")
            val pyVer = json.optString("python_version", "Unknown")
            Pair(ytVer, pyVer)
        } catch (e: Exception) {
            Pair("Error: ${e.message}", "Unknown")
        }
    }

    suspend fun inspectUrl(url: String, cookiesPath: String? = null): Result<MediaInfo> =
        withContext(Dispatchers.IO) {
            try {
                val pyResult = module.callAttr("inspect_url", url, cookiesPath).toString()
                val json = JSONObject(pyResult)

                if (json.has("error")) {
                    return@withContext Result.failure(Exception(json.getString("error")))
                }

                val title = json.optString("title", "Unknown Title")
                val thumbnail = json.optString("thumbnail", "")
                val duration = json.optLong("duration", 0)
                val uploader = json.optString("uploader", "Unknown")
                val extractor = json.optString("extractor", "generic")
                val webUrl = json.optString("url", url)
                val isImagePost = json.optBoolean("is_image_post", false)

                val formatsArray = json.optJSONArray("formats")
                val formatsList = mutableListOf<FormatOption>()

                if (formatsArray != null) {
                    for (i in 0 until formatsArray.length()) {
                        val f = formatsArray.getJSONObject(i)
                        formatsList.add(
                            FormatOption(
                                formatId = f.optString("format_id", ""),
                                ext = f.optString("ext", "mp4"),
                                resolution = f.optString("resolution", "Default"),
                                height = f.optInt("height", 0),
                                filesize = f.optLong("filesize", 0),
                                isVideo = f.optBoolean("is_video", true),
                                isAudio = f.optBoolean("is_audio", true),
                                vcodec = f.optString("vcodec", "none"),
                                acodec = f.optString("acodec", "none"),
                                fps = f.optDouble("fps", 0.0),
                                tbr = f.optDouble("tbr", 0.0),
                                directUrl = f.optString("direct_url", ""),
                                needsAudio = f.optBoolean("needs_audio", false),
                                isImage = f.optBoolean("is_image", false)
                            )
                        )
                    }
                }

                Result.success(
                    MediaInfo(
                        title = title,
                        thumbnail = thumbnail,
                        duration = duration,
                        uploader = uploader,
                        extractor = extractor,
                        url = webUrl,
                        formats = formatsList,
                        isImagePost = isImagePost
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

data class EngineDownloadResult(
    val outputPath: String,
    val needsMuxing: Boolean = false,
    val videoPath: String? = null,
    val audioPath: String? = null
)

    suspend fun downloadMedia(
        url: String,
        formatId: String,
        outputPath: String,
        cookiesPath: String? = null,
        clipStart: Long? = null,
        clipEnd: Long? = null,
        isAudioOnly: Boolean = false,
        audioFormat: String = "mp3",
        onProgress: (DownloadProgress) -> Unit
    ): Result<EngineDownloadResult> = withContext(Dispatchers.IO) {
        try {
            // Callback bridge from Python hook to Kotlin
            val callback = object {
                @Suppress("unused")
                fun onUpdate(statusJson: String) {
                    try {
                        val j = JSONObject(statusJson)
                        val explicitPct = if (j.has("percentage")) j.optDouble("percentage", 0.0).toFloat() else null
                        val progress = DownloadProgress(
                            status = j.optString("status", "downloading"),
                            downloadedBytes = j.optLong("downloaded_bytes", 0),
                            totalBytes = j.optLong("total_bytes", 0),
                            speed = j.optDouble("speed", 0.0),
                            eta = j.optLong("eta", 0),
                            progressFraction = explicitPct,
                            phase = j.optString("phase", "single")
                        )
                        onProgress(progress)
                    } catch (_: Exception) { }
                }
            }

            // Create a Python lambda that calls callback.onUpdate
            val pyCallback = python.builtins.callAttr("getattr", callback, "onUpdate")

            val pyResult = module.callAttr(
                "download_media",
                url,
                formatId,
                outputPath,
                cookiesPath,
                pyCallback,
                clipStart,
                clipEnd,
                isAudioOnly,
                audioFormat
            ).toString()

            val json = JSONObject(pyResult)
            if (json.has("error")) {
                val errMsg = json.getString("error")
                val isCancelled = json.optBoolean("cancelled", false) || errMsg.contains("cancelled", ignoreCase = true)
                if (isCancelled) {
                    Result.failure(CancellationException(errMsg))
                } else {
                    Result.failure(Exception(errMsg))
                }
            } else {
                val needsMuxing = json.optBoolean("needs_muxing", false)
                val videoPath = if (json.has("video_path")) json.getString("video_path") else null
                val audioPath = if (json.has("audio_path")) json.getString("audio_path") else null
                val finalOutput = json.optString("output_path", outputPath)

                Result.success(
                    EngineDownloadResult(
                        outputPath = finalOutput,
                        needsMuxing = needsMuxing,
                        videoPath = videoPath,
                        audioPath = audioPath
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
