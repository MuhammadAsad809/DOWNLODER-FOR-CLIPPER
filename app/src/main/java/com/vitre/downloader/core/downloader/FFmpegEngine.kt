package com.vitre.downloader.core.downloader

import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFprobe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FFmpegEngine {

    fun getFFmpegVersion(): String {
        return try {
            Config.getFFmpegVersion() ?: "FFmpeg Native"
        } catch (e: Exception) {
            "FFmpeg 4.4-LTS"
        }
    }

    suspend fun extractAudio(
        inputPath: String,
        outputPath: String,
        audioFormat: String = "mp3"
    ): Result<String> = withContext(Dispatchers.IO) {
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Arguments for clean audio extraction
        val cmd = when (audioFormat.lowercase()) {
            "mp3" -> arrayOf("-y", "-i", inputPath, "-vn", "-c:a", "libmp3lame", "-q:a", "2", outputPath)
            "m4a", "aac" -> arrayOf("-y", "-i", inputPath, "-vn", "-c:a", "aac", "-b:a", "192k", outputPath)
            "opus" -> arrayOf("-y", "-i", inputPath, "-vn", "-c:a", "libopus", "-b:a", "128k", outputPath)
            else -> arrayOf("-y", "-i", inputPath, "-vn", outputPath)
        }

        val rc = FFmpeg.execute(cmd)
        if (rc == Config.RETURN_CODE_SUCCESS) {
            Result.success(outputPath)
        } else {
            val lastOutput = Config.getLastCommandOutput()
            Result.failure(Exception("FFmpeg failed with return code $rc: $lastOutput"))
        }
    }

    suspend fun probeMedia(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        val rc = FFprobe.execute(arrayOf("-v", "error", "-show_format", "-show_streams", filePath))
        if (rc == Config.RETURN_CODE_SUCCESS) {
            Result.success(Config.getLastCommandOutput() ?: "Probed successfully")
        } else {
            Result.failure(Exception("FFprobe failed with return code $rc"))
        }
    }
}
