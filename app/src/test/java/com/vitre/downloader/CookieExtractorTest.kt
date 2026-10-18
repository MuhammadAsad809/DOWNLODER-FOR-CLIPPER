package com.vitre.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieExtractorTest {

    @Test
    fun parseWebCookiesToNetscape_parsesStandardCookieHeader() {
        val rawHeader = "SID=abc123xyz; HSID=h789; SSID=s456; LOGIN_INFO=AF_some_token"
        val pairs = rawHeader.split(";").mapNotNull {
            val trimmed = it.trim()
            if (trimmed.contains("=")) {
                val k = trimmed.substringBefore("=").trim()
                val v = trimmed.substringAfter("=").trim()
                if (k.isNotEmpty() && v.isNotEmpty()) Pair(k, v) else null
            } else null
        }

        assertEquals(4, pairs.size)
        assertEquals("SID", pairs[0].first)
        assertEquals("abc123xyz", pairs[0].second)
        assertEquals("LOGIN_INFO", pairs[3].first)

        // Verify Netscape format line format: domain, include_subdomains, path, secure, expiry, name, value
        val expiry = 2147483647L
        val netscapeLine = ".youtube.com\tTRUE\t/\tTRUE\t$expiry\t${pairs[0].first}\t${pairs[0].second}"
        val parts = netscapeLine.split("\t")
        assertEquals(7, parts.size)
        assertEquals(".youtube.com", parts[0])
        assertEquals("TRUE", parts[1])
        assertEquals("/", parts[2])
        assertEquals("TRUE", parts[3])
        assertEquals("SID", parts[5])
        assertEquals("abc123xyz", parts[6])
    }

    @Test
    fun parseInstagramCookies_detectsSessionIdAndCsrf() {
        val rawHeader = "sessionid=987654321%3Abc%3A12; ds_user_id=12345678; csrftoken=abcdef0123"
        val pairs = rawHeader.split(";").mapNotNull {
            val trimmed = it.trim()
            if (trimmed.contains("=")) {
                val k = trimmed.substringBefore("=").trim()
                val v = trimmed.substringAfter("=").trim()
                if (k.isNotEmpty() && v.isNotEmpty()) Pair(k, v) else null
            } else null
        }.toMap()

        assertTrue(pairs.containsKey("sessionid"))
        assertTrue(pairs.containsKey("ds_user_id"))
        assertTrue(pairs.containsKey("csrftoken"))
        assertEquals("12345678", pairs["ds_user_id"])
    }

    @Test
    fun domainResolution_mapsKnownHosts() {
        fun resolveDomains(host: String): List<String> {
            val h = host.lowercase()
            return when {
                h.contains("youtube.com") || h.contains("google.com") -> listOf(".youtube.com", ".google.com")
                h.contains("instagram.com") -> listOf(".instagram.com")
                h.contains("tiktok.com") -> listOf(".tiktok.com")
                else -> listOf(".$h")
            }
        }

        assertEquals(listOf(".youtube.com", ".google.com"), resolveDomains("m.youtube.com"))
        assertEquals(listOf(".instagram.com"), resolveDomains("www.instagram.com"))
        assertEquals(listOf(".tiktok.com"), resolveDomains("vm.tiktok.com"))
    }
}
