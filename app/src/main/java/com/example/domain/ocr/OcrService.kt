package com.example.domain.ocr

import java.io.File

/**
 * Result of Optical Character Recognition on a document page.
 */
data class OcrResult(
    val rawText: String,
    val confidence: Float, // 0.0 to 100.0
    val durationMs: Long,
    val language: String,
    val wordBoxes: List<OcrWordBox> = emptyList(),
    val isTesseractEngine: Boolean = false,
    val note: String = ""
)

/**
 * Bounding box representation for word-level evidence mapping.
 */
data class OcrWordBox(
    val text: String,
    val confidence: Float,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Common OCR service contract.
 */
interface OcrService {
    suspend fun recognize(
        imageFile: File,
        language: String = "ben+eng",
        pageSegmentationMode: Int = 3
    ): OcrResult
}
