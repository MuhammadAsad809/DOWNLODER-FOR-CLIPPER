package com.vitre.downloader.core.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
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

            // Select container format based on audio codec
            val mime = audioFormatObj.getString(MediaFormat.KEY_MIME) ?: ""
            val outputContainer = when {
                mime.contains("opus") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                }
                else -> {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
            }

            muxer = MediaMuxer(outputPath, outputContainer)
            val muxerAudioTrack = muxer.addTrack(audioFormatObj)
            muxer.start()

            // API 26-28 compatible buffer size retrieval (Finding 6 fix)
            val maxBufferSize = if (audioFormatObj.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormatObj.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                samplesWritten++
                extractor.advance()
            }

            muxer.stop()

            if (samplesWritten == 0 || !outputFile.exists() || outputFile.length() < 1024) {
                outputFile.delete()
                return@withContext Result.failure(Exception("Audio extraction produced 0 samples or corrupted output"))
            }

            Result.success(outputPath)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Muxes a separate video stream (e.g. 1080p DASH) and audio stream (e.g. AAC/M4A)
     * into a unified, synchronized MP4 container without re-encoding.
     */
    suspend fun muxVideoAndAudio(
        videoPath: String,
        audioPath: String,
        outputPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioPath) }

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var videoHasAudio = false
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/")) {
                    videoHasAudio = true
                }
            }

            // If the video already contains an audio track (e.g. progressive stream from Instagram/TikTok),
            // no muxing is required! Simply copy the video to outputPath.
            if (videoHasAudio && videoTrackIndex != -1) {
                File(videoPath).copyTo(outputFile, overwrite = true)
                return@withContext Result.success(outputPath)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                return@withContext Result.failure(Exception("No video track found in video stream"))
            }

            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val isVpVideo = videoMime.contains("vp8") || videoMime.contains("vp9")

            val outputContainer = if (isVpVideo) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(outputPath, outputContainer)

            videoExtractor.selectTrack(videoTrackIndex)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            val muxerAudioTrack = if (audioTrackIndex != -1 && audioFormat != null) {
                audioExtractor.selectTrack(audioTrackIndex)
                muxer.addTrack(audioFormat)
            } else -1

            muxer.start()

            val rawVideoBufSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 4 * 1024 * 1024
            val videoBufSize = maxOf(rawVideoBufSize, 4 * 1024 * 1024)
            val videoBuffer = ByteBuffer.allocate(videoBufSize)
            val videoBufferInfo = MediaCodec.BufferInfo()

            val rawAudioBufSize = if (audioFormat?.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) == true) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 512 * 1024
            val audioBufSize = maxOf(rawAudioBufSize, 512 * 1024)
            val audioBuffer = ByteBuffer.allocate(audioBufSize)
            val audioBufferInfo = MediaCodec.BufferInfo()

            // Synchronously interleave video and audio samples in monotonic timestamp order
            var hasVideo = true
            var hasAudio = (muxerAudioTrack != -1 && audioFormat != null)
            var videoSamplesWritten = 0
            var audioSamplesWritten = 0
            var lastVideoPts = -1L
            var lastAudioPts = -1L

            while (hasVideo || hasAudio) {
                val videoTime = if (hasVideo) videoExtractor.sampleTime else Long.MAX_VALUE
                val audioTime = if (hasAudio) audioExtractor.sampleTime else Long.MAX_VALUE

                if (hasVideo && (!hasAudio || videoTime <= audioTime)) {
                    videoBufferInfo.offset = 0
                    videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                    if (videoBufferInfo.size < 0) {
                        hasVideo = false
                    } else {
                        val currentPts = maxOf(0L, videoExtractor.sampleTime)
                        // Prevent MediaMuxer crash on B-frame out-of-order presentation timestamps
                        val safePts = if (currentPts >= lastVideoPts) currentPts else lastVideoPts + 1000L
                        lastVideoPts = safePts
                        videoBufferInfo.presentationTimeUs = safePts
                        videoBufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                        videoSamplesWritten++
                        videoExtractor.advance()
                    }
                } else if (hasAudio) {
                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
                    if (audioBufferInfo.size < 0) {
                        hasAudio = false
                    } else {
                        val currentPts = maxOf(0L, audioExtractor.sampleTime)
                        val safePts = if (currentPts >= lastAudioPts) currentPts else lastAudioPts + 1000L
                        lastAudioPts = safePts
                        audioBufferInfo.presentationTimeUs = safePts
                        audioBufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                        audioSamplesWritten++
                        audioExtractor.advance()
                    }
                }
            }

            muxer.stop()

            if (!outputFile.exists() || outputFile.length() < 1024) {
                outputFile.delete()
                val vFile = File(videoPath)
                if (vFile.exists() && vFile.length() > 1024) {
                    vFile.copyTo(outputFile, overwrite = true)
                    return@withContext Result.success(outputPath)
                }
                return@withContext Result.failure(Exception("Muxing video and audio tracks produced an empty or corrupted file"))
            }

            Result.success(outputPath)
        } catch (e: Exception) {
            outputFile.delete()
            // Resilient fallback: If MediaMuxer fails, salvage the video stream rather than aborting
            try {
                val vFile = File(videoPath)
                if (vFile.exists() && vFile.length() > 1024) {
                    vFile.copyTo(outputFile, overwrite = true)
                    return@withContext Result.success(outputPath)
                }
            } catch (_: Exception) {}
            Result.failure(e)
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
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

    /**
     * Losslessly trims media to the specified time window [startMs, endMs]
     * using hardware-accelerated MediaExtractor and MediaMuxer.
     * Re-bases presentation timestamps from 0 for instant, smooth playback.
     */
    suspend fun trimMedia(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists() || inputFile.length() < 1024) {
            return@withContext Result.failure(Exception("Input media file is missing or empty"))
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor().apply {
                setDataSource(inputPath)
            }

            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                return@withContext Result.failure(Exception("No tracks found in media file"))
            }

            // Determine if input uses VP8/VP9 or Opus/Vorbis (requiring WebM container)
            var hasVpVideo = false
            var hasOpusAudio = false
            var maxInputBufSize = 4 * 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.contains("vp8") || mime.contains("vp9")) hasVpVideo = true
                if (mime.contains("opus") || mime.contains("vorbis")) hasOpusAudio = true
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val buf = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (buf > maxInputBufSize) maxInputBufSize = buf
                }
            }

            val outputContainer = if (hasVpVideo || hasOpusAudio) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(outputPath, outputContainer)
            val trackMap = mutableMapOf<Int, Int>()
            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    try {
                        val muxerTrack = muxer.addTrack(format)
                        trackMap[i] = muxerTrack
                        extractor.selectTrack(i)
                        if (mime.startsWith("video/") && videoTrackIndex == -1) {
                            videoTrackIndex = i
                        } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                            audioTrackIndex = i
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FFmpegEngine", "Skipping incompatible track $i ($mime): ${e.message}")
                    }
                }
            }

            if (trackMap.isEmpty()) {
                return@withContext Result.failure(Exception("No supported audio/video tracks found for clipping"))
            }

            muxer.start()

            val startUs = maxOf(0L, startMs * 1000L)
            val endUs = maxOf(startUs + 250_000L, endMs * 1000L)

            // Seek to previous sync point for clean cut
            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val buffer = ByteBuffer.allocate(maxInputBufSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val hasVideo = videoTrackIndex != -1
            var baseUs = -1L
            var firstKeyframeFound = !hasVideo
            val lastTrackPts = mutableMapOf<Int, Long>()
            var totalSamplesWritten = 0

            var videoReachedEnd = false
            var audioReachedEnd = false

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val sampleTimeUs = extractor.sampleTime
                val isVideo = (trackIndex == videoTrackIndex)
                val isAudio = (trackIndex == audioTrackIndex)

                // Check end condition
                if (isVideo && sampleTimeUs > endUs) {
                    videoReachedEnd = true
                }
                if (isAudio && sampleTimeUs > (endUs + 200_000L)) {
                    audioReachedEnd = true
                }

                val allEnded = (hasVideo && videoReachedEnd && (audioTrackIndex == -1 || audioReachedEnd)) ||
                        (!hasVideo && audioReachedEnd) ||
                        (hasVideo && sampleTimeUs > (endUs + 2_000_000L))

                if (allEnded) {
                    break
                }

                val muxerTrack = trackMap[trackIndex]
                if (muxerTrack != null) {
                    val flags = extractor.sampleFlags
                    val isKey = (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                    // For video, ensure the first written sample is an IDR/keyframe
                    if (isVideo && !firstKeyframeFound) {
                        if (isKey) {
                            firstKeyframeFound = true
                            baseUs = sampleTimeUs
                        } else {
                            if (!extractor.advance()) break
                            continue
                        }
                    }

                    // For audio-only streams, establish base from first audio sample
                    if (!hasVideo && baseUs == -1L) {
                        baseUs = sampleTimeUs
                    }

                    val canWrite = firstKeyframeFound && (
                        (isVideo && !videoReachedEnd) ||
                        (isAudio && !audioReachedEnd) ||
                        (!isVideo && !isAudio && sampleTimeUs <= endUs)
                    )

                    if (canWrite) {
                        // Drop pre-roll audio packets prior to first video keyframe
                        if (isAudio && hasVideo && sampleTimeUs < (baseUs - 50_000L)) {
                            if (!extractor.advance()) break
                            continue
                        }

                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size > 0) {
                            val rawPts = maxOf(0L, sampleTimeUs - baseUs)
                            val lastPts = lastTrackPts[trackIndex] ?: -1L
                            // Guarantee strictly monotonic presentation timestamps per track
                            // to prevent MediaMuxer crash on B-frames or sample timestamp jitter
                            val safePts = if (rawPts >= lastPts) rawPts else (lastPts + 1000L)
                            lastTrackPts[trackIndex] = safePts

                            bufferInfo.presentationTimeUs = safePts
                            bufferInfo.flags = flags
                            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                            totalSamplesWritten++
                        }
                    }
                }

                if (!extractor.advance()) {
                    break
                }
            }

            if (totalSamplesWritten > 0) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    android.util.Log.w("FFmpegEngine", "muxer.stop exception: ${e.message}")
                }
            }

            if (totalSamplesWritten == 0 || !outputFile.exists() || outputFile.length() < 1024) {
                outputFile.delete()
                return@withContext Result.failure(Exception("Clipping produced empty file (0 samples written)"))
            }

            Result.success(outputPath)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
