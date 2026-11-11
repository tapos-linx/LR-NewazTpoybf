package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified Document Lifecycle States (Phase 3).
 */
object DocumentProcessingState {
    const val PENDING = "PENDING"
    const val IMPORTING = "IMPORTING"
    const val IMPORTED = "IMPORTED"
    const val VALIDATING = "VALIDATING"
    const val READY_FOR_PROCESSING = "READY_FOR_PROCESSING"
    const val PROCESSING = "PROCESSING"
    const val COMPLETED = "COMPLETED"
    const val NEEDS_MANUAL_REVIEW = "NEEDS_MANUAL_REVIEW"
    const val FAILED = "FAILED"
    const val DUPLICATE = "DUPLICATE"
    const val UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
    const val QUEUED = "QUEUED"
    const val UNVERIFIED = "UNVERIFIED"
    const val VERIFIED = "VERIFIED"
    const val ZERO_BYTE = "ZERO_BYTE"
}

/**
 * Unified Source Types for Multi-Source Ingestion.
 */
object DocumentSourceType {
    const val ANDROID_SAF_FILE = "ANDROID_SAF_FILE"
    const val ANDROID_SAF_FOLDER = "ANDROID_SAF_FOLDER"
    const val LOCAL_ZIP_ARCHIVE = "LOCAL_ZIP_ARCHIVE"
    const val GOOGLE_DRIVE = "GOOGLE_DRIVE"
    const val DESKTOP_COMPANION = "DESKTOP_COMPANION"
    const val CAMERA_CAPTURE = "CAMERA_CAPTURE"
}

/**
 * Represents a scanned or imported Bangladesh land record document in the Unified Document Library.
 * Preserves immutable source metadata, SHA-256 content hash, Google Drive File ID (when applicable),
 * processing lifecycle state, and extracted cadastral metadata.
 */
@Entity(
    tableName = "land_documents",
    indices = [
        Index(value = ["classifiedType"]),
        Index(value = ["ownersSummary"]),
        Index(value = ["dateCaptured"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["sha256"]),
        Index(value = ["processingStatus"]),
        Index(value = ["sourceType"])
    ]
)
data class LandDocumentEntity(
    @PrimaryKey
    val id: String, // documentId (UUID)
    val title: String,
    val sourceFileName: String, // originalFilename
    val sourceFilePath: String, // sourceUri or sourceReference
    val sourceCategory: String, // Initial folder or batch label
    val classifiedType: String, // surveyType: CS, SA, RS, BRS, DEEDS, MUTATION, etc.
    val fileFormat: String, // PDF, JPG, PNG, TIFF, WEBP, ZIP, CSV, XLSX, DOCX, TXT, JSON
    val fileSizeBytes: Long = 0L, // fileSize
    val isZeroByte: Boolean = false,
    val pageCount: Int = 1,
    val dateCaptured: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(), // importTimestamp
    val processedAt: Long? = null,
    val status: String = DocumentProcessingState.UNVERIFIED,
    val errorMessage: String? = null,
    val primaryDistrict: String? = null,
    val primaryUpazila: String? = null,
    val primaryMouza: String? = null, // mouzaName
    val primaryJlNo: String? = null,
    val primaryKhatianNo: String? = null, // khatianNumber
    val primaryDagNo: String? = null, // dagNumber
    val primaryLandClass: String? = null,
    val primaryAreaDecimals: String? = null,
    val ownersSummary: String? = null,
    val overallConfidence: Float = 0.0f,
    val extractedDataJson: String? = null,
    val legalDisclaimerAcknowledged: Boolean = false,
    val userNotes: String? = null,
    // Phase 3 Unified Document Library & Forensic Integrity columns (Added in Room v7)
    val storedFilename: String = "",
    val mimeType: String = "application/octet-stream",
    val sourceType: String = DocumentSourceType.ANDROID_SAF_FILE,
    val googleDriveFileId: String? = null,
    val sha256: String = "",
    val importTimestamp: Long = System.currentTimeMillis(),
    val processingStatus: String = DocumentProcessingState.IMPORTED,
    val processingError: String? = null,
    val verificationStatus: String = "UNVERIFIED"
)

/**
 * Durable Room-persisted Bulk Import Queue Item Entity (Phase 4).
 * Ensures queued imports survive app restarts and background WorkManager execution.
 */
@Entity(
    tableName = "import_queue_items",
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["processingState"]),
        Index(value = ["sha256"]),
        Index(value = ["sourceType"])
    ]
)
data class ImportQueueItemEntity(
    @PrimaryKey
    val queueItemId: String, // UUID
    val batchId: String,
    val documentId: String? = null,
    val originalFilename: String,
    val storedFilePath: String? = null,
    val mimeType: String,
    val fileFormat: String,
    val fileSizeBytes: Long,
    val sourceType: String,
    val sourceUri: String,
    val parentFolder: String,
    val googleDriveFileId: String? = null,
    val sha256: String = "",
    val isDuplicate: Boolean = false,
    val duplicateOfDocumentId: String? = null,
    val isZeroByte: Boolean = false,
    val surveyType: String = "UNKNOWN",
    val mouzaName: String? = null,
    val processingState: String = DocumentProcessingState.PENDING,
    val progressFraction: Float = 0.0f,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Immutable Import & Forensic Audit Log Entity (Phase 5).
 */
@Entity(
    tableName = "import_audit_logs",
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class ImportAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: String,
    val eventType: String, // IMPORT_STARTED, SHA256_VERIFIED, DUPLICATE_DETECTED, SANDBOX_COPIED, OCR_COMPLETED, MANUAL_CORRECTION, IMPORT_FAILED
    val sourceType: String,
    val sourceReference: String,
    val sha256: String,
    val fileSizeBytes: Long,
    val actor: String = "USER",
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
