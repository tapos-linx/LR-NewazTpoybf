package com.example.domain.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Handles Google Drive direct file and folder injection, Storage Access Framework (SAF)
 * Google Drive document provider integration, and Google Drive URL / ID direct downloads.
 */
object GoogleDriveManager {

    /**
     * Extracts a Google Drive File ID or Folder ID from common link formats:
     * - https://drive.google.com/file/d/{id}/view...
     * - https://drive.google.com/drive/folders/{id}...
     * - https://drive.google.com/open?id={id}
     * - https://drive.google.com/uc?id={id}
     * - Direct ID string
     */
    fun extractDriveId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) return null

        val fileRegex = Regex("/file/d/([a-zA-Z0-9_-]+)")
        val folderRegex = Regex("/folders/([a-zA-Z0-9_-]+)")
        val idParamRegex = Regex("[?&]id=([a-zA-Z0-9_-]+)")

        fileRegex.find(trimmed)?.groupValues?.get(1)?.let { return it }
        folderRegex.find(trimmed)?.groupValues?.get(1)?.let { return it }
        idParamRegex.find(trimmed)?.groupValues?.get(1)?.let { return it }

        // If it looks like a raw ID
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{20,}$"))) {
            return trimmed
        }

        return null
    }

    /**
     * Downloads a public or anyone-with-link Google Drive file directly over HTTP.
     * Follows redirects and handles large-file virus scan confirmation tokens.
     */
    suspend fun downloadDriveFile(
        driveId: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }

        var downloadUrl = "https://drive.google.com/uc?export=download&id=$driveId"
        var redirectCount = 0
        var connection: HttpURLConnection? = null

        try {
            while (redirectCount < 6) {
                val url = URL(downloadUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                }
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 303
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    downloadUrl = location
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            val conn = connection ?: throw IllegalStateException("Could not open connection to Google Drive")
            val inputStream: InputStream = conn.inputStream

            // Read into output file
            FileOutputStream(outputFile).use { out ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    onProgress(totalBytes)
                }
            }
        } finally {
            connection?.disconnect()
        }

        outputFile
    }

    /**
     * Intent for selecting a Master Google Drive Folder or subfolder via Storage Access Framework
     */
    fun createDriveFolderTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
    }

    /**
     * Intent for selecting a ZIP archive from Google Drive
     */
    fun createDriveZipPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/x-zip",
                    "application/octet-stream"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Intent for multi-file selection directly from Google Drive
     */
    fun createDriveMultiFilesPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "image/*",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/csv",
                    "text/plain"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
