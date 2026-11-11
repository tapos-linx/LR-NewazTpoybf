package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentProcessingState
import com.example.data.repository.LandRecordRepository
import com.example.domain.ingestion.DocumentImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable Batch Extraction Worker.
 * Purges stale legacy records if present and processes all pending items in the Room-backed import queue.
 * Never reports 100% progress until all actual processing operations complete.
 */
class BatchExtractionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = LandRecordRepository(applicationContext, db)
        val importRepository = DocumentImportRepository(applicationContext, db, repository)

        try {
            repository.purgeLegacyStaleRecords()

            val pending = db.landDocumentDao().getPendingQueueItems()
            var completed = 0
            var failed = 0
            val total = pending.size

            for ((idx, item) in pending.withIndex()) {
                if (isStopped) {
                    return@withContext Result.retry()
                }
                val pct = if (total > 0) ((idx.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 99) else 0
                setProgress(
                    workDataOf(
                        "status" to "প্রক্রিয়াকরণ চলছে: ${item.originalFilename}",
                        "progress" to pct,
                        "processed" to idx,
                        "total" to total
                    )
                )
                val updated = importRepository.processQueueItem(item.queueItemId)
                if (updated?.processingState in setOf(
                        DocumentProcessingState.COMPLETED,
                        DocumentProcessingState.NEEDS_MANUAL_REVIEW,
                        DocumentProcessingState.DUPLICATE
                    )
                ) {
                    completed++
                } else {
                    failed++
                }
            }

            setProgress(
                workDataOf(
                    "status" to "প্রক্রিয়াকরণ সম্পন্ন",
                    "progress" to 100,
                    "processed" to completed,
                    "failed" to failed,
                    "total" to total
                )
            )
            Result.success(
                workDataOf(
                    "processed" to completed,
                    "failed" to failed,
                    "total" to total
                )
            )
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.localizedMessage ?: "Unknown error")))
            }
        }
    }
}
