package com.example.domain.hcr

import java.util.UUID

/**
 * Data structures for Bangla Handwriting Recognition (HCR).
 * Supports token-level confidence, historical cursive handwriting,
 * marginal notes, and manual verification audits.
 */
data class HcrToken(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val confidence: Float,
    val isFlagged: Boolean = confidence < 0.70f || text.contains("[Unclear"),
    val boundingBox: List<Int> = emptyList() // [x, y, w, h]
)

data class HcrTranscriptionResult(
    val id: String = UUID.randomUUID().toString(),
    val documentId: Long = 0L,
    val pageNumber: Int = 1,
    val originalText: String,
    val correctedText: String? = null,
    val confidence: Float,
    val tokens: List<HcrToken> = emptyList(),
    val requiresManualReview: Boolean = confidence < 0.75f,
    val modelVariant: String = "trocr-bengali-handwritten-v1",
    val contextCategory: HcrContextCategory = HcrContextCategory.GENERAL,
    val isVerified: Boolean = false,
    val editorNotes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class HcrContextCategory(val displayNameBn: String, val displayNameEn: String) {
    GENERAL("সাধারণ হস্তলিপি", "General Handwriting"),
    OWNER_NAME("মালিকের নাম ও পিতা/স্বামী", "Owner & Father/Husband Name"),
    DAG_NUMBER("দাগ ও খতিয়ান নম্বর", "Dag & Khatian Number"),
    HEIR_SHARE("উত্তরাধিকারীর অংশ (আনা/হিস্যা)", "Heir Share (Anna/Decimal)"),
    MARGINALIA("প্রান্তিক নোট / পার্শ্বীয় মন্তব্য", "Marginal Notes / Sidenotes"),
    SEAL_OR_STAMP("সিল ও রাজস্ব স্ট্যাম্প", "Official Seal / Revenue Stamp")
}

data class MarginalNoteItem(
    val id: String = UUID.randomUUID().toString(),
    val marginLocation: String, // "left_margin", "right_margin", "top_seal", "bottom_endorsement"
    val text: String,
    val confidence: Float,
    val isUnclear: Boolean,
    val manualCorrection: String? = null
)
