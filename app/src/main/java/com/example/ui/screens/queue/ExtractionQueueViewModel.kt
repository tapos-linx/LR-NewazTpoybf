package com.example.ui.screens.queue

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentProcessingState
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.repository.LandRecordRepository
import com.example.domain.ingestion.DocumentImportManager
import com.example.domain.ingestion.DocumentImportRepository
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class QueueItemStatus(val bengaliLabel: String) {
    QUEUED("অপেক্ষমাণ (PENDING)"),
    DECODING("আমদানি ও যাচাই (IMPORTING / VALIDATING)"),
    OCR_PROCESSING("বাংলা OCR ও HCR চলছে (PROCESSING)"),
    PARSING("স্ট্রাকচারড ফিল্ড পার্সিং"),
    VERIFYING("মেটাডেটা যাচাই ও হ্যাশ নিরীক্ষা"),
    COMPLETED("সম্পন্ন (COMPLETED)"),
    FAILED("ব্যর্থ (FAILED / NEEDS_MANUAL_REVIEW)")
}

data class ExtractionQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String = "CS",
    val status: QueueItemStatus = QueueItemStatus.QUEUED,
    val rawProcessingState: String = DocumentProcessingState.PENDING,
    val progress: Float = 0f,
    val fileSizeBytes: Long = 0L,
    val sourceType: String = DocumentSourceType.ANDROID_SAF_FILE,
    val mouzaName: String? = null,
    val isDuplicate: Boolean = false,
    val ocrConfidence: Float? = null,
    val extractedSummary: String? = null,
    val errorMessage: String? = null,
    val sourceUri: Uri? = null,
    val discoveredFile: DiscoveredFile? = null,
    val persistedDocumentId: String? = null
)

data class ExtractionQueueUiState(
    val items: List<ExtractionQueueItem> = emptyList(),
    val filteredItems: List<ExtractionQueueItem> = emptyList(),
    val isRunning: Boolean = false,
    val currentIndex: Int = -1,
    val totalQueued: Int = 0,
    val totalBytesSelected: Long = 0L,
    val sourceBreakdown: Map<String, Int> = emptyMap(),
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val searchQuery: String = "",
    val selectedSurveyFilter: String = "ALL",
    val selectedMouzaFilter: String = "ALL"
)

class ExtractionQueueViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = LandRecordRepository(application, database)
    private val importRepository = DocumentImportRepository(application, database, repository)
    private val importManager = DocumentImportManager(application, importRepository)
    private val scanner = SafFolderScanner(application)

    private val _uiState = MutableStateFlow(ExtractionQueueUiState())
    val uiState: StateFlow<ExtractionQueueUiState> = _uiState.asStateFlow()

    private var extractionJob: Job? = null

    init {
        // Observe durable Room-persisted queue items so state survives process restarts
        viewModelScope.launch {
            importRepository.allQueueItems.collect { entities ->
                if (entities.isNotEmpty()) {
                    val mapped = entities.map { it.toUiItem() }
                    _uiState.update { state ->
                        recomputeState(state.copy(items = mapped))
                    }
                }
            }
        }
    }

    private fun ImportQueueItemEntity.toUiItem(): ExtractionQueueItem {
        val mappedStatus = when (processingState) {
            DocumentProcessingState.PENDING, DocumentProcessingState.READY_FOR_PROCESSING -> QueueItemStatus.QUEUED
            DocumentProcessingState.IMPORTING, DocumentProcessingState.IMPORTED, DocumentProcessingState.VALIDATING -> QueueItemStatus.DECODING
            DocumentProcessingState.PROCESSING -> QueueItemStatus.OCR_PROCESSING
            DocumentProcessingState.COMPLETED, DocumentProcessingState.NEEDS_MANUAL_REVIEW, DocumentProcessingState.DUPLICATE -> QueueItemStatus.COMPLETED
            DocumentProcessingState.FAILED, DocumentProcessingState.UNSUPPORTED_FORMAT, DocumentProcessingState.ZERO_BYTE -> QueueItemStatus.FAILED
            else -> QueueItemStatus.QUEUED
        }
        val summaryText = when (processingState) {
            DocumentProcessingState.COMPLETED -> "$originalFilename — নিষ্কাশন সম্পন্ন (UNVERIFIED)"
            DocumentProcessingState.NEEDS_MANUAL_REVIEW -> "$originalFilename — ম্যানুয়াল রিভিউ প্রয়োজন (NEEDS_MANUAL_REVIEW)"
            DocumentProcessingState.DUPLICATE -> "$originalFilename — ডুপ্লিকেট শনাক্ত (মূল প্রমাণ সংরক্ষিত)"
            DocumentProcessingState.UNSUPPORTED_FORMAT -> "$originalFilename — অসমর্থিত ফরম্যাট ($fileFormat)"
            DocumentProcessingState.FAILED -> "ত্রুটি: ${errorMessage ?: "প্রক্রিয়াকরণ ব্যর্থ"}"
            else -> null
        }
        return ExtractionQueueItem(
            id = queueItemId,
            title = originalFilename,
            category = surveyType,
            status = mappedStatus,
            rawProcessingState = processingState,
            progress = progressFraction,
            fileSizeBytes = fileSizeBytes,
            sourceType = sourceType,
            mouzaName = mouzaName,
            isDuplicate = isDuplicate,
            extractedSummary = summaryText,
            errorMessage = errorMessage,
            sourceUri = if (sourceUri.isNotBlank()) Uri.parse(sourceUri) else null,
            persistedDocumentId = documentId
        )
    }

    private fun recomputeState(state: ExtractionQueueUiState): ExtractionQueueUiState {
        val q = state.searchQuery.trim().lowercase()
        val survey = state.selectedSurveyFilter
        val mouza = state.selectedMouzaFilter

        val filtered = state.items.filter { item ->
            val matchesQuery = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                (item.mouzaName?.lowercase()?.contains(q) == true) ||
                item.category.lowercase().contains(q)
            val matchesSurvey = survey == "ALL" || item.category.equals(survey, ignoreCase = true)
            val matchesMouza = mouza == "ALL" || (item.mouzaName?.equals(mouza, ignoreCase = true) == true)
            matchesQuery && matchesSurvey && matchesMouza
        }

        val comp = state.items.count { it.status == QueueItemStatus.COMPLETED }
        val failed = state.items.count { it.status == QueueItemStatus.FAILED }
        val dups = state.items.count { it.isDuplicate }
        val totalBytes = state.items.sumOf { it.fileSizeBytes }
        val breakdown = state.items.groupBy { it.sourceType }.mapValues { it.value.size }

        return state.copy(
            filteredItems = filtered,
            totalQueued = state.items.size,
            totalBytesSelected = totalBytes,
            sourceBreakdown = breakdown,
            completedCount = comp,
            failedCount = failed,
            skippedDuplicateCount = dups
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { recomputeState(it.copy(searchQuery = query)) }
    }

    fun updateSurveyFilter(surveyType: String) {
        _uiState.update { recomputeState(it.copy(selectedSurveyFilter = surveyType)) }
    }

    fun updateMouzaFilter(mouza: String) {
        _uiState.update { recomputeState(it.copy(selectedMouzaFilter = mouza)) }
    }

    fun addDocumentToQueue(title: String, category: String = "CS", uri: Uri? = null) {
        val newItem = ExtractionQueueItem(
            title = title,
            category = category,
            status = QueueItemStatus.QUEUED,
            progress = 0f,
            sourceUri = uri
        )
        _uiState.update { state ->
            recomputeState(state.copy(items = state.items + newItem))
        }
    }

    fun addDiscoveredFilesToQueue(
        files: List<DiscoveredFile>,
        sourceType: String = DocumentSourceType.ANDROID_SAF_FILE,
        autoStart: Boolean = true
    ) {
        if (files.isEmpty()) return
        val newItems = files.map { file ->
            ExtractionQueueItem(
                title = file.name,
                category = file.classifiedType.name,
                status = if (file.isZeroByte) QueueItemStatus.FAILED else QueueItemStatus.QUEUED,
                rawProcessingState = if (file.isZeroByte) DocumentProcessingState.FAILED else if (file.isDuplicate) DocumentProcessingState.DUPLICATE else DocumentProcessingState.PENDING,
                progress = 0f,
                fileSizeBytes = file.sizeBytes,
                sourceType = sourceType,
                isDuplicate = file.isDuplicate,
                sourceUri = file.uri,
                discoveredFile = file
            )
        }
        _uiState.update { state ->
            val existingUris = state.items.mapNotNull { it.sourceUri }.toSet()
            val deduped = newItems.filter { it.sourceUri == null || it.sourceUri !in existingUris }
            recomputeState(state.copy(items = state.items + deduped))
        }

        viewModelScope.launch(Dispatchers.IO) {
            importRepository.enqueueDiscoveredFiles(files, sourceType = sourceType)
            if (autoStart) {
                startSequentialExtraction()
            }
        }
    }

    fun removeDocument(id: String) {
        _uiState.update { state ->
            recomputeState(state.copy(items = state.items.filter { it.id != id }))
        }
        viewModelScope.launch(Dispatchers.IO) {
            importRepository.cancelQueuedItem(id)
        }
    }

    fun removeDuplicateFilesOnly() {
        _uiState.update { state ->
            recomputeState(state.copy(items = state.items.filter { !it.isDuplicate }))
        }
        viewModelScope.launch(Dispatchers.IO) {
            importRepository.removeDuplicatesFromQueue()
        }
    }

    fun clearQueue() {
        extractionJob?.cancel()
        importManager.cancelBackgroundImportWork()
        _uiState.update { ExtractionQueueUiState() }
        viewModelScope.launch(Dispatchers.IO) {
            importRepository.clearQueue()
        }
    }

    fun pauseExtraction() {
        extractionJob?.cancel()
        importManager.cancelBackgroundImportWork()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun retryFailedImports() {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.status == QueueItemStatus.FAILED) {
                    item.copy(
                        status = QueueItemStatus.QUEUED,
                        rawProcessingState = DocumentProcessingState.PENDING,
                        progress = 0f,
                        errorMessage = null
                    )
                } else item
            }
            recomputeState(state.copy(items = updated))
        }
        viewModelScope.launch(Dispatchers.IO) {
            importRepository.retryFailedItems()
            startSequentialExtraction()
        }
    }

    fun startSequentialExtraction() {
        if (_uiState.value.isRunning) return
        extractionJob?.cancel()

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRunning = true) }
            val currentItems = _uiState.value.items.toMutableList()

            for (i in currentItems.indices) {
                val item = _uiState.value.items.getOrNull(i) ?: continue
                if (item.status == QueueItemStatus.COMPLETED) continue

                _uiState.update { it.copy(currentIndex = i) }

                try {
                    updateItemStatus(
                        index = i,
                        status = QueueItemStatus.DECODING,
                        rawState = DocumentProcessingState.IMPORTING,
                        progress = 0.25f,
                        ocrConfidence = null,
                        summary = null
                    )

                    val discovered = item.discoveredFile ?: item.sourceUri?.let { uri ->
                        scanner.scanMultipleUris(listOf(uri), item.category).supportedFiles.firstOrNull()
                    }

                    updateItemStatus(
                        index = i,
                        status = QueueItemStatus.OCR_PROCESSING,
                        rawState = DocumentProcessingState.PROCESSING,
                        progress = 0.60f,
                        ocrConfidence = null,
                        summary = null
                    )

                    var persistedDocId: String? = null
                    var finalRawState = DocumentProcessingState.COMPLETED
                    if (discovered != null) {
                        persistedDocId = repository.processImportedFile(
                            discovered = discovered,
                            sourceTypeOverride = item.sourceType
                        )
                        val persistedDoc = repository.getDocumentOnce(persistedDocId)
                        finalRawState = persistedDoc?.processingStatus ?: DocumentProcessingState.COMPLETED
                        if (finalRawState == DocumentProcessingState.FAILED || persistedDoc?.status == DocumentProcessingState.ZERO_BYTE) {
                            throw IllegalStateException(persistedDoc?.processingError ?: persistedDoc?.errorMessage ?: "Processing failed")
                        }
                    }

                    updateItemStatus(
                        index = i,
                        status = QueueItemStatus.PARSING,
                        rawState = DocumentProcessingState.VALIDATING,
                        progress = 0.85f,
                        ocrConfidence = null,
                        summary = null
                    )

                    val summary = "${item.title} — নিষ্কাশন সম্পন্ন (অযাচাইকৃত / UNVERIFIED)"
                    updateItemStatus(
                        index = i,
                        status = QueueItemStatus.COMPLETED,
                        rawState = finalRawState,
                        progress = 1.0f,
                        ocrConfidence = null,
                        summary = summary,
                        persistedDocId = persistedDocId
                    )
                } catch (e: Exception) {
                    updateItemStatus(
                        index = i,
                        status = QueueItemStatus.FAILED,
                        rawState = DocumentProcessingState.FAILED,
                        progress = 0.0f,
                        ocrConfidence = null,
                        summary = "ত্রুটি: ${e.localizedMessage ?: "প্রক্রিয়াকরণ ব্যর্থ"}",
                        errorMessage = e.localizedMessage
                    )
                }
            }

            _uiState.update { state ->
                recomputeState(
                    state.copy(
                        isRunning = false,
                        currentIndex = -1
                    )
                )
            }
        }
    }

    private fun updateItemStatus(
        index: Int,
        status: QueueItemStatus,
        rawState: String,
        progress: Float,
        ocrConfidence: Float?,
        summary: String?,
        persistedDocId: String? = null,
        errorMessage: String? = null
    ) {
        _uiState.update { state ->
            val mutableList = state.items.toMutableList()
            if (index in mutableList.indices) {
                val current = mutableList[index]
                mutableList[index] = current.copy(
                    status = status,
                    rawProcessingState = rawState,
                    progress = progress,
                    ocrConfidence = ocrConfidence ?: current.ocrConfidence,
                    extractedSummary = summary ?: current.extractedSummary,
                    persistedDocumentId = persistedDocId ?: current.persistedDocumentId,
                    errorMessage = errorMessage ?: current.errorMessage
                )
            }
            recomputeState(state.copy(items = mutableList))
        }
    }

    fun retryItem(id: String) {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.id == id) {
                    item.copy(
                        status = QueueItemStatus.QUEUED,
                        rawProcessingState = DocumentProcessingState.PENDING,
                        progress = 0f,
                        errorMessage = null
                    )
                } else item
            }
            recomputeState(state.copy(items = updated))
        }
    }
}
