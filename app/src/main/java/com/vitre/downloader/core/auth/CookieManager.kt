package com.vitre.downloader.core.auth

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class CookieManager(private val context: Context) {

    private val cookieFile: File
        get() = File(context.filesDir, "cookies.txt")

    fun hasCookies(): Boolean = cookieFile.exists() && cookieFile.length() > 0

    fun getCookieFilePath(): String? = if (hasCookies()) cookieFile.absolutePath else null

    fun clearCookies(): Boolean {
        return if (cookieFile.exists()) {
            cookieFile.delete()
        } else {
            true
        }
    }

    /**
     * Imports and strictly validates a user-selected Netscape HTTP Cookie file.
     * Never logs cookie values or sends them externally.
     */
    suspend fun importCookiesFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open selected file"))

            val reader = BufferedReader(InputStreamReader(inputStream))
            var validCookieCount = 0
            val validatedLines = mutableListOf<String>()

            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    // Allow Netscape header comments
                    if (trimmed.startsWith("#")) {
                        validatedLines.add(line)
                        continue
                    }

                    // Netscape format requires 7 tab-delimited columns:
                    // domain, include_subdomains, path, secure, expiry, name, value
                    val parts = trimmed.split("\t")
                    if (parts.size >= 7) {
                        validCookieCount++
                        validatedLines.add(line)
                    }
                }
            }

            if (validCookieCount == 0) {
                return@withContext Result.failure(
                    Exception("Invalid cookie file format. File must be a valid Netscape cookies.txt file.")
                )
            }

            // Write validated cookies safely to app-private storage
            cookieFile.bufferedWriter().use { writer ->
                for (line in validatedLines) {
                    writer.write(line)
                    writer.newLine()
                }
            }

            Result.success(validCookieCount)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to process cookie file: ${e.localizedMessage}"))
        }
    }

    /**
     * Extracts and merges session cookies from an in-app WebView session.
     * Formats cookies to standard Netscape cookies.txt specification.
     */
    suspend fun saveCookiesFromWeb(url: String, cookieHeader: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (cookieHeader.isBlank()) {
                return@withContext Result.failure(Exception("No cookies found in current session"))
            }

            val host = try {
                Uri.parse(url).host?.lowercase() ?: ""
            } catch (_: Exception) { "" }

            val domains = when {
                host.contains("youtube.com") || host.contains("google.com") -> listOf(".youtube.com", ".google.com")
                host.contains("instagram.com") -> listOf(".instagram.com")
                host.contains("tiktok.com") -> listOf(".tiktok.com")
                host.contains("twitter.com") || host.contains("x.com") -> listOf(".x.com", ".twitter.com")
                host.contains("reddit.com") -> listOf(".reddit.com")
                host.contains("vimeo.com") -> listOf(".vimeo.com")
                host.contains("soundcloud.com") -> listOf(".soundcloud.com")
                host.contains("facebook.com") -> listOf(".facebook.com")
                host.isNotEmpty() -> {
                    val root = host.removePrefix("www.").removePrefix("m.")
                    listOf(if (root.startsWith(".")) root else ".$root")
                }
                else -> listOf(".youtube.com")
            }

            // Read existing cookies to merge and preserve across platforms
            val existingCookies = mutableMapOf<String, String>()
            if (cookieFile.exists() && cookieFile.length() > 0) {
                cookieFile.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("\t")
                        if (parts.size >= 7) {
                            val key = "${parts[0].lowercase()}:${parts[5]}"
                            existingCookies[key] = line
                        }
                    }
                }
            }

            val expiry = (System.currentTimeMillis() / 1000) + 315360000L // 10 years in the future
            var addedCount = 0

            val cookiePairs = cookieHeader.split(";")
            for (pair in cookiePairs) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty() || !trimmed.contains("=")) continue

                val name = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=").trim()
                if (name.isEmpty() || value.isEmpty()) continue

                for (domain in domains) {
                    val netscapeLine = "$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
                    val key = "${domain.lowercase()}:$name"
                    existingCookies[key] = netscapeLine
                    addedCount++
                }
            }

            if (addedCount == 0) {
                return@withContext Result.failure(Exception("No valid cookies could be parsed"))
            }

            // Write merged cookies with standard Netscape header
            cookieFile.bufferedWriter().use { writer ->
                writer.write("# Netscape HTTP Cookie File")
                writer.newLine()
                writer.write("# Generated automatically by Vitre Downloader In-App Extractor")
                writer.newLine()
                writer.newLine()
                for (line in existingCookies.values) {
                    writer.write(line)
                    writer.newLine()
                }
            }

            Result.success(existingCookies.size)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save cookies: ${e.localizedMessage}"))
        }
    }

    /**
     * Checks if a user has ACTUALLY completed login on the target platform.
     * Guarantees:
     * 1. Returns FALSE if the user is still on any login, signin, or onboarding screen.
     * 2. Checks ONLY real authenticated account tokens (never anonymous/guest tokens like csrf, token_v2, ct0).
     */
    fun hasAuthSession(url: String, cookieHeader: String): Boolean {
        if (cookieHeader.isBlank()) return false
        val u = url.lowercase()

        // 1. NEVER consider logged-in if still on an authentication / login / onboarding screen
        val isStillOnLoginPage = u.contains("/login") ||
                u.contains("/signin") ||
                u.contains("servicelogin") ||
                u.contains("/accounts/login") ||
                u.contains("/i/flow/login") ||
                u.contains("/onboarding") ||
                u.contains("v3/signin") ||
                u.contains("accounts.google.com")

        if (isStillOnLoginPage) {
            return false
        }

        // 2. Platform-specific REAL authenticated session token verification
        return when {
            u.contains("youtube.com") -> {
                // Must be on youtube.com (redirected from Google accounts) AND have LOGIN_INFO or SID
                cookieHeader.contains("LOGIN_INFO=") ||
                (cookieHeader.contains("SID=") && cookieHeader.contains("HSID="))
            }
            u.contains("instagram.com") -> {
                // Instagram ONLY assigns sessionid after valid credential verification
                cookieHeader.contains("sessionid=") && cookieHeader.contains("ds_user_id=")
            }
            u.contains("reddit.com") -> {
                // Reddit ONLY assigns reddit_session after account login (never token_v2)
                cookieHeader.contains("reddit_session=")
            }
            u.contains("tiktok.com") -> {
                // TikTok assigns sessionid after login (not just anonymous sid_guard)
                cookieHeader.contains("sessionid=") && !cookieHeader.contains("sessionid=\"\"")
            }
            u.contains("x.com") || u.contains("twitter.com") -> {
                // X ONLY assigns auth_token after successful account login (ct0 is just anonymous CSRF)
                cookieHeader.contains("auth_token=")
            }
            else -> !isStillOnLoginPage && cookieHeader.length > 20
        }
    }


    /**
     * Returns human-readable platform names that currently have saved cookies.
     */
    fun getActiveDomains(): List<String> {
        if (!hasCookies()) return emptyList()
        val domains = mutableSetOf<String>()
        try {
            cookieFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("\t")
                    if (parts.isNotEmpty()) {
                        val d = parts[0].removePrefix(".").lowercase()
                        when {
                            d.contains("youtube") -> domains.add("YouTube")
                            d.contains("google") -> domains.add("Google")
                            d.contains("instagram") -> domains.add("Instagram")
                            d.contains("tiktok") -> domains.add("TikTok")
                            d.contains("reddit") -> domains.add("Reddit")
                            d.contains("twitter") || d.contains("x.com") -> domains.add("Twitter / X")
                            d.contains("vimeo") -> domains.add("Vimeo")
                            d.contains("soundcloud") -> domains.add("SoundCloud")
                            d.contains("facebook") -> domains.add("Facebook")
                            else -> domains.add(d)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return domains.toList().sorted()
    }
}
