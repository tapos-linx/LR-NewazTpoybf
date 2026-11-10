package com.example.domain.drive

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles Google Drive direct file and folder injection, Storage Access Framework (SAF)
 * Google Drive document provider integration, and public/shared Google Drive URL downloads.
 *
 * SECURITY & AUTHENTICATION CONTRACT:
 * - Direct HTTP download (`downloadDriveFile`) supports ONLY public / "Anyone with the link"
 *   Google Drive files.
 * - Private Google Drive files require authentication via the Android OS Google Drive
 *   Storage Access Framework (SAF) provider (`createDriveFolderTreeIntent`,
 *   `createDriveZipPickerIntent`, `createDriveMultiFilesPickerIntent`).
 * - Enforces redirect limits, download size caps (`MAX_DOWNLOAD_BYTES`), HTML login-wall
 *   detection, and automatic partial-file cleanup on failure.
 */
object GoogleDriveManager {

    const val MAX_REDIRECTS = 6
    const val MAX_DOWNLOAD_BYTES: Long = 100L * 1024L * 1024L // 100 MB safety ceiling

    /**
     * Extracts a Google Drive File ID or Folder ID from common link formats:
     * - https://drive.google.com/file/d/{id}/view...
     * - https://drive.google.com/drive/folders/{id}...
     * - https://drive.google.com/open?id={id}
     * - https://drive.google.com/uc?id={id}
     * - Direct ID string (15+ alphanumeric/dash/underscore chars)
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

        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{15,}$"))) {
            return trimmed
        }

        return null
    }

    /**
     * Downloads a public ("Anyone with the link") Google Drive file over HTTPS.
     * Rejects private/authenticated files with an explicit error instructing the user
     * to use the Google Drive SAF picker, enforces [maxDownloadBytes], and cleans up
     * partial output files on any failure.
     */
    suspend fun downloadDriveFile(
        driveId: String,
        outputFile: File,
        maxDownloadBytes: Long = MAX_DOWNLOAD_BYTES,
        onProgress: (bytesDownloaded: Long) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val cleanId = extractDriveId(driveId)
            ?: throw IllegalArgumentException("অবৈধ গুগল ড্রাইভ লিংক বা ফাইল আইডি: $driveId")

        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }

        var downloadUrl = "https://drive.google.com/uc?export=download&id=$cleanId"
        var redirectCount = 0
        var connection: HttpURLConnection? = null

        try {
            while (redirectCount < MAX_REDIRECTS) {
                val url = URL(downloadUrl)
                if (url.protocol.lowercase() != "https" && url.protocol.lowercase() != "http") {
                    throw IOException("Unsupported redirect protocol: ${url.protocol}")
                }
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; LandRecordForensicPlatform)")
                }
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 ||
                    status == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect response ($status) missing Location header")
                    if (location.contains("accounts.google.com") || location.contains("ServiceLogin")) {
                        connection.disconnect()
                        throw IOException(
                            "ব্যক্তিগত (Private) গুগল ড্রাইভ ফাইল সরাসরি লিংকের মাধ্যমে ডাউনলোড করা যাবে না। " +
                                "অনুগ্রহ করে গুগল ড্রাইভ SAF পিকার ব্যবহার করুন।"
                        )
                    }
                    downloadUrl = URL(url, location).toString()
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            if (redirectCount >= MAX_REDIRECTS) {
                throw IOException("গুগল ড্রাইভ ডাউনলোডে অতিরিক্ত রিডাইরেক্ট ($MAX_REDIRECTS) শনাক্ত হয়েছে।")
            }

            val conn = connection ?: throw IOException("গুগল ড্রাইভ সংযোগ স্থাপন করা যায়নি।")
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                responseCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                throw IOException(
                    "গুগল ড্রাইভ ফাইলটি ব্যক্তিগত (HTTP $responseCode)। " +
                        "প্রাইভেট ফাইল বা ফোল্ডারের জন্য গুগল ড্রাইভ SAF পিকার ব্যবহার করুন।"
                )
            }
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw IOException("গুগল ড্রাইভ ফাইলটি পাওয়া যায়নি (HTTP 404)।")
            }
            if (responseCode !in 200..299) {
                throw IOException("গুগল ড্রাইভ ডাউনলোড ব্যর্থ হয়েছে (HTTP $responseCode)।")
            }

            val contentLength = conn.contentLengthLong
            if (contentLength > maxDownloadBytes) {
                throw IOException(
                    "ফাইলের আকার ($contentLength bytes) সর্বোচ্চ অনুমোদিত সীমা ($maxDownloadBytes bytes) অতিক্রম করেছে।"
                )
            }

            val contentType = (conn.contentType ?: "").lowercase()
            val inputStream: InputStream = conn.inputStream

            FileOutputStream(outputFile).use { out ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                var firstChunkPreview = ""

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > maxDownloadBytes) {
                        throw IOException(
                            "ডাউনলোডকৃত ফাইল সর্বোচ্চ অনুমোদিত আকার ($maxDownloadBytes bytes) অতিক্রম করেছে।"
                        )
                    }
                    if (firstChunkPreview.isEmpty() && contentType.contains("text/html")) {
                        firstChunkPreview = String(buffer, 0, bytesRead.coerceAtMost(1024), Charsets.UTF_8)
                        if (firstChunkPreview.contains("accounts.google.com") ||
                            firstChunkPreview.contains("ServiceLogin") ||
                            firstChunkPreview.contains("Sign in")
                        ) {
                            throw IOException(
                                "ফাইলটি ব্যক্তিগত (Private) এবং লগইন প্রয়োজন। " +
                                    "অনুগ্রহ করে গুগল ড্রাইভ SAF ফাইল পিকার ব্যবহার করুন।"
                            )
                        }
                    }
                    out.write(buffer, 0, bytesRead)
                    onProgress(totalBytes)
                }

                if (totalBytes == 0L) {
                    throw IOException("গুগল ড্রাইভ হতে ০-বাইট (খালি) ফাইল প্রাপ্ত হয়েছে।")
                }
            }
        } catch (t: Throwable) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw t
        } finally {
            connection?.disconnect()
        }

        outputFile
    }

    /**
     * Intent for selecting a Master Google Drive Folder or subfolder via Storage Access Framework.
     * Supports authenticated private Google Drive accounts signed in on the Android device.
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
     * Intent for selecting a ZIP archive from Google Drive via Storage Access Framework.
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
     * Intent for multi-file selection directly from Google Drive via Storage Access Framework.
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
