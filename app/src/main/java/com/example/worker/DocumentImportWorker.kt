package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentProcessingState
import com.example.domain.ingestion.DocumentImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable WorkManager Worker for Multi-Source Document Import & Extraction (Phase 2 & Phase 4).
 * Reads pending or interrupted queue items from Room (`import_queue_items`), processes each item
 * through validation, sandbox copy, OCR/parsing, and updates genuine progress only as items finish.
 */
class DocumentImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val docDao = db.landDocumentDao()
        val importRepository = DocumentImportRepository(applicationContext, db)

        try {
            // Recover any items left in IMPORTING / VALIDATING / PROCESSING state from an interrupted run
            val allQueueItems = docDao.getAllQueueItemsOnce()
            for (item in allQueueItems) {
                if (item.processingState in setOf(
                        DocumentProcessingState.IMPORTING,
                        DocumentProcessingState.VALIDATING,
                        DocumentProcessingState.PROCESSING
                    )
                ) {
                    docDao.updateQueueItem(
                        item.copy(
                            processingState = DocumentProcessingState.PENDING,
                            progressFraction = 0f,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            val pendingItems = docDao.getPendingQueueItems()
            if (pendingItems.isEmpty()) {
                setProgress(
                    workDataOf(
                        "status" to "IDLE",
                        "processed_count" to 0,
                        "total_count" to 0,
                        "progress" to 100
                    )
                )
                return@withContext Result.success(
                    workDataOf(
                        "processed_count" to 0,
                        "failed_count" to 0
                    )
                )
            }

            var completedCount = 0
            var failedCount = 0
            val total = pendingItems.size

            for ((index, item) in pendingItems.withIndex()) {
                if (isStopped) {
                    return@withContext Result.retry()
                }

                val currentPct = ((index.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 99)
                setProgress(
                    workDataOf(
                        "status" to "PROCESSING",
                        "current_file" to item.originalFilename,
                        "processed_count" to index,
                        "total_count" to total,
                        "progress" to currentPct
                    )
                )

                val outcome = importRepository.processQueueItem(item.queueItemId)
                if (outcome?.processingState in setOf(
                        DocumentProcessingState.COMPLETED,
                        DocumentProcessingState.NEEDS_MANUAL_REVIEW,
                        DocumentProcessingState.DUPLICATE
                    )
                ) {
                    completedCount++
                } else {
                    failedCount++
                }
            }

            // Only report 100% after all actual processing operations have completed
            setProgress(
                workDataOf(
                    "status" to "COMPLETED",
                    "processed_count" to completedCount,
                    "failed_count" to failedCount,
                    "total_count" to total,
                    "progress" to 100
                )
            )

            Result.success(
                workDataOf(
                    "processed_count" to completedCount,
                    "failed_count" to failedCount,
                    "total_count" to total
                )
            )
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.localizedMessage ?: "Worker failed")))
            }
        }
    }
}
