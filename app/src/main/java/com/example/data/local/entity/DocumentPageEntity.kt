package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single page within a land record document.
 * Preserves the pristine original image as raw evidence and stores the preprocessed copy.
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = LandDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "pageIndex"], unique = true)
    ]
)
data class DocumentPageEntity(
    @PrimaryKey
    val id: String, // UUID
    val documentId: String,
    val pageIndex: Int, // 1-based index (1, 2, 3...)
    val rawImagePath: String, // Absolute path to pristine original image (raw evidence)
    val processedImagePath: String? = null, // Absolute path to preprocessed/binarized copy
    val rotationDegrees: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val rawOcrText: String = "",
    val ocrLanguage: String = "ben+eng",
    val ocrConfidence: Float = 0.0f,
    val ocrDurationMs: Long = 0L,
    val extractedFieldsJson: String? = null
)
