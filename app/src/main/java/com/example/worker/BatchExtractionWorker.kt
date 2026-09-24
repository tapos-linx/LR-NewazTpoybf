package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.repository.LandRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatchExtractionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = LandRecordRepository(applicationContext)

        setProgress(workDataOf("status" to "ইনdexing records...", "progress" to 10))

        try {
            // Seed sample records if database is fresh
            repository.seedInitialSampleRecordsIfEmpty()

            setProgress(workDataOf("status" to "প্রক্রিয়াকরণ সম্পন্ন", "progress" to 100))
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.localizedMessage ?: "Unknown error")))
        }
    }
}
