package com.vitre.downloader.core.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.vitre.downloader.core.database.DownloadDao
import com.vitre.downloader.core.database.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaResolver {

    /**
     * Resolves a valid, playable Uri for a downloaded media item.
     * If the recorded mediaUri is null or dead, scans public storage and MediaStore,
     * updates the database with the resolved Uri, and returns the playable Uri.
     * Returns null if no file is found (preventing invalid HTTP URLs from crashing ExoPlayer).
     */
    suspend fun resolveMediaUri(
        context: Context,
        item: DownloadEntity,
        downloadDao: DownloadDao? = null
    ): Uri? = withContext(Dispatchers.IO) {
        // 1. Try recorded mediaUri if present
        if (!item.mediaUri.isNullOrBlank()) {
            val recordedUri = Uri.parse(item.mediaUri)
            if (isUriReadable(context, recordedUri)) {
                return@withContext recordedUri
            }
        }

        // 2. Scan public folders for matching files
        val safeTitle = item.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_").trim('_')
        val candidateDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Vitre"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Vitre"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Vitre"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vitre"),
            File("/storage/emulated/0/Movies/Vitre"),
            File("/storage/emulated/0/Music/Vitre"),
            File("/storage/emulated/0/Pictures/Vitre"),
            File("/storage/emulated/0/Download/Vitre")
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                val matched = files.firstOrNull { f ->
                    val name = f.nameWithoutExtension
                    name.equals(safeTitle, ignoreCase = true) ||
                    name.contains(safeTitle, ignoreCase = true) ||
                    (safeTitle.length >= 4 && name.contains(safeTitle.take(8), ignoreCase = true))
                } ?: if (files.size == 1 && item.title.isNotBlank()) {
                    files.first()
                } else null

                if (matched != null && matched.exists() && matched.length() > 0) {
                    val fileUri = getShareableUri(context, matched)
                    downloadDao?.updateMediaUri(item.id, fileUri.toString())
                    return@withContext fileUri
                }
            }
        }

        // 3. Query MediaStore
        val mediaStoreUri = queryMediaStore(context, safeTitle)
        if (mediaStoreUri != null) {
            downloadDao?.updateMediaUri(item.id, mediaStoreUri.toString())
            return@withContext mediaStoreUri
        }

        null
    }

    private fun isUriReadable(context: Context, uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return false
                    File(path).let { it.exists() && it.length() > 0 }
                }
                "content" -> {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        pfd.statSize > 0
                    } ?: false
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getShareableUri(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    private fun queryMediaStore(context: Context, safeTitle: String): Uri? {
        val resolver = context.contentResolver
        val collections = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )

        val searchToken = if (safeTitle.length > 12) safeTitle.take(12) else safeTitle
        for (collection in collections) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$searchToken%")

                resolver.query(collection, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (_: Exception) {
                // Continue
            }
        }
        return null
    }
}
