package com.example.domain.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val language: String, val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Success(val language: String, val file: File) : DownloadState()
    data class Error(val language: String, val errorMessage: String) : DownloadState()
}

/**
 * Downloads and verifies Tesseract 'ben' and 'eng' traineddata models
 * from official GitHub repositories into the app's internal storage directory.
 */
class TessModelDownloader(
    private val context: Context,
    private val targetDirectory: File
) {

    companion object {
        // Fast, mobile-optimized official Tesseract 4.x/5.x models
        const val BEN_URL_FAST = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/ben.traineddata"
        const val ENG_URL_FAST = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata"

        // Backup mirror from tessdata repository if fast raw is unreachable
        const val BEN_URL_BACKUP = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ben.traineddata"
        const val ENG_URL_BACKUP = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Downloads a traineddata file (e.g. "ben" or "eng") into [targetDirectory].
     * Writes to a temporary file first and renames atomically upon completion.
     */
    suspend fun downloadModel(
        language: String,
        onProgress: ((progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val fileName = if (language.endsWith(".traineddata")) language else "$language.traineddata"
        val targetFile = File(targetDirectory, fileName)
        val tempFile = File(targetDirectory, "$fileName.tmp")

        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }

        val url = when {
            language.startsWith("ben") -> BEN_URL_FAST
            language.startsWith("eng") -> ENG_URL_FAST
            else -> "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$fileName"
        }

        _downloadState.value = DownloadState.Downloading(language, 0, 0L, -1L)

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NewazLandRecordExtractor-Android")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP download failed with code ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty HTTP response body received for $url")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else -1
                        _downloadState.value = DownloadState.Downloading(language, percent, totalRead, totalBytes)
                        onProgress?.invoke(percent, totalRead, totalBytes)
                    }
                    output.flush()
                }
            }

            // Basic sanity check: traineddata files are typically at least 100 KB
            if (tempFile.length() < 100 * 1024) {
                tempFile.delete()
                throw IOException("Downloaded file $fileName is suspiciously small (${tempFile.length()} bytes)")
            }

            // Atomic rename to final target
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across file systems
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            _downloadState.value = DownloadState.Success(language, targetFile)
            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            val msg = e.localizedMessage ?: "Unknown download error"
            _downloadState.value = DownloadState.Error(language, msg)
            Result.failure(e)
        }
    }

    /**
     * Downloads both Bengali and English models consecutively.
     */
    suspend fun downloadBothModels(
        onOverallProgress: (currentLang: String, percent: Int) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val downloaded = mutableListOf<File>()

        // 1. Bengali
        onOverallProgress("ben", 0)
        val benResult = downloadModel("ben") { percent, _, _ ->
            onOverallProgress("ben", percent)
        }
        if (benResult.isFailure) {
            return@withContext Result.failure(benResult.exceptionOrNull()!!)
        }
        downloaded.add(benResult.getOrThrow())

        // 2. English
        onOverallProgress("eng", 0)
        val engResult = downloadModel("eng") { percent, _, _ ->
            onOverallProgress("eng", percent)
        }
        if (engResult.isFailure) {
            return@withContext Result.failure(engResult.exceptionOrNull()!!)
        }
        downloaded.add(engResult.getOrThrow())

        Result.success(downloaded)
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
