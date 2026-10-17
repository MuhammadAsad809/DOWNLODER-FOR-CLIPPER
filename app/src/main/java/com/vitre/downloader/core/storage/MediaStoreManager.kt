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
        isVideo: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativePath = if (isVideo) {
                    "${Environment.DIRECTORY_MOVIES}/Vitre"
                } else {
                    "${Environment.DIRECTORY_MUSIC}/Vitre"
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

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                Result.success(uri)
            } else {
                // Android 8 / 9 Fallback
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(baseDir, "Vitre").apply { mkdirs() }
                val targetFile = File(targetDir, displayName)

                FileInputStream(sourceFile).use { input ->
                    targetFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
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

            context.contentResolver.openOutputStream(targetFile.uri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            Result.success(targetFile.uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
