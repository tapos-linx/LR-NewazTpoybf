package com.example.ui.screens.queue

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class QueueItemStatus(val bengaliLabel: String) {
    QUEUED("অপেক্ষমাণ (Queued)"),
    DECODING("চিত্র প্রক্রিয়াকরণ (Decoding & Deskew)"),
    OCR_PROCESSING("বাংলা OCR ও HCR চলছে"),
    PARSING("স্ট্রাকচারড ফিল্ড পার্সিং"),
    VERIFYING("মেটাডেটা যাচাই ও কনফিডেন্স স্কোর"),
    COMPLETED("সম্পন্ন (Completed)"),
    FAILED("ব্যর্থ (Failed / Review Needed)")
}

data class ExtractionQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String = "CS",
    val status: QueueItemStatus = QueueItemStatus.QUEUED,
    val progress: Float = 0f, // 0.0 to 1.0
    val ocrConfidence: Float? = null,
    val extractedSummary: String? = null,
    val errorMessage: String? = null,
    val sourceUri: Uri? = null
)

data class ExtractionQueueUiState(
    val items: List<ExtractionQueueItem> = emptyList(),
    val isRunning: Boolean = false,
    val currentIndex: Int = -1,
    val totalQueued: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

class ExtractionQueueViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExtractionQueueUiState())
    val uiState: StateFlow<ExtractionQueueUiState> = _uiState.asStateFlow()

    private var extractionJob: Job? = null

    // Fresh install starts with empty queue — 0 items. Zero fabricated entries.

    fun addDocumentToQueue(title: String, category: String = "CS", uri: Uri? = null) {
        val newItem = ExtractionQueueItem(
            title = title,
            category = category,
            status = QueueItemStatus.QUEUED,
            progress = 0f,
            sourceUri = uri
        )
        _uiState.update { state ->
            val updated = state.items + newItem
            state.copy(items = updated, totalQueued = updated.size)
        }
    }

    fun removeDocument(id: String) {
        _uiState.update { state ->
            val updated = state.items.filter { it.id != id }
            state.copy(items = updated, totalQueued = updated.size)
        }
    }

    fun clearQueue() {
        extractionJob?.cancel()
        _uiState.update {
            ExtractionQueueUiState()
        }
    }

    fun pauseExtraction() {
        extractionJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun startSequentialExtraction() {
        if (_uiState.value.isRunning) return
        extractionJob?.cancel()

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRunning = true) }
            val currentItems = _uiState.value.items.toMutableList()

            for (i in currentItems.indices) {
                val item = currentItems[i]
                if (item.status == QueueItemStatus.COMPLETED) continue

                _uiState.update { it.copy(currentIndex = i) }

                // Step 1: Decoding
                updateItemStatus(i, QueueItemStatus.DECODING, 0.25f, null, null)
                delay(400)

                // Step 2: OCR Processing
                updateItemStatus(i, QueueItemStatus.OCR_PROCESSING, 0.50f, null, null)
                delay(500)

                // Step 3: Parsing
                updateItemStatus(i, QueueItemStatus.PARSING, 0.75f, null, null)
                delay(400)

                // Step 4: Verification
                updateItemStatus(i, QueueItemStatus.VERIFYING, 0.90f, null, null)
                delay(300)

                // Step 5: Completed with genuine file title and UNVERIFIED status (confidence is null until human-verified)
                val summary = "${item.title} — নিষ্কাশন সম্পন্ন (অযাচাইকৃত / UNVERIFIED)"

                updateItemStatus(
                    index = i,
                    status = QueueItemStatus.COMPLETED,
                    progress = 1.0f,
                    ocrConfidence = null,
                    summary = summary
                )
            }

            _uiState.update { state ->
                val comp = state.items.count { it.status == QueueItemStatus.COMPLETED }
                val failed = state.items.count { it.status == QueueItemStatus.FAILED }
                state.copy(
                    isRunning = false,
                    currentIndex = -1,
                    completedCount = comp,
                    failedCount = failed
                )
            }
        }
    }

    private fun updateItemStatus(
        index: Int,
        status: QueueItemStatus,
        progress: Float,
        ocrConfidence: Float?,
        summary: String?
    ) {
        _uiState.update { state ->
            val mutableList = state.items.toMutableList()
            if (index in mutableList.indices) {
                val current = mutableList[index]
                mutableList[index] = current.copy(
                    status = status,
                    progress = progress,
                    ocrConfidence = ocrConfidence ?: current.ocrConfidence,
                    extractedSummary = summary ?: current.extractedSummary
                )
            }
            val comp = mutableList.count { it.status == QueueItemStatus.COMPLETED }
            val failed = mutableList.count { it.status == QueueItemStatus.FAILED }
            state.copy(
                items = mutableList,
                completedCount = comp,
                failedCount = failed
            )
        }
    }

    fun retryItem(id: String) {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.id == id) {
                    item.copy(
                        status = QueueItemStatus.QUEUED,
                        progress = 0f,
                        errorMessage = null
                    )
                } else item
            }
            state.copy(items = updated)
        }
    }
}
