package com.vitre.downloader.core.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class FFmpegEngine {

    fun getFFmpegVersion(): String {
        return "Android MediaCodec & MediaMuxer (Hardware Accelerated)"
    }

    /**
     * Extracts audio track from input video file directly into an audio container (M4A/AAC).
     * Uses hardware-accelerated MediaExtractor and MediaMuxer (API 18+).
     */
    suspend fun extractAudio(
        inputPath: String,
        outputPath: String,
        audioFormat: String = "m4a"
    ): Result<String> = withContext(Dispatchers.IO) {
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor().apply { setDataSource(inputPath) }
            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormatObj: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormatObj = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormatObj == null) {
                return@withContext Result.failure(Exception("No audio track found in the media file"))
            }

            extractor.selectTrack(audioTrackIndex)

            // Mux into MP4 container for audio (.m4a)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(audioFormatObj)
            muxer.start()

            val maxBufferSize = audioFormatObj.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            Result.success(outputPath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Probes media metadata using Android MediaMetadataRetriever
     */
    suspend fun probeMedia(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "0"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "0"
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "Unknown"
            retriever.release()
            Result.success("Mime: $mime, Duration: ${duration}ms, Resolution: ${width}x${height}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
