package com.vitre.downloader

import com.vitre.downloader.core.models.DownloadStatus
import com.vitre.downloader.core.models.FormatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatOptionTest {

    @Test
    fun formatOption_defaultParameters_instantiatesCleanly() {
        val audioFmt = FormatOption(
            formatId = "bestaudio",
            ext = "m4a",
            resolution = "Audio (M4A)",
            isAudio = true,
            isVideo = false
        )

        assertEquals("bestaudio", audioFmt.formatId)
        assertEquals("m4a", audioFmt.ext)
        assertEquals("Audio (M4A)", audioFmt.resolution)
        assertTrue(audioFmt.isAudio)
        assertFalse(audioFmt.isVideo)
        assertEquals(0L, audioFmt.filesize)
        assertEquals("Stream", audioFmt.displaySize)
        assertEquals("none", audioFmt.vcodec)
        assertEquals("none", audioFmt.acodec)
    }

    @Test
    fun formatOption_displaySize_formatsCorrectly() {
        val mbFmt = FormatOption(
            formatId = "137",
            ext = "mp4",
            resolution = "1080p",
            filesize = 52_428_800L, // 50 MB
            isVideo = true,
            isAudio = false
        )
        assertEquals("50.0 MB", mbFmt.displaySize)

        val gbFmt = FormatOption(
            formatId = "313",
            ext = "webm",
            resolution = "2160p",
            filesize = 1_610_612_736L, // ~1.5 GB
            isVideo = true,
            isAudio = false
        )
        assertEquals("1.50 GB", gbFmt.displaySize)
    }

    @Test
    fun downloadStatus_containsAllLifecycleStates() {
        val states = DownloadStatus.values().map { it.name }
        assertTrue(states.contains("QUEUED"))
        assertTrue(states.contains("DOWNLOADING"))
        assertTrue(states.contains("PROCESSING"))
        assertTrue(states.contains("COMPLETED"))
        assertTrue(states.contains("FAILED"))
        assertTrue(states.contains("CANCELLED"))
    }

    @Test
    fun clipperTimeParsing_parsesStandardFormats() {
        fun parseTime(clean: String): Long? {
            if (clean.isEmpty()) return null
            if (clean.contains(":")) {
                val parts = clean.split(":")
                if (parts.size == 2) {
                    val m = parts[0].toLongOrNull() ?: return null
                    val s = parts[1].toLongOrNull() ?: return null
                    return m * 60 + s
                } else if (parts.size == 3) {
                    val h = parts[0].toLongOrNull() ?: return null
                    val m = parts[1].toLongOrNull() ?: return null
                    val s = parts[2].toLongOrNull() ?: return null
                    return h * 3600 + m * 60 + s
                }
            }
            return clean.toLongOrNull()
        }

        assertEquals(0L, parseTime("00:00"))
        assertEquals(90L, parseTime("01:30"))
        assertEquals(3665L, parseTime("01:01:05"))
        assertEquals(45L, parseTime("45"))
    }
}
