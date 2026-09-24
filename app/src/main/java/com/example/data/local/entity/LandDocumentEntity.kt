package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a scanned or imported Bangladesh land record document.
 * Stores extracted metadata including document type, owner name(s), date captured,
 * survey classification, geographical location (district, upazila, mouza, JL),
 * and parcel identifiers (khatian no, dag no).
 */
@Entity(
    tableName = "land_documents",
    indices = [
        Index(value = ["classifiedType"]),
        Index(value = ["ownersSummary"]),
        Index(value = ["dateCaptured"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"])
    ]
)
data class LandDocumentEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val sourceFileName: String,
    val sourceFilePath: String, // Original SAF URI or local path
    val sourceCategory: String, // Initial folder name e.g. CS, RS, tituwhatsapp, camera
    val classifiedType: String, // Document Type: CS, SA, RS, BRS, DEEDS, MUTATION, etc.
    val fileFormat: String, // PDF, JPG, PNG, etc.
    val fileSizeBytes: Long = 0L,
    val isZeroByte: Boolean = false,
    val pageCount: Int = 1,
    val dateCaptured: Long = System.currentTimeMillis(), // Timestamp when scanned/captured/imported
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val status: String = "QUEUED", // QUEUED, PROCESSING, EXTRACTED, VERIFIED, ERROR, ZERO_BYTE
    val errorMessage: String? = null,
    val primaryDistrict: String? = null,
    val primaryUpazila: String? = null,
    val primaryMouza: String? = null,
    val primaryJlNo: String? = null,
    val primaryKhatianNo: String? = null,
    val primaryDagNo: String? = null,
    val primaryLandClass: String? = null,
    val primaryAreaDecimals: String? = null,
    val ownersSummary: String? = null, // Extracted owner name(s)
    val overallConfidence: Float = 0.0f,
    val extractedDataJson: String? = null, // Serialized LandRecordData
    val legalDisclaimerAcknowledged: Boolean = false,
    val userNotes: String? = null
)
