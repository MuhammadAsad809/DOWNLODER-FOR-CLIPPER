package com.vitre.downloader.core.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MediaStoreManager(private val context: Context) {

    suspend fun saveMediaToPublicStorage(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        isImage: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativePath = when {
                    isImage -> "${Environment.DIRECTORY_PICTURES}/Vitre"
                    isVideo -> "${Environment.DIRECTORY_MOVIES}/Vitre"
                    else -> "${Environment.DIRECTORY_MUSIC}/Vitre"
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                var bytesWritten = 0L
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        bytesWritten = input.copyTo(out)
                    }
                }

                if (bytesWritten != sourceFile.length() || bytesWritten == 0L) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    return@withContext Result.failure(Exception("Failed to write complete media file to MediaStore ($bytesWritten / ${sourceFile.length()} bytes)"))
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                Result.success(uri)
            } else {
                // Android 8 / 9 Fallback
                val baseDir = when {
                    isImage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
                val targetDir = File(baseDir, "Vitre").apply { mkdirs() }
                val targetFile = File(targetDir, displayName)

                var bytesWritten = 0L
                FileInputStream(sourceFile).use { input ->
                    targetFile.outputStream().use { out ->
                        bytesWritten = input.copyTo(out)
                    }
                }

                if (bytesWritten != sourceFile.length() || bytesWritten == 0L) {
                    try { targetFile.delete() } catch (_: Exception) {}
                    return@withContext Result.failure(Exception("Failed to write complete media file to storage ($bytesWritten / ${sourceFile.length()} bytes)"))
                }

                // Trigger Media Scanner Broadcast
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(targetFile)
                }
                context.sendBroadcast(mediaScanIntent)

                Result.success(Uri.fromFile(targetFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveToCustomFolder(
        treeUri: Uri,
        displayName: String,
        mimeType: String,
        sourceFile: File
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Failed to access custom folder"))
            val targetFile = pickedDir.createFile(mimeType, displayName)
                ?: return@withContext Result.failure(Exception("Failed to create file in custom folder"))

            var bytesWritten = 0L
            context.contentResolver.openOutputStream(targetFile.uri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    bytesWritten = inStream.copyTo(outStream)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            if (bytesWritten != sourceFile.length() || bytesWritten == 0L) {
                try { targetFile.delete() } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Failed to write complete media file to custom folder ($bytesWritten / ${sourceFile.length()} bytes)"))
            }

            Result.success(targetFile.uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
