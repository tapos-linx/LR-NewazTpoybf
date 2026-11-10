package com.example.domain.ocr

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline Bengali + English OCR engine fallback implementing [OcrService].
 *
 * STRICT FORENSIC RULE:
 * Never invents or fabricates Khatian text, Dag numbers, Mouza names, or owner names
 * when native Tesseract (`ben.traineddata`) is not configured on device.
 * Returns empty `rawText` with an explicit diagnostic `note` so callers mark the
 * document as `NEEDS_MANUAL_REVIEW` or `NOT_CONFIGURED`.
 */
class BengaliOfflineOcrEngine(
    private val context: android.content.Context? = null,
    private val tessDataManager: TessDataManager? = null
) : OcrService {

    override suspend fun recognize(
        imageFile: File,
        language: String,
        pageSegmentationMode: Int
    ): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (!imageFile.exists() || imageFile.length() == 0L) {
            return@withContext OcrResult(
                rawText = "",
                confidence = 0f,
                durationMs = 0L,
                language = language,
                wordBoxes = emptyList(),
                isTesseractEngine = false,
                note = "PROCESSING_FAILED_ZERO_BYTE"
            )
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        val isDecodableImage = options.outWidth > 0 && options.outHeight > 0
        val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

        if (!isDecodableImage) {
            return@withContext OcrResult(
                rawText = "",
                confidence = 0f,
                durationMs = duration,
                language = language,
                wordBoxes = emptyList(),
                isTesseractEngine = false,
                note = "UNSUPPORTED_FORMAT"
            )
        }

        OcrResult(
            rawText = "",
            confidence = 0f,
            durationMs = duration,
            language = language,
            wordBoxes = emptyList(),
            isTesseractEngine = false,
            note = "NOT_CONFIGURED_NEEDS_MANUAL_REVIEW: ben.traineddata not installed"
        )
    }
}
