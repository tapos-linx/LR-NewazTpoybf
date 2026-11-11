package com.example.domain.ingestion

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.FolderScanSummary
import com.example.worker.DocumentImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Central Multi-Source Document Import Manager (Phase 2 & Phase 4).
 * Coordinates:
 * - AndroidSafImportSource (Phone storage single & multiple files)
 * - LocalFolderImportSource (Recursive SAF directory trees & nested ZIP archives)
 * - GoogleDriveImportSource (OAuth 2.0 Drive API v3 + Workspace export + SAF Drive picker)
 * - DesktopUploadImportSource (Secure PC-to-Android Web Companion pairing & transfer)
 * - Durable WorkManager execution (`DocumentImportWorker`) with unique work names
 */
class DocumentImportManager(
    private val context: Context,
    val importRepository: DocumentImportRepository = DocumentImportRepository(context),
    val safSource: AndroidSafImportSource = AndroidSafImportSource(context),
    val folderSource: LocalFolderImportSource = LocalFolderImportSource(context),
    val googleDriveSource: GoogleDriveImportSource = GoogleDriveImportSource(context),
    val desktopCompanionSource: DesktopUploadImportSource = DesktopUploadImportSource()
) {
    companion object {
        const val UNIQUE_IMPORT_WORK_NAME = "LR_NEWAZ_UNIFIED_DOCUMENT_IMPORT_WORK"
    }

    suspend fun importFromSafUris(
        uris: List<Uri>,
        sourceType: String = DocumentSourceType.ANDROID_SAF_FILE,
        scheduleBackgroundWork: Boolean = false
    ): FolderScanSummary = withContext(Dispatchers.IO) {
        val summary = safSource.scanSelectedDocuments(uris, sourceCategory = sourceType)
        val allToQueue = summary.supportedFiles
        importRepository.enqueueDiscoveredFiles(allToQueue, sourceType = sourceType)
        if (scheduleBackgroundWork) {
            enqueueDurableImportWork()
        }
        summary
    }

    suspend fun importFromFolderTree(
        treeUri: Uri,
        sourceType: String = DocumentSourceType.ANDROID_SAF_FOLDER,
        scheduleBackgroundWork: Boolean = false
    ): FolderScanSummary = withContext(Dispatchers.IO) {
        val summary = folderSource.scanFolderTreeRecursively(treeUri)
        importRepository.enqueueDiscoveredFiles(summary.supportedFiles, sourceType = sourceType)
        if (scheduleBackgroundWork) {
            enqueueDurableImportWork()
        }
        summary
    }

    suspend fun importFromGoogleDriveApi(
        remoteFiles: List<GoogleDriveRemoteFile>,
        scheduleBackgroundWork: Boolean = false
    ): List<ImportQueueItemEntity> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "drive_api_staging_${UUID.randomUUID()}")
        stagingDir.mkdirs()

        val discoveredList = mutableListOf<DiscoveredFile>()
        for (remote in remoteFiles) {
            val downloadRes = googleDriveSource.downloadFileOrWorkspaceExport(remote, stagingDir)
            if (downloadRes.isSuccess) {
                val outcome = downloadRes.getOrThrow()
                val (fmt, mime) = DocumentValidationService.resolveFormatAndMime(outcome.originalName, outcome.effectiveMimeType)
                discoveredList.add(
                    DiscoveredFile(
                        uri = Uri.fromFile(outcome.file),
                        name = outcome.originalName,
                        parentFolder = DocumentSourceType.GOOGLE_DRIVE,
                        mimeType = mime,
                        sizeBytes = outcome.sizeBytes,
                        isZeroByte = outcome.sizeBytes == 0L,
                        format = fmt,
                        classifiedType = com.example.data.model.LandRecordType.fromFolderOrName(outcome.originalName),
                        sha256Hash = outcome.sha256,
                        isDuplicate = false
                    )
                )
            }
        }

        val queued = importRepository.enqueueDiscoveredFiles(
            files = discoveredList,
            sourceType = DocumentSourceType.GOOGLE_DRIVE
        )
        if (scheduleBackgroundWork && queued.isNotEmpty()) {
            enqueueDurableImportWork()
        }
        queued
    }

    suspend fun syncFromDesktopCompanion(
        backendBaseUrl: String,
        pairingToken: String,
        apiKey: String? = null,
        scheduleBackgroundWork: Boolean = false
    ): Result<List<ImportQueueItemEntity>> = withContext(Dispatchers.IO) {
        val sessionDocsRes = desktopCompanionSource.fetchPairedSessionUploads(backendBaseUrl, pairingToken, apiKey)
        if (sessionDocsRes.isFailure) {
            return@withContext Result.failure(sessionDocsRes.exceptionOrNull() ?: IllegalStateException("Companion sync failed"))
        }

        val remoteDocs = sessionDocsRes.getOrThrow()
        if (remoteDocs.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("NO_BYTES_TRANSFERRED: No uploaded files found in this desktop companion session"))
        }

        val stagingDir = File(context.cacheDir, "companion_staging_${UUID.randomUUID()}")
        stagingDir.mkdirs()

        val discovered = mutableListOf<DiscoveredFile>()
        for (docMeta in remoteDocs) {
            val fileRes = desktopCompanionSource.downloadCompanionDocument(
                backendBaseUrl = backendBaseUrl,
                docMeta = docMeta,
                destinationDir = stagingDir,
                pairingToken = pairingToken,
                apiKey = apiKey
            )
            if (fileRes.isSuccess) {
                val localFile = fileRes.getOrThrow()
                val (fmt, mime) = DocumentValidationService.resolveFormatAndMime(docMeta.filename, null)
                discovered.add(
                    DiscoveredFile(
                        uri = Uri.fromFile(localFile),
                        name = docMeta.filename,
                        parentFolder = DocumentSourceType.DESKTOP_COMPANION,
                        mimeType = mime,
                        sizeBytes = localFile.length(),
                        isZeroByte = localFile.length() == 0L,
                        format = fmt,
                        classifiedType = com.example.data.model.LandRecordType.fromFolderOrName(docMeta.filename),
                        sha256Hash = docMeta.sha256.ifBlank { DocumentHashService.computeSha256(localFile) },
                        isDuplicate = false
                    )
                )
            }
        }

        if (discovered.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("NO_BYTES_TRANSFERRED: Zero valid files could be verified from desktop session"))
        }

        val queued = importRepository.enqueueDiscoveredFiles(
            files = discovered,
            sourceType = DocumentSourceType.DESKTOP_COMPANION
        )
        if (scheduleBackgroundWork) {
            enqueueDurableImportWork()
        }
        Result.success(queued)
    }

    suspend fun processAllPendingInForeground(
        onProgress: (processed: Int, total: Int, currentFileName: String) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val pending = importRepository.getQueueItemsOnce().filter {
            it.processingState in setOf(
                com.example.data.local.entity.DocumentProcessingState.PENDING,
                com.example.data.local.entity.DocumentProcessingState.READY_FOR_PROCESSING,
                com.example.data.local.entity.DocumentProcessingState.IMPORTED
            )
        }
        var completed = 0
        for ((idx, item) in pending.withIndex()) {
            onProgress(idx, pending.size, item.originalFilename)
            val res = importRepository.processQueueItem(item.queueItemId)
            if (res?.processingState in setOf(
                    com.example.data.local.entity.DocumentProcessingState.COMPLETED,
                    com.example.data.local.entity.DocumentProcessingState.NEEDS_MANUAL_REVIEW
                )
            ) {
                completed++
            }
            onProgress(idx + 1, pending.size, item.originalFilename)
        }
        completed
    }

    fun enqueueDurableImportWork(batchId: String? = null) {
        val request = OneTimeWorkRequestBuilder<DocumentImportWorker>()
            .setInputData(workDataOf("batch_id" to (batchId ?: "")))
            .addTag(UNIQUE_IMPORT_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMPORT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelBackgroundImportWork() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMPORT_WORK_NAME)
    }
}
