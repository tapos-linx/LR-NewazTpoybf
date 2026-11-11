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
    val recentDocuments: List<LandDocumentEntity> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val unverifiedCount: Int = 0,
    val confirmedCount: Int = 0,
    val conflictedCount: Int = 0,
    val zeroByteCount: Int = 0,
    val verifiedCount: Int = 0, // compatibility alias
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

    private val extractedRecordRepository = com.example.data.repository.ExtractedLandRecordRepository(application)

    init {
        viewModelScope.launch {
            repository.purgeLegacyStaleRecords()
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

        val total = docs.size
        val unverified = docs.count { it.status.equals("UNVERIFIED", ignoreCase = true) || it.status.equals("EXTRACTED", ignoreCase = true) }
        val confirmed = docs.count { it.status.equals("CONFIRMED", ignoreCase = true) || it.status.equals("VERIFIED", ignoreCase = true) }
        val conflicted = docs.count { it.status.equals("CONFLICTED", ignoreCase = true) || it.status.equals("CONTRADICTORY", ignoreCase = true) }
        val zeroByte = docs.count { it.isZeroByte }
        val recents = docs.take(5)

        HomeUiState(
            documents = docs,
            filteredDocuments = filtered,
            recentDocuments = recents,
            selectedCategory = category,
            searchQuery = query,
            totalCount = total,
            unverifiedCount = unverified,
            confirmedCount = confirmed,
            conflictedCount = conflicted,
            zeroByteCount = zeroByte,
            verifiedCount = confirmed,
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

    fun deleteDocuments(ids: Collection<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                repository.deleteDocument(id)
            }
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

    fun importFilesFromUris(uris: List<Uri>, onFinished: ((Int) -> Unit)? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "পিসি ও ফোন মেমোরি থেকে ${uris.size}টি ফাইল যাচাই হচ্ছে..."
            try {
                val scanner = com.example.domain.saf.SafFolderScanner(getApplication())
                val summary = scanner.scanMultipleUris(uris, defaultCategory = "পিসি_ও_ফোন_বাল্ক_আপলোড")
                val validFiles = summary.supportedFiles.filter { !it.isDuplicate }
                var importedCount = 0
                for ((index, discovered) in validFiles.withIndex()) {
                    _processingMessage.value = "বাল্ক নথি প্রক্রিয়াকরণ (${index + 1}/${validFiles.size}): ${discovered.name}"
                    try {
                        repository.processImportedFile(discovered)
                        importedCount++
                    } catch (_: Exception) {
                    }
                }
                _processingMessage.value = "সফলভাবে $importedCount টি নথি যুক্ত হয়েছে"
                onFinished?.invoke(importedCount)
            } catch (_: Exception) {
                onFinished?.invoke(0)
            } finally {
                _isBatchProcessing.value = false
            }
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
            ExportFormat.PDF -> {
                buildString {
                    appendLine("📄 সামগ্রিক ভূমি রেকর্ড ফরেনসিক PDF ডসিয়ার (.PDF)")
                    appendLine("==================================================")
                    appendLine("ফরম্যাট: A4 মুদ্রণযোগ্য মাস্টার ইনডেক্স ও প্রতিটি নথির বিস্তারিত টেবিল")
                    appendLine("মোট অন্তর্ভুক্ত নথি: ${all.size} টি")
                    appendLine("--------------------------------------------------")
                    all.take(15).forEachIndexed { idx, doc ->
                        appendLine("${idx + 1}. ${doc.title} (${doc.classifiedType}) - মৌজা: ${doc.primaryMouza ?: "—"}, খতিয়ান: ${doc.primaryKhatianNo ?: "—"}, দাগ: ${doc.primaryDagNo ?: "—"}")
                    }
                }
            }
            ExportFormat.ZIP -> {
                buildString {
                    appendLine("📦 সম্পূর্ণ ফরেনসিক আর্কাইভ বান্ডেল (.ZIP)")
                    appendLine("==================================================")
                    appendLine("মোট অন্তর্ভুক্ত নথি: ${all.size} টি")
                    appendLine("অন্তর্ভুক্ত ফাইলসমূহ:")
                    appendLine("  ১. AUDIT_MANIFEST.csv (সকল নথির মেটাডাটা ও SHA-256 স্ট্যাটাস)")
                    appendLine("  ২. land_records_detailed.csv (মালিক, খতিয়ান, দাগ ও অংশের তালিকা)")
                    appendLine("  ৩. land_records_dossier.pdf (A4 ফরম্যাটেড PDF ডসিয়ার)")
                    appendLine("  ৪. land_records_full.json (স্ট্রাকচার্ড JSON ডাটাবেস এক্সপোর্ট)")
                    appendLine("  ৫. land_records_summary.txt (আইনি সারসংক্ষেপ প্রতিবেদন)")
                    appendLine("  ৬. documents/<নথি>/ (সকল মূল PDF, ছবি, ও প্রসেসকৃত পাতা)")
                    appendLine("--------------------------------------------------")
                    all.take(15).forEachIndexed { idx, doc ->
                        appendLine("${idx + 1}. ${doc.title} (${doc.classifiedType}) - খতিয়ান: ${doc.primaryKhatianNo ?: "—"}, দাগ: ${doc.primaryDagNo ?: "—"}")
                    }
                    if (all.size > 15) {
                        appendLine("... এবং আরও ${all.size - 15} টি নথি")
                    }
                }
            }
        }
    }

    /**
     * Saves consolidated multi-record summary report, formatted PDF, or full ZIP archive directly to local SAF / PC disk URI.
     */
    fun saveBatchReportToSafUri(
        uri: Uri,
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "পিসি / লোকাল ডিস্কে ফাইল সংরক্ষণ হচ্ছে..."

            val all = uiState.value.documents
            val result = when (format) {
                ExportFormat.ZIP -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    val zipBytes = ReportExportManager.buildFullMassExportZipBytes(getApplication(), all, pairs)
                    ReportExportManager.writeBytesToSafUri(getApplication(), uri, zipBytes)
                }
                ExportFormat.PDF -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                    ReportExportManager.writeBytesToSafUri(getApplication(), uri, pdfBytes)
                }
                else -> {
                    val content = getBatchPreviewContent(format)
                    ReportExportManager.writeContentToSafUri(getApplication(), uri, content)
                }
            }
            _isBatchProcessing.value = false

            if (result.isSuccess) {
                val msg = "সফলভাবে লোকাল ডিস্কে সংরক্ষিত হয়েছে (${format.extension.uppercase()})"
                _showExportBatchDialog.value = false
                onComplete(true, msg)
            } else {
                val err = "সংরক্ষণ ব্যর্থ হয়েছে: ${result.exceptionOrNull()?.localizedMessage}"
                onComplete(false, err)
            }
        }
    }

    /**
     * Saves consolidated multi-record summary report, formatted PDF, or full ZIP archive directly into
     * Phone Memory (Downloads/NewazLandRecords/) with a single click.
     */
    fun saveBatchReportToPhoneDownloads(
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "ফোন মেমোরি (Downloads) ফোল্ডারে ডাউনলোড হচ্ছে..."

            val all = uiState.value.documents
            val fileName = suggestBatchFileName(format)
            val result = when (format) {
                ExportFormat.ZIP -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    val zipBytes = ReportExportManager.buildFullMassExportZipBytes(getApplication(), all, pairs)
                    ReportExportManager.saveBytesToPhoneDownloads(getApplication(), fileName, format.mimeType, zipBytes)
                }
                ExportFormat.PDF -> {
                    val pairs = all.map { doc ->
                        val data = repository.parseDataJson(doc.extractedDataJson)
                        Pair(doc, data)
                    }
                    val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                    ReportExportManager.saveBytesToPhoneDownloads(getApplication(), fileName, format.mimeType, pdfBytes)
                }
                else -> {
                    val content = getBatchPreviewContent(format)
                    ReportExportManager.saveTextToPhoneDownloads(getApplication(), fileName, format.mimeType, content)
                }
            }
            _isBatchProcessing.value = false

            if (result.isSuccess) {
                val savedPath = result.getOrNull() ?: "Downloads/$fileName"
                val msg = "ফোন মেমোরিতে সংরক্ষিত হয়েছে: $savedPath"
                _showExportBatchDialog.value = false
                onComplete(true, msg)
            } else {
                val err = "ডাউনলোড ব্যর্থ হয়েছে: ${result.exceptionOrNull()?.localizedMessage}"
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

    fun shareBatchReport(format: ExportFormat) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "শেয়ার করার জন্য ফাইল প্রস্তুত হচ্ছে..."
            try {
                val app = getApplication<Application>()
                val all = uiState.value.documents
                val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
                val fileName = suggestBatchFileName(format)
                val outFile = File(exportDir, fileName)
                when (format) {
                    ExportFormat.ZIP -> {
                        val pairs = all.map { doc ->
                            val data = repository.parseDataJson(doc.extractedDataJson)
                            Pair(doc, data)
                        }
                        val zipBytes = ReportExportManager.buildFullMassExportZipBytes(app, all, pairs)
                        outFile.writeBytes(zipBytes)
                    }
                    ExportFormat.PDF -> {
                        val pairs = all.map { doc ->
                            val data = repository.parseDataJson(doc.extractedDataJson)
                            Pair(doc, data)
                        }
                        val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                        outFile.writeBytes(pdfBytes)
                    }
                    else -> {
                        val content = getBatchPreviewContent(format)
                        outFile.writeText(content, Charsets.UTF_8)
                    }
                }
                _isBatchProcessing.value = false
                ReportExportManager.shareFile(app, outFile, format.mimeType, "ভূমি রেকর্ড বাল্ক আর্কাইভ ($fileName)")
            } catch (_: Exception) {
                _isBatchProcessing.value = false
            }
        }
    }

    fun exportRecords(
        format: ExportFormat,
        categoryFilter: String = "ALL",
        onComplete: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isBatchProcessing.value = true
            _processingMessage.value = "রিপোর্ট তৈরি হচ্ছে..."
            try {
                val app = getApplication<Application>()
                val all = if (categoryFilter == "ALL") {
                    uiState.value.documents
                } else {
                    uiState.value.documents.filter { it.classifiedType.equals(categoryFilter, ignoreCase = true) }
                }
                val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
                val fileName = suggestBatchFileName(format)
                val outFile = File(exportDir, fileName)
                when (format) {
                    ExportFormat.ZIP -> {
                        val pairs = all.map { doc ->
                            val data = repository.parseDataJson(doc.extractedDataJson)
                            Pair(doc, data)
                        }
                        val zipBytes = ReportExportManager.buildFullMassExportZipBytes(app, all, pairs)
                        outFile.writeBytes(zipBytes)
                    }
                    ExportFormat.PDF -> {
                        val pairs = all.map { doc ->
                            val data = repository.parseDataJson(doc.extractedDataJson)
                            Pair(doc, data)
                        }
                        val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                        outFile.writeBytes(pdfBytes)
                    }
                    ExportFormat.CSV -> {
                        val csvFile = ReportExportManager.generateDocumentsCsv(app, all)
                        csvFile.copyTo(outFile, overwrite = true)
                    }
                    else -> {
                        val content = getBatchPreviewContent(format)
                        outFile.writeText(content, Charsets.UTF_8)
                    }
                }
                _isBatchProcessing.value = false
                onComplete(outFile)
            } catch (_: Exception) {
                _isBatchProcessing.value = false
            }
        }
    }

    fun clearExport() {
        _exportedFile.value = null
    }
}
