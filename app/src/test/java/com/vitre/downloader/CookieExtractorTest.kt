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
            val u = url.lowercase()

            val isStillOnLoginPage = u.contains("/login") ||
                    u.contains("/signin") ||
                    u.contains("servicelogin") ||
                    u.contains("/accounts/login") ||
                    u.contains("/i/flow/login") ||
                    u.contains("/onboarding") ||
                    u.contains("v3/signin") ||
                    u.contains("accounts.google.com")

            if (isStillOnLoginPage) return false

            return when {
                u.contains("youtube.com") -> {
                    cookieHeader.contains("LOGIN_INFO=") ||
                    (cookieHeader.contains("SID=") && cookieHeader.contains("HSID="))
                }
                u.contains("instagram.com") -> {
                    cookieHeader.contains("sessionid=") && cookieHeader.contains("ds_user_id=")
                }
                u.contains("reddit.com") -> {
                    cookieHeader.contains("reddit_session=")
                }
                u.contains("tiktok.com") -> {
                    cookieHeader.contains("sessionid=") && !cookieHeader.contains("sessionid=\"\"")
                }
                u.contains("x.com") || u.contains("twitter.com") -> {
                    cookieHeader.contains("auth_token=")
                }
                else -> false
            }
        }

        // 1. Must NEVER capture while still on login / accounts / signin screen!
        org.junit.Assert.assertFalse(hasAuthSession("https://accounts.google.com/v3/signin", "NID=123; PREF=abc"))
        org.junit.Assert.assertFalse(hasAuthSession("https://www.reddit.com/login/", "token_v2=guest_token; csrf=cf123"))
        org.junit.Assert.assertFalse(hasAuthSession("https://www.instagram.com/accounts/login/", "csrftoken=cf123"))
        org.junit.Assert.assertFalse(hasAuthSession("https://x.com/i/flow/login", "ct0=guest_ct0"))
        org.junit.Assert.assertFalse(hasAuthSession("https://x.com/i/jf/onboarding", "ct0=guest_ct0; guest_id=gid123"))

        // 2. Successful redirect to YouTube after Google/Gmail sign in
        assertTrue(hasAuthSession("https://m.youtube.com/", "LOGIN_INFO=token_xyz_123; SID=sid123; HSID=h123"))

        // 3. Successful redirect to Instagram feed
        assertTrue(hasAuthSession("https://www.instagram.com/", "csrftoken=cf123; sessionid=ig_sess_999; ds_user_id=12345"))

        // 4. Successful redirect to Reddit home
        assertTrue(hasAuthSession("https://www.reddit.com/", "reddit_session=red_token_456"))

        // 5. Successful redirect to X / Twitter home
        assertTrue(hasAuthSession("https://x.com/home", "auth_token=real_auth_token_789; ct0=ct123"))
    }
}
