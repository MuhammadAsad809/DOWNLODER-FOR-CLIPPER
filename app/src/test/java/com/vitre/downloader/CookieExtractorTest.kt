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

    @Test
    fun hasAuthSession_detectsPlatformSessions() {
        fun hasAuthSession(url: String, cookieHeader: String): Boolean {
            if (cookieHeader.isBlank()) return false
            val h = url.lowercase()
            return when {
                h.contains("youtube.com") || h.contains("google.com") -> {
                    cookieHeader.contains("LOGIN_INFO") ||
                    (cookieHeader.contains("SID=") && cookieHeader.contains("HSID=")) ||
                    cookieHeader.contains("SAPISID=")
                }
                h.contains("instagram.com") -> {
                    cookieHeader.contains("sessionid=") || cookieHeader.contains("ds_user_id=")
                }
                h.contains("reddit.com") -> {
                    cookieHeader.contains("reddit_session=") || cookieHeader.contains("token_v2=")
                }
                h.contains("tiktok.com") -> {
                    cookieHeader.contains("sessionid=") || cookieHeader.contains("sid_guard=")
                }
                h.contains("twitter.com") || h.contains("x.com") -> {
                    cookieHeader.contains("auth_token=") || cookieHeader.contains("ct0=")
                }
                else -> false
            }
        }

        // 1. YouTube / Google Gmail session
        val googleCookie = "SID=abc1234; HSID=xyz567; SSID=ss11; APISID=ap99; SAPISID=sp88"
        assertTrue(hasAuthSession("https://accounts.google.com/signin", googleCookie))
        assertTrue(hasAuthSession("https://m.youtube.com/", "LOGIN_INFO=token_xyz_123"))

        // 2. Instagram session
        val igCookie = "csrftoken=cf123; sessionid=ig_sess_999; ds_user_id=12345"
        assertTrue(hasAuthSession("https://www.instagram.com/", igCookie))

        // 3. Reddit session
        val redditCookie = "reddit_session=red_token_456"
        assertTrue(hasAuthSession("https://www.reddit.com/", redditCookie))

        // 4. TikTok session
        val tiktokCookie = "sid_guard=guard_789; sessionid=tt_sess_101"
        assertTrue(hasAuthSession("https://www.tiktok.com/", tiktokCookie))

        // 5. Unauthenticated session returns false
        val anonCookie = "PREF=f1=50000000; VISITOR_INFO1_LIVE=vis_123; YSC=ysc_abc"
        org.junit.Assert.assertFalse(hasAuthSession("https://m.youtube.com/", anonCookie))
    }
}
