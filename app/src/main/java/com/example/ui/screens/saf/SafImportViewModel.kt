package com.example.ui.screens.saf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LandRecordRepository
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SafImportUiState(
    val isScanning: Boolean = false,
    val isProcessingBatch: Boolean = false,
    val isExporting: Boolean = false,
    val scanSummary: FolderScanSummary? = null,
    val currentProcessingIndex: Int = 0,
    val totalToProcess: Int = 0,
    val processingFileName: String = "",
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val processedCount: Int = 0
)

class SafImportViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = SafFolderScanner(application)
    private val repository = LandRecordRepository(application)

    private val _uiState = MutableStateFlow(SafImportUiState())
    val uiState: StateFlow<SafImportUiState> = _uiState.asStateFlow()

    fun scanFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                statusMessage = null
            )

            try {
                val summary = scanner.scanDirectory(treeUri)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary,
                    statusMessage = "ফোল্ডার স্ক্যান সম্পন্ন: ${summary.supportedFiles.size} টি সমর্থিত ফাইল পাওয়া গেছে"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "ফোল্ডার স্ক্যান করতে সমস্যা: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Scans multiple individual files (PDF, JPG, PNG, TIFF, ZIP, CSV, JSON, TXT)
     * selected from PC local disk or phone memory (Downloads / SD Card).
     */
    fun scanMultipleFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                statusMessage = null
            )

            try {
                val summary = scanner.scanMultipleUris(uris, defaultCategory = "পিসি_ও_ফোন_বাল্ক_আপলোড")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary,
                    statusMessage = "ফাইল যাচাই সম্পন্ন: ${summary.supportedFiles.size} টি নথি প্রক্রিয়াকরণের জন্য প্রস্তুত"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "ফাইল স্ক্যান করতে সমস্যা: ${e.localizedMessage}"
                )
            }
        }
    }

    fun startBatchProcessing(onComplete: () -> Unit) {
        val summary = _uiState.value.scanSummary ?: return
        val files = summary.supportedFiles.filter { !it.isDuplicate }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingBatch = true,
                totalToProcess = files.size,
                currentProcessingIndex = 0,
                processedCount = 0,
                errorMessage = null
            )

            var successCount = 0
            for ((index, file) in files.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    currentProcessingIndex = index + 1,
                    processingFileName = file.name
                )

                try {
                    repository.processImportedFile(file)
                    successCount++
                } catch (_: Exception) {
                    // Continue with remaining files even if one encounters an error
                }
            }

            _uiState.value = _uiState.value.copy(
                isProcessingBatch = false,
                processedCount = successCount,
                statusMessage = "সফলভাবে $successCount টি নথি ডাটাবেসে যুক্ত ও যাচাই করা হয়েছে!"
            )
            onComplete()
        }
    }

    fun suggestExportFileName(format: ExportFormat): String {
        return ReportExportManager.suggestFileName(null, format)
    }

    /**
     * One-click Mass Download of all land records directly into Phone Memory (Downloads/NewazLandRecords/).
     */
    fun downloadAllToPhoneMemory(
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                errorMessage = null,
                statusMessage = "ফোন মেমোরি (Downloads) ফোল্ডারে বাল্ক ফাইল তৈরি হচ্ছে..."
            )
            try {
                val app = getApplication<Application>()
                val allDocs = repository.allDocuments.first()
                val fileName = suggestExportFileName(format)

                val result = when (format) {
                    ExportFormat.ZIP -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val zipBytes = ReportExportManager.buildFullMassExportZipBytes(app, allDocs, pairs)
                        ReportExportManager.saveBytesToPhoneDownloads(app, fileName, format.mimeType, zipBytes)
                    }
                    ExportFormat.PDF -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                        ReportExportManager.saveBytesToPhoneDownloads(app, fileName, format.mimeType, pdfBytes)
                    }
                    ExportFormat.CSV -> {
                        val content = ReportExportManager.buildDocumentsCsvContent(allDocs)
                        ReportExportManager.saveTextToPhoneDownloads(app, fileName, format.mimeType, content)
                    }
                    ExportFormat.JSON -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val content = ReportExportManager.buildBatchJsonReportContent(pairs)
                        ReportExportManager.saveTextToPhoneDownloads(app, fileName, format.mimeType, content)
                    }
                    ExportFormat.TEXT -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val content = ReportExportManager.buildBatchTextSummaryContent(pairs)
                        ReportExportManager.saveTextToPhoneDownloads(app, fileName, format.mimeType, content)
                    }
                }

                if (result.isSuccess) {
                    val path = result.getOrNull() ?: "Downloads/NewazLandRecords/$fileName"
                    val msg = "✅ ফোন মেমোরিতে সংরক্ষিত হয়েছে: $path"
                    _uiState.value = _uiState.value.copy(isExporting = false, statusMessage = msg)
                    onComplete(true, msg)
                } else {
                    val err = "ডাউনলোড ব্যর্থ: ${result.exceptionOrNull()?.localizedMessage}"
                    _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = err)
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                val err = "ডাউনলোড ত্রুটি: ${e.localizedMessage}"
                _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = err)
                onComplete(false, err)
            }
        }
    }

    /**
     * Saves all land records (ZIP archive, PDF dossier, or CSV/JSON) to user-selected PC Local Disk / SAF URI.
     */
    fun saveAllToSafUri(
        uri: Uri,
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                errorMessage = null,
                statusMessage = "পিসি লোকাল ডিস্ক / ফোল্ডারে সংরক্ষণ হচ্ছে..."
            )
            try {
                val app = getApplication<Application>()
                val allDocs = repository.allDocuments.first()

                val result = when (format) {
                    ExportFormat.ZIP -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val zipBytes = ReportExportManager.buildFullMassExportZipBytes(app, allDocs, pairs)
                        ReportExportManager.writeBytesToSafUri(app, uri, zipBytes)
                    }
                    ExportFormat.PDF -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val pdfBytes = ReportExportManager.buildBatchFormattedPdfBytes(pairs)
                        ReportExportManager.writeBytesToSafUri(app, uri, pdfBytes)
                    }
                    ExportFormat.CSV -> {
                        val content = ReportExportManager.buildDocumentsCsvContent(allDocs)
                        ReportExportManager.writeContentToSafUri(app, uri, content)
                    }
                    ExportFormat.JSON -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val content = ReportExportManager.buildBatchJsonReportContent(pairs)
                        ReportExportManager.writeContentToSafUri(app, uri, content)
                    }
                    ExportFormat.TEXT -> {
                        val pairs = allDocs.map { doc ->
                            Pair(doc, repository.parseDataJson(doc.extractedDataJson))
                        }
                        val content = ReportExportManager.buildBatchTextSummaryContent(pairs)
                        ReportExportManager.writeContentToSafUri(app, uri, content)
                    }
                }

                if (result.isSuccess) {
                    val msg = "✅ সফলভাবে পিসি / লোকাল ডিস্কে সংরক্ষিত হয়েছে (${format.extension.uppercase()})"
                    _uiState.value = _uiState.value.copy(isExporting = false, statusMessage = msg)
                    onComplete(true, msg)
                } else {
                    val err = "সংরক্ষণ ব্যর্থ: ${result.exceptionOrNull()?.localizedMessage}"
                    _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = err)
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                val err = "সংরক্ষণ ত্রুটি: ${e.localizedMessage}"
                _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = err)
                onComplete(false, err)
            }
        }
    }
}
