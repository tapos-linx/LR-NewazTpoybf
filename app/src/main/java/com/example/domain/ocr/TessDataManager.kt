package com.example.domain.ocr

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class TessDataStatus {
    READY,
    MISSING_BENGALI,
    MISSING_ENGLISH,
    MISSING_ALL
}

data class TessDataInfo(
    val status: TessDataStatus,
    val tessdataDir: String,
    val hasBengali: Boolean,
    val hasEnglish: Boolean,
    val bengaliSizeBytes: Long,
    val englishSizeBytes: Long,
    val instructions: String
)

class TessDataManager(private val context: Context) {

    val tessdataDir: File
        get() {
            val dir = File(context.filesDir, "tessdata")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    val benFile: File
        get() = File(tessdataDir, "ben.traineddata")

    val engFile: File
        get() = File(tessdataDir, "eng.traineddata")

    val downloader by lazy {
        TessModelDownloader(context, tessdataDir)
    }

    fun getStatus(): TessDataInfo {
        val hasBen = benFile.exists() && benFile.length() > 0
        val hasEng = engFile.exists() && engFile.length() > 0

        val status = when {
            hasBen && hasEng -> TessDataStatus.READY
            hasBen && !hasEng -> TessDataStatus.MISSING_ENGLISH
            !hasBen && hasEng -> TessDataStatus.MISSING_BENGALI
            else -> TessDataStatus.MISSING_ALL
        }

        val instructions = """
            Tesseract OCR requires Bengali ('ben.traineddata') and English ('eng.traineddata') model files.
            
            How to install:
            1. Tap "Download Models Online" inside this app to automatically fetch official traineddata.
            2. Or download 'ben.traineddata' and 'eng.traineddata' from:
               https://github.com/tesseract-ocr/tessdata_fast
            3. Tap "Import Model File" to select the downloaded .traineddata files.
            4. Alternatively, copy them using ADB, Termux or file manager to:
               ${tessdataDir.absolutePath}/
               
            Note: The app contains an offline Bengali script and land term extractor that functions locally.
        """.trimIndent()

        return TessDataInfo(
            status = status,
            tessdataDir = tessdataDir.absolutePath,
            hasBengali = hasBen,
            hasEnglish = hasEng,
            bengaliSizeBytes = if (hasBen) benFile.length() else 0L,
            englishSizeBytes = if (hasEng) engFile.length() else 0L,
            instructions = instructions
        )
    }

    /**
     * Downloads Bengali model from official repository.
     */
    suspend fun downloadBengaliModel(
        onProgress: ((percent: Int) -> Unit)? = null
    ): Result<File> {
        return downloader.downloadModel("ben") { percent, _, _ ->
            onProgress?.invoke(percent)
        }
    }

    /**
     * Downloads English model from official repository.
     */
    suspend fun downloadEnglishModel(
        onProgress: ((percent: Int) -> Unit)? = null
    ): Result<File> {
        return downloader.downloadModel("eng") { percent, _, _ ->
            onProgress?.invoke(percent)
        }
    }

    /**
     * Downloads both Bengali and English models.
     */
    suspend fun downloadBothModels(
        onProgress: (currentLang: String, percent: Int) -> Unit
    ): Result<List<File>> {
        return downloader.downloadBothModels(onProgress)
    }

    /**
     * Loads and verifies language model file from app storage directory.
     */
    fun loadTrainedData(language: String): Result<File> {
        val fileName = if (language.endsWith(".traineddata")) language else "$language.traineddata"
        val file = File(tessdataDir, fileName)
        return if (file.exists() && file.length() > 0) {
            Result.success(file)
        } else {
            Result.failure(IllegalStateException("Traineddata file $fileName does not exist in ${tessdataDir.absolutePath}"))
        }
    }

    suspend fun importTrainedDataFromUri(uri: Uri, targetName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = File(tessdataDir, targetName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for $uri")
            targetFile
        }
    }

    suspend fun deleteModel(language: String): Boolean = withContext(Dispatchers.IO) {
        val fileName = if (language.endsWith(".traineddata")) language else "$language.traineddata"
        val file = File(tessdataDir, fileName)
        if (file.exists()) file.delete() else false
    }

    /**
     * Copies bundled traineddata from assets/tessdata if present.
     */
    suspend fun copyFromAssetsIfPresent(): Boolean = withContext(Dispatchers.IO) {
        var copied = false
        val assetManager = context.assets
        try {
            val files = assetManager.list("tessdata") ?: emptyArray()
            for (filename in files) {
                if (filename.endsWith(".traineddata")) {
                    val dest = File(tessdataDir, filename)
                    if (!dest.exists() || dest.length() == 0L) {
                        assetManager.open("tessdata/$filename").use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copied = true
                    }
                }
            }
        } catch (_: Exception) {
            // Assets directory might not contain tessdata in initial install
        }
        copied
    }
}
