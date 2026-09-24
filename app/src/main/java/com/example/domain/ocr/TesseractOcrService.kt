package com.example.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Production implementation of [OcrService] using the Tesseract API.
 *
 * Integrates Tesseract's TessBaseAPI for Bengali ('ben') and English ('eng') recognition,
 * page segmentation mode configuration, confidence reporting, word-level bounding box
 * extraction, and graceful offline fallback.
 */
class TesseractOcrService(
    private val context: Context,
    val tessDataManager: TessDataManager = TessDataManager(context)
) : OcrService {

    private val offlineFallbackEngine = BengaliOfflineOcrEngine(context, tessDataManager)

    override suspend fun recognize(
        imageFile: File,
        language: String,
        pageSegmentationMode: Int
    ): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Verify file exists
        if (!imageFile.exists() || imageFile.length() == 0L) {
            return@withContext OcrResult(
                rawText = "",
                confidence = 0f,
                durationMs = System.currentTimeMillis() - startTime,
                language = language,
                note = "Error: Input image file does not exist or is empty (${imageFile.absolutePath})"
            )
        }

        // 2. Ensure model files are loaded in storage directory
        val effectiveLang = resolveLanguageCode(language)
        val modelsReady = ensureTrainedDataAvailable(effectiveLang)

        // 3. Attempt execution via Tesseract API
        if (modelsReady) {
            val tesseractResult = executeTesseractOcr(imageFile, effectiveLang, pageSegmentationMode, startTime)
            if (tesseractResult != null && tesseractResult.rawText.isNotBlank()) {
                return@withContext tesseractResult
            }
        }

        // 4. Fallback to built-in offline Bengali & English script analyzer
        val fallbackResult = offlineFallbackEngine.recognize(imageFile, language, pageSegmentationMode)
        val note = if (!modelsReady) {
            "Tesseract models missing in ${tessDataManager.tessdataDir.name}. Extracted via offline fallback engine."
        } else {
            fallbackResult.note
        }

        fallbackResult.copy(note = note)
    }

    /**
     * Directly runs OCR on an in-memory [Bitmap] via Tesseract API.
     */
    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        language: String = "ben+eng",
        pageSegmentationMode: Int = 3
    ): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val effectiveLang = resolveLanguageCode(language)

        val modelsReady = ensureTrainedDataAvailable(effectiveLang)
        if (modelsReady) {
            val tessResult = executeTesseractOnBitmap(bitmap, effectiveLang, pageSegmentationMode, startTime)
            if (tessResult != null && tessResult.rawText.isNotBlank()) {
                return@withContext tessResult
            }
        }

        // Save temp file for fallback analyzer
        val tempFile = File(context.cacheDir, "temp_ocr_${System.currentTimeMillis()}.png")
        try {
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            recognize(tempFile, language, pageSegmentationMode)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Checks if the required language models ('ben.traineddata' / 'eng.traineddata')
     * exist in the app's internal storage directory.
     */
    fun ensureTrainedDataAvailable(language: String): Boolean {
        val hasBen = tessDataManager.benFile.exists() && tessDataManager.benFile.length() > 0
        val hasEng = tessDataManager.engFile.exists() && tessDataManager.engFile.length() > 0

        return when {
            language.contains("ben") && language.contains("eng") -> hasBen || hasEng
            language.contains("ben") -> hasBen
            language.contains("eng") -> hasEng
            else -> hasBen || hasEng
        }
    }

    /**
     * Executes Tesseract API via reflection to safely support both tesseract4android
     * and android-tess-two binaries across varied Android ABI environments.
     */
    private fun executeTesseractOcr(
        imageFile: File,
        language: String,
        pageSegMode: Int,
        startTime: Long
    ): OcrResult? {
        val tessInstance = createAndInitTessBaseApi(language, pageSegMode) ?: return null

        return try {
            val tessClass = tessInstance.javaClass

            // setImage(File) or setImage(Bitmap)
            val setImageFileMethod = runCatching { tessClass.getMethod("setImage", File::class.java) }.getOrNull()
            if (setImageFileMethod != null) {
                setImageFileMethod.invoke(tessInstance, imageFile)
            } else {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
                val setImageBitmapMethod = tessClass.getMethod("setImage", Bitmap::class.java)
                setImageBitmapMethod.invoke(tessInstance, bitmap)
            }

            // Extract recognized UTF-8 text
            val getUtf8TextMethod = tessClass.getMethod("getUTF8Text")
            val rawText = (getUtf8TextMethod.invoke(tessInstance) as? String)?.trim() ?: ""

            // Extract mean confidence
            val meanConfMethod = runCatching { tessClass.getMethod("meanConfidence") }.getOrNull()
            val confidence = (meanConfMethod?.invoke(tessInstance) as? Int)?.toFloat() ?: 75.0f

            // Extract word boxes if available
            val wordBoxes = extractBoundingBoxes(tessInstance)

            val duration = System.currentTimeMillis() - startTime

            OcrResult(
                rawText = rawText,
                confidence = confidence.coerceIn(0f, 100f),
                durationMs = duration,
                language = language,
                wordBoxes = wordBoxes,
                isTesseractEngine = true,
                note = "Recognized via native Tesseract API ($language, PSM=$pageSegMode)"
            )
        } catch (_: Exception) {
            null
        } finally {
            recycleTessInstance(tessInstance)
        }
    }

    private fun executeTesseractOnBitmap(
        bitmap: Bitmap,
        language: String,
        pageSegMode: Int,
        startTime: Long
    ): OcrResult? {
        val tessInstance = createAndInitTessBaseApi(language, pageSegMode) ?: return null

        return try {
            val tessClass = tessInstance.javaClass
            val setImageBitmapMethod = tessClass.getMethod("setImage", Bitmap::class.java)
            setImageBitmapMethod.invoke(tessInstance, bitmap)

            val getUtf8TextMethod = tessClass.getMethod("getUTF8Text")
            val rawText = (getUtf8TextMethod.invoke(tessInstance) as? String)?.trim() ?: ""

            val meanConfMethod = runCatching { tessClass.getMethod("meanConfidence") }.getOrNull()
            val confidence = (meanConfMethod?.invoke(tessInstance) as? Int)?.toFloat() ?: 75.0f

            val wordBoxes = extractBoundingBoxes(tessInstance)
            val duration = System.currentTimeMillis() - startTime

            OcrResult(
                rawText = rawText,
                confidence = confidence.coerceIn(0f, 100f),
                durationMs = duration,
                language = language,
                wordBoxes = wordBoxes,
                isTesseractEngine = true,
                note = "Recognized Bitmap via native Tesseract API ($language)"
            )
        } catch (_: Exception) {
            null
        } finally {
            recycleTessInstance(tessInstance)
        }
    }

    private fun createAndInitTessBaseApi(language: String, pageSegMode: Int): Any? {
        val classNames = listOf(
            "com.googlecode.tesseract.android.TessBaseAPI",
            "cz.adaptech.tesseract4android.TessBaseAPI"
        )

        for (className in classNames) {
            try {
                val clazz = Class.forName(className)
                val instance = clazz.getDeclaredConstructor().newInstance()

                val initMethod = clazz.getMethod("init", String::class.java, String::class.java)
                val dataPath = tessDataManager.tessdataDir.parentFile?.absolutePath ?: context.filesDir.absolutePath

                val initialized = initMethod.invoke(instance, dataPath, language) as? Boolean ?: true
                if (initialized) {
                    val setPsmMethod = runCatching {
                        clazz.getMethod("setPageSegMode", Int::class.javaPrimitiveType)
                    }.getOrNull()
                    setPsmMethod?.invoke(instance, pageSegMode)
                    return instance
                }
            } catch (_: Throwable) {
                // Try next class candidate
            }
        }
        return null
    }

    private fun extractBoundingBoxes(tessInstance: Any): List<OcrWordBox> {
        val boxes = mutableListOf<OcrWordBox>()
        try {
            val getWordsMethod = tessInstance.javaClass.getMethod("getWords")
            val iterator = getWordsMethod.invoke(tessInstance) ?: return boxes

            // ResultIterator bounding box extraction
            val iterClass = iterator.javaClass
            val getBoxMethod = iterClass.getMethod("getBoundingBox", Int::class.javaPrimitiveType)
            val getUtf8TextMethod = iterClass.getMethod("getUTF8Text", Int::class.javaPrimitiveType)
            val confidenceMethod = iterClass.getMethod("confidence", Int::class.javaPrimitiveType)
            val nextMethod = iterClass.getMethod("next", Int::class.javaPrimitiveType)

            val rLevelWord = 3 // RIL_WORD in Tesseract PageIteratorLevel

            do {
                val word = getUtf8TextMethod.invoke(iterator, rLevelWord) as? String ?: ""
                val conf = (confidenceMethod.invoke(iterator, rLevelWord) as? Float) ?: 70f
                val rect = getBoxMethod.invoke(iterator, rLevelWord) as? Rect

                if (word.isNotBlank() && rect != null) {
                    boxes.add(
                        OcrWordBox(
                            text = word,
                            confidence = conf,
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom
                        )
                    )
                }
            } while (nextMethod.invoke(iterator, rLevelWord) as? Boolean == true)
        } catch (_: Throwable) {
            // Words extraction optional
        }
        return boxes
    }

    private fun recycleTessInstance(instance: Any?) {
        if (instance == null) return
        try {
            val recycleMethod = runCatching { instance.javaClass.getMethod("recycle") }.getOrNull()
                ?: runCatching { instance.javaClass.getMethod("end") }.getOrNull()
            recycleMethod?.invoke(instance)
        } catch (_: Throwable) {
            // Ignore recycle failures
        }
    }

    private fun resolveLanguageCode(language: String): String {
        val hasBen = tessDataManager.benFile.exists() && tessDataManager.benFile.length() > 0
        val hasEng = tessDataManager.engFile.exists() && tessDataManager.engFile.length() > 0

        return when {
            hasBen && hasEng -> "ben+eng"
            hasBen -> "ben"
            hasEng -> "eng"
            language.contains("ben") && language.contains("eng") -> "ben+eng"
            language.contains("ben") -> "ben"
            else -> "eng"
        }
    }
}
