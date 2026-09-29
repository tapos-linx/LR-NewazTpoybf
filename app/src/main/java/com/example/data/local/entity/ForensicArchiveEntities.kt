package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * OCR extracted block entity for zero-data-loss storage.
 * Stores text blocks, confidence, bounding boxes, and unreadable marks.
 */
@Entity(
    tableName = "ocr_blocks",
    indices = [
        Index("documentId"),
        Index("pageNumber")
    ]
)
data class OcrBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val blockType: String = "TEXT", // TEXT, STAMP, SEAL, MARGINAL_NOTE, TABLE_CELL
    val rawText: String,
    val confidence: Float,
    val isUnclear: Boolean = false,
    val unclearReason: String? = null,
    val boundingBoxJson: String? = null, // [x, y, w, h]
    val engineName: String = "Tesseract+PaddleOCR",
    val extractedAt: Long = System.currentTimeMillis()
)

/**
 * HCR Handwritten record entity.
 * Stores original handwriting recognition, manual corrections, and researcher audits.
 */
@Entity(
    tableName = "hcr_records",
    indices = [
        Index("documentId"),
        Index("isVerified")
    ]
)
data class HcrRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int = 1,
    val contextCategory: String = "GENERAL", // OWNER_NAME, DAG_NUMBER, HEIR_SHARE, MARGINALIA, SEAL_OR_STAMP
    val originalText: String,
    val correctedText: String? = null,
    val confidence: Float,
    val isVerified: Boolean = false,
    val requiresManualReview: Boolean = false,
    val editorNotes: String? = null,
    val cropImagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Cadastral Map Sheet Entity.
 */
@Entity(
    tableName = "cadastral_maps",
    indices = [
        Index("mouzaName"),
        Index("surveyType")
    ]
)
data class CadastralMapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mouzaName: String,
    val jlNumber: String? = null,
    val surveyType: String = "CS", // CS, SA, RS, BS_BRS
    val sheetNumber: String = "১ নং চাদর",
    val scale: String = "১৬ ইঞ্চি = ১ মাইল",
    val northArrowAngle: Float = 0.0f,
    val imagePath: String? = null,
    val totalPlotsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Cadastral Plot (Dag) Entity.
 */
@Entity(
    tableName = "cadastral_plots",
    indices = [
        Index("mapId"),
        Index("dagNo"),
        Index("isMatchedInheritance")
    ]
)
data class CadastralPlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mapId: Long,
    val dagNo: String,
    val dagNoBn: String,
    val areaDecimals: Double,
    val polygonCoordsJson: String, // JSON array of points
    val centroidX: Int,
    val centroidY: Int,
    val isMatchedInheritance: Boolean = false,
    val highlightColorHex: String? = null,
    val landClass: String? = "নাল"
)

/**
 * Land Owner & Inheritance Entity.
 */
@Entity(
    tableName = "land_owners",
    indices = [
        Index("documentId"),
        Index("ownerName")
    ]
)
data class LandOwnerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val ownerName: String,
    val fatherOrHusband: String? = null,
    val relationship: String? = null,
    val hissaDecimal: Double, // Share between 0.0001 and 1.0000
    val hissaAnnaBn: String? = null, // e.g. "৪ আনা", "১৬ আনা"
    val khatianNo: String? = null,
    val surveyType: String = "CS"
)

/**
 * Forensic Evidence Audit Entity.
 * Classifies chain of title across 6 legal tiers.
 */
@Entity(
    tableName = "evidence_audits",
    indices = [
        Index("documentId"),
        Index("evidenceTier")
    ]
)
data class EvidenceAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val evidenceTier: String, // VERIFIED, CORROBORATED, PROBABLE, POSSIBLE, RECORD GAP, CONTRADICTORY
    val confidenceScore: Float,
    val hissaBalanced: Boolean = true,
    val discrepanciesJson: String? = null, // JSON list of detected flaws
    val chainNotes: String? = null,
    val auditedBy: String = "LR-Newaz Forensic Engine",
    val auditedAt: Long = System.currentTimeMillis()
)

/**
 * Bulk Upload & Background Job Entity.
 */
@Entity(
    tableName = "batch_jobs",
    indices = [
        Index("status")
    ]
)
data class BatchJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobName: String,
    val totalFiles: Int,
    val processedFiles: Int = 0,
    val duplicateFiles: Int = 0,
    val status: String = "PENDING", // PENDING, RUNNING, PAUSED, COMPLETED, FAILED
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * Search Index Entity for offline search.
 */
@Entity(
    tableName = "search_index",
    indices = [
        Index("keyword"),
        Index("documentId")
    ]
)
data class SearchIndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val keyword: String,
    val fieldName: String, // OWNER, KHATIAN, DAG, MOUZA, NOTES
    val isBengali: Boolean = true
)
