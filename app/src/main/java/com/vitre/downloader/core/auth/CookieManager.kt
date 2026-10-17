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
            // Note: Exception message does NOT expose any cookie contents
            Result.failure(Exception("Failed to process cookie file: ${e.localizedMessage}"))
        }
    }
}
