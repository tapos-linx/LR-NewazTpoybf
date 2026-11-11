package com.example.domain.ingestion

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentProcessingState
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportAuditLogEntity
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.LandRecordRepository
import com.example.domain.saf.DiscoveredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Unified Document Import Repository (Phase 2, Phase 3 & Phase 4).
 * Persists queue items, document states, and immutable forensic audit logs in Room.
 */
class DocumentImportRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val landRecordRepository: LandRecordRepository = LandRecordRepository(context, database)
) {
    private val docDao = database.landDocumentDao()

    val allDocuments: Flow<List<LandDocumentEntity>> = docDao.getAllDocuments()
    val allQueueItems: Flow<List<ImportQueueItemEntity>> = docDao.observeAllQueueItems()

    fun observeAuditLogs(documentId: String): Flow<List<ImportAuditLogEntity>> =
        docDao.observeAuditLogsForDocument(documentId)

    suspend fun getAuditLogsOnce(documentId: String): List<ImportAuditLogEntity> =
        docDao.getAuditLogsForDocumentOnce(documentId)

    suspend fun getQueueItemsOnce(): List<ImportQueueItemEntity> =
        docDao.getAllQueueItemsOnce()

    suspend fun enqueueDiscoveredFiles(
        files: List<DiscoveredFile>,
        sourceType: String = DocumentSourceType.ANDROID_SAF_FILE,
        batchId: String = UUID.randomUUID().toString()
    ): List<ImportQueueItemEntity> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext emptyList()

        val existingQueue = docDao.getAllQueueItemsOnce()
        val seenHashesInQueue = existingQueue.mapNotNull { it.sha256.takeIf { h -> h.isNotBlank() } }.toMutableSet()
        val createdItems = mutableListOf<ImportQueueItemEntity>()
        val now = System.currentTimeMillis()

        for (file in files) {
            val hash = file.sha256Hash.ifBlank {
                if (!file.isZeroByte) DocumentHashService.computeSha256(context, file.uri) else ""
            }
            val existingDoc = if (hash.isNotBlank()) docDao.findDocumentBySha256(hash) else null
            val isDup = file.isDuplicate || existingDoc != null || (hash.isNotBlank() && hash in seenHashesInQueue)
            if (hash.isNotBlank()) {
                seenHashesInQueue.add(hash)
            }

            val initialState = when {
                file.isZeroByte || file.sizeBytes == 0L -> DocumentProcessingState.FAILED
                file.format.lowercase() !in DocumentValidationService.SUPPORTED_EXTENSIONS -> DocumentProcessingState.UNSUPPORTED_FORMAT
                isDup -> DocumentProcessingState.DUPLICATE
                else -> DocumentProcessingState.PENDING
            }

            val errorMsg = when (initialState) {
                DocumentProcessingState.FAILED -> "ZERO_BYTE_EMPTY_FILE: 0-byte file cannot be processed"
                DocumentProcessingState.UNSUPPORTED_FORMAT -> "UNSUPPORTED_FORMAT: .${file.format} is not supported"
                DocumentProcessingState.DUPLICATE -> "DUPLICATE_SHA256: Matches ${existingDoc?.sourceFileName ?: file.duplicateOriginalSource ?: "existing queue item"}"
                else -> null
            }

            val item = ImportQueueItemEntity(
                queueItemId = UUID.randomUUID().toString(),
                batchId = batchId,
                documentId = existingDoc?.id,
                originalFilename = file.name,
                storedFilePath = null,
                mimeType = file.mimeType ?: "application/octet-stream",
                fileFormat = file.format,
                fileSizeBytes = file.sizeBytes,
                sourceType = sourceType,
                sourceUri = file.uri.toString(),
                parentFolder = file.parentFolder,
                sha256 = hash,
                isDuplicate = isDup,
                duplicateOfDocumentId = existingDoc?.id,
                isZeroByte = file.isZeroByte || file.sizeBytes == 0L,
                surveyType = file.classifiedType.name,
                processingState = initialState,
                progressFraction = if (initialState in setOf(DocumentProcessingState.FAILED, DocumentProcessingState.UNSUPPORTED_FORMAT, DocumentProcessingState.DUPLICATE)) 1.0f else 0.0f,
                errorMessage = errorMsg,
                createdAt = now,
                updatedAt = now
            )
            createdItems.add(item)
        }

        docDao.insertQueueItems(createdItems)
        createdItems
    }

    /**
     * Executes the full multi-stage import lifecycle for a single queue item:
     * PENDING -> IMPORTING -> IMPORTED -> VALIDATING -> READY_FOR_PROCESSING -> PROCESSING -> COMPLETED / NEEDS_MANUAL_REVIEW / FAILED
     */
    suspend fun processQueueItem(queueItemId: String): ImportQueueItemEntity? = withContext(Dispatchers.IO) {
        val current = docDao.getQueueItemById(queueItemId) ?: return@withContext null
        if (current.processingState in setOf(
                DocumentProcessingState.COMPLETED,
                DocumentProcessingState.DUPLICATE,
                DocumentProcessingState.UNSUPPORTED_FORMAT
            )
        ) {
            return@withContext current
        }

        try {
            // Stage 1: IMPORTING
            var updated = current.copy(
                processingState = DocumentProcessingState.IMPORTING,
                progressFraction = 0.15f,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            docDao.updateQueueItem(updated)

            val uri = Uri.parse(current.sourceUri)
            val discovered = DiscoveredFile(
                uri = uri,
                name = current.originalFilename,
                parentFolder = current.parentFolder,
                mimeType = current.mimeType,
                sizeBytes = current.fileSizeBytes,
                isZeroByte = current.isZeroByte,
                format = current.fileFormat,
                classifiedType = com.example.data.model.LandRecordType.fromFolderOrName("${current.parentFolder} ${current.originalFilename}"),
                sha256Hash = current.sha256,
                isDuplicate = current.isDuplicate
            )

            // Stage 2: IMPORTED & VALIDATING
            updated = updated.copy(
                processingState = DocumentProcessingState.VALIDATING,
                progressFraction = 0.35f,
                updatedAt = System.currentTimeMillis()
            )
            docDao.updateQueueItem(updated)

            // Stage 3: READY_FOR_PROCESSING & PROCESSING
            updated = updated.copy(
                processingState = DocumentProcessingState.PROCESSING,
                progressFraction = 0.60f,
                updatedAt = System.currentTimeMillis()
            )
            docDao.updateQueueItem(updated)

            val persistedDocId = landRecordRepository.processImportedFile(
                discovered = discovered,
                sourceTypeOverride = current.sourceType,
                googleDriveFileId = current.googleDriveFileId
            )

            val persistedDoc = docDao.getDocumentByIdOnce(persistedDocId)
            val finalState = when (persistedDoc?.processingStatus) {
                DocumentProcessingState.FAILED, DocumentProcessingState.ZERO_BYTE -> DocumentProcessingState.FAILED
                DocumentProcessingState.UNSUPPORTED_FORMAT -> DocumentProcessingState.UNSUPPORTED_FORMAT
                DocumentProcessingState.DUPLICATE -> DocumentProcessingState.DUPLICATE
                DocumentProcessingState.NEEDS_MANUAL_REVIEW -> DocumentProcessingState.NEEDS_MANUAL_REVIEW
                else -> DocumentProcessingState.COMPLETED
            }

            updated = updated.copy(
                documentId = persistedDocId,
                storedFilePath = persistedDoc?.storedFilename,
                sha256 = persistedDoc?.sha256 ?: updated.sha256,
                surveyType = persistedDoc?.classifiedType ?: updated.surveyType,
                mouzaName = persistedDoc?.primaryMouza,
                processingState = finalState,
                progressFraction = 1.0f,
                errorMessage = persistedDoc?.processingError ?: persistedDoc?.errorMessage,
                updatedAt = System.currentTimeMillis()
            )
            docDao.updateQueueItem(updated)
            updated
        } catch (e: Exception) {
            val failed = current.copy(
                processingState = DocumentProcessingState.FAILED,
                progressFraction = 0.0f,
                errorMessage = e.localizedMessage ?: "Import processing error",
                retryCount = current.retryCount + 1,
                updatedAt = System.currentTimeMillis()
            )
            docDao.updateQueueItem(failed)
            docDao.insertAuditLog(
                ImportAuditLogEntity(
                    documentId = current.documentId ?: current.queueItemId,
                    eventType = "IMPORT_FAILED",
                    sourceType = current.sourceType,
                    sourceReference = current.sourceUri,
                    sha256 = current.sha256,
                    fileSizeBytes = current.fileSizeBytes,
                    details = "Error: ${e.localizedMessage ?: "Unknown exception"}"
                )
            )
            failed
        }
    }

    suspend fun retryFailedItems(): Int = withContext(Dispatchers.IO) {
        val failedList = docDao.getFailedQueueItems()
        var resetCount = 0
        for (item in failedList) {
            if (!item.isZeroByte) {
                docDao.updateQueueItem(
                    item.copy(
                        processingState = DocumentProcessingState.PENDING,
                        progressFraction = 0.0f,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                resetCount++
            }
        }
        resetCount
    }

    suspend fun cancelQueuedItem(queueItemId: String) = withContext(Dispatchers.IO) {
        docDao.deleteQueueItemById(queueItemId)
    }

    /**
     * Removes duplicate entries from the import queue without deleting any original evidence files.
     */
    suspend fun removeDuplicatesFromQueue() = withContext(Dispatchers.IO) {
        docDao.removeDuplicateQueueItems()
    }

    suspend fun clearQueue() = withContext(Dispatchers.IO) {
        docDao.clearQueueItems()
    }

    suspend fun recordHumanCorrectionAudit(
        documentId: String,
        fieldName: String,
        previousValue: String?,
        newValue: String,
        actor: String = "USER"
    ) = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentByIdOnce(documentId)
        docDao.insertAuditLog(
            ImportAuditLogEntity(
                documentId = documentId,
                eventType = "MANUAL_CORRECTION",
                sourceType = doc?.sourceType ?: "MANUAL",
                sourceReference = doc?.sourceFilePath ?: documentId,
                sha256 = doc?.sha256.orEmpty(),
                fileSizeBytes = doc?.fileSizeBytes ?: 0L,
                actor = actor,
                details = "Field '$fieldName' updated from '${previousValue ?: "null"}' to '$newValue'"
            )
        )
    }
}
