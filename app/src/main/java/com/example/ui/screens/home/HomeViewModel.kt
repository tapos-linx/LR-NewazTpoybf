package com.example.ui.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.LandRecordType
import com.example.data.repository.LandRecordRepository
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.domain.saf.DiscoveredFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val documents: List<LandDocumentEntity> = emptyList(),
    val filteredDocuments: List<LandDocumentEntity> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val verifiedCount: Int = 0,
    val zeroByteCount: Int = 0,
    val isBatchProcessing: Boolean = false,
    val processingMessage: String = "",
    val exportedFile: File? = null,
    val showExportBatchDialog: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val repository = LandRecordRepository(application)

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing = _isBatchProcessing.asStateFlow()

    private val _processingMessage = MutableStateFlow("")
    val processingMessage = _processingMessage.asStateFlow()

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile = _exportedFile.asStateFlow()

    private val _showExportBatchDialog = MutableStateFlow(false)
    val showExportBatchDialog = _showExportBatchDialog.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialSampleRecordsIfEmpty()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(repository.allDocuments, _selectedCategory, _searchQuery) { docs, category, query ->
            Triple(docs, category, query)
        },
        _isBatchProcessing,
        _processingMessage,
        _showExportBatchDialog
    ) { (docs, category, query), isProcessing, msg, showBatchDialog ->
        val filtered = docs.filter { doc ->
            val matchesCategory = when (category) {
                "ALL" -> true
                "ZERO_BYTE" -> doc.isZeroByte
                else -> doc.classifiedType.equals(category, ignoreCase = true) ||
                        doc.sourceCategory.equals(category, ignoreCase = true)
            }

            val matchesQuery = if (query.isBlank()) true else {
                doc.title.contains(query, ignoreCase = true) ||
                        (doc.primaryKhatianNo ?: "").contains(query, ignoreCase = true) ||
                        (doc.primaryDagNo ?: "").contains(query, ignoreCase = true) ||
                        (doc.primaryMouza ?: "").contains(query, ignoreCase = true) ||
                        (doc.primaryDistrict ?: "").contains(query, ignoreCase = true) ||
                        (doc.ownersSummary ?: "").contains(query, ignoreCase = true)
            }

            matchesCategory && matchesQuery
        }

        HomeUiState(
            documents = docs,
            filteredDocuments = filtered,
            selectedCategory = category,
            searchQuery = query,
            totalCount = docs.size,
            verifiedCount = docs.count { it.status == "VERIFIED" },
            zeroByteCount = docs.count { it.isZeroByte },
            isBatchProcessing = isProcessing,
            processingMessage = msg,
            exportedFile = _exportedFile.value,
            showExportBatchDialog = showBatchDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
        }
    }

    fun importDiscoveredFile(discovered: DiscoveredFile, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "ফাইল প্রক্রিয়াকরণ হচ্ছে: ${discovered.name}"
            val docId = repository.processImportedFile(discovered)
            _isBatchProcessing.value = false
            onComplete(docId)
        }
    }

    fun importFilesFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            for ((index, uri) in uris.withIndex()) {
                val filename = "আহরিত_নথি_${System.currentTimeMillis()}_${index + 1}.pdf"
                _processingMessage.value = "নথি পড়া হচ্ছে (${index + 1}/${uris.size})..."

                val discovered = DiscoveredFile(
                    uri = uri,
                    name = filename,
                    parentFolder = "input_import",
                    mimeType = "application/pdf",
                    sizeBytes = 1024L,
                    isZeroByte = false,
                    format = "PDF",
                    classifiedType = LandRecordType.fromFolderOrName(filename)
                )
                repository.processImportedFile(discovered)
            }
            _isBatchProcessing.value = false
        }
    }

    fun setShowExportBatchDialog(show: Boolean) {
        _isBatchProcessing.value = false
        _showExportBatchDialog.value = show
    }

    fun suggestBatchFileName(format: ExportFormat): String {
        return ReportExportManager.suggestFileName(null, format)
    }

    fun getBatchPreviewContent(format: ExportFormat): String {
        val all = uiState.value.documents
        return when (format) {
            ExportFormat.JSON -> {
                val pairs = all.map { doc ->
                    val data = repository.parseDataJson(doc.extractedDataJson)
                    Pair(doc, data)
                }
                ReportExportManager.buildBatchJsonReportContent(pairs)
            }
            ExportFormat.TEXT -> {
                val pairs = all.map { doc ->
                    val data = repository.parseDataJson(doc.extractedDataJson)
                    Pair(doc, data)
                }
                ReportExportManager.buildBatchTextSummaryContent(pairs)
            }
            ExportFormat.CSV -> {
                ReportExportManager.buildDocumentsCsvContent(all)
            }
        }
    }

    /**
     * Saves consolidated multi-record summary report directly to local SAF URI.
     */
    fun saveBatchReportToSafUri(
        uri: Uri,
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "রিপোর্ট ফাইলে লেখা হচ্ছে..."

            val all = uiState.value.documents
            val content = when (format) {
                ExportFormat.JSON -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    ReportExportManager.buildBatchJsonReportContent(pairs)
                }
                ExportFormat.TEXT -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    ReportExportManager.buildBatchTextSummaryContent(pairs)
                }
                ExportFormat.CSV -> {
                    ReportExportManager.buildDocumentsCsvContent(all)
                }
            }

            val result = ReportExportManager.writeContentToSafUri(getApplication(), uri, content)
            _isBatchProcessing.value = false

            if (result.isSuccess) {
                val msg = "সকল নথির মেটাডাটা সফলভাবে ডিভাইসে সংরক্ষিত হয়েছে (${format.extension.uppercase()})"
                _showExportBatchDialog.value = false
                onComplete(true, msg)
            } else {
                val err = "সংরক্ষণ ব্যর্থ হয়েছে: ${result.exceptionOrNull()?.localizedMessage}"
                onComplete(false, err)
            }
        }
    }

    fun exportAllToCsv(onSuccess: (File) -> Unit) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "CSV রিপোর্ট তৈরি হচ্ছে..."

            val all = uiState.value.documents
            val csvFile = ReportExportManager.generateDocumentsCsv(getApplication(), all)
            _isBatchProcessing.value = false
            _exportedFile.value = csvFile
            onSuccess(csvFile)
        }
    }

    fun clearExport() {
        _exportedFile.value = null
    }
}
