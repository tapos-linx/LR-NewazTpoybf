package com.example.ui.screens.saf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DocumentSourceType
import com.example.data.repository.LandRecordRepository
import com.example.domain.drive.GoogleDriveManager
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import com.example.domain.ingestion.DesktopPairingSession
import com.example.domain.ingestion.DocumentImportManager
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class SafImportUiState(
    val isScanning: Boolean = false,
    val isDownloadingDrive: Boolean = false,
    val isProcessingBatch: Boolean = false,
    val isExporting: Boolean = false,
    val scanSummary: FolderScanSummary? = null,
    val currentProcessingIndex: Int = 0,
    val totalToProcess: Int = 0,
    val processingFileName: String = "",
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val processedCount: Int = 0,
    val lastSourceLabel: String = "",
    val desktopPairingSession: DesktopPairingSession? = null,
    val isSyncingDesktopCompanion: Boolean = false
)

class SafImportViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = SafFolderScanner(application)
    private val repository = LandRecordRepository(application)
    private val importManager = DocumentImportManager(application)

    private val _uiState = MutableStateFlow(SafImportUiState())
    val uiState: StateFlow<SafImportUiState> = _uiState.asStateFlow()

    private fun mergeSummaries(existing: FolderScanSummary?, incoming: FolderScanSummary): FolderScanSummary {
        if (existing == null) return incoming
        val existingHashes = existing.supportedFiles.mapNotNull { it.sha256Hash.ifBlank { null } }.toSet()
        val existingUris = existing.supportedFiles.map { it.uri }.toSet()

        val newSupported = incoming.supportedFiles.map { file ->
            if ((file.sha256Hash.isNotBlank() && file.sha256Hash in existingHashes) || file.uri in existingUris) {
                file.copy(isDuplicate = true)
            } else {
                file
            }
        }
        val combinedSupported = existing.supportedFiles + newSupported
        val combinedZero = existing.zeroByteFiles + incoming.zeroByteFiles
        val combinedDuplicates = existing.duplicateFiles + newSupported.filter { it.isDuplicate }
        val combinedCorrupt = existing.corruptOrRejectedFiles + incoming.corruptOrRejectedFiles
        val combinedCategories = combinedSupported.groupBy { it.parentFolder }.mapValues { it.value.size }

        return FolderScanSummary(
            rootUri = incoming.rootUri,
            totalFilesFound = existing.totalFilesFound + incoming.totalFilesFound,
            supportedFiles = combinedSupported,
            zeroByteFiles = combinedZero,
            duplicateFiles = combinedDuplicates,
            corruptOrRejectedFiles = combinedCorrupt,
            ignoredFilesCount = existing.ignoredFilesCount + incoming.ignoredFilesCount,
            categoriesFound = combinedCategories
        )
    }

    fun clearScanSummary() {
        _uiState.value = _uiState.value.copy(
            scanSummary = null,
            errorMessage = null,
            statusMessage = null,
            processedCount = 0
        )
    }

    fun scanFolderTree(
        treeUri: Uri,
        sourceLabel: String = "লোকাল ডিস্ক / ফোল্ডার (SAF)",
        onScanned: ((List<DiscoveredFile>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                lastSourceLabel = sourceLabel,
                statusMessage = "$sourceLabel স্ক্যান হচ্ছে..."
            )

            try {
                val summary = importManager.importFromFolderTree(
                    treeUri = treeUri,
                    sourceType = if (sourceLabel.contains("ড্রাইভ")) DocumentSourceType.GOOGLE_DRIVE else DocumentSourceType.ANDROID_SAF_FOLDER
                )
                val merged = mergeSummaries(_uiState.value.scanSummary, summary)
                val validNewFiles = summary.supportedFiles.filter { !it.isDuplicate }
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = merged,
                    statusMessage = "$sourceLabel স্ক্যান সম্পন্ন: ${summary.supportedFiles.size} টি সমর্থিত নথি পাওয়া গেছে (মোট প্রস্তুত: ${merged.supportedFiles.count { !it.isDuplicate }})"
                )
                onScanned?.invoke(validNewFiles)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "ফোল্ডার স্ক্যান করতে সমস্যা: ${e.localizedMessage}"
                )
            }
        }
    }

    fun scanMultipleFiles(
        uris: List<Uri>,
        sourceCategory: String = "পিসি_ও_ফোন_বাল্ক_আপলোড",
        onScanned: ((List<DiscoveredFile>) -> Unit)? = null
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                lastSourceLabel = sourceCategory,
                statusMessage = "$sourceCategory থেকে ${uris.size} টি আইটেম স্ক্যান ও আনপ্যাক হচ্ছে..."
            )

            try {
                val summary = scanner.scanMultipleUris(uris, defaultCategory = sourceCategory)
                importManager.importRepository.enqueueDiscoveredFiles(
                    files = summary.supportedFiles,
                    sourceType = when {
                        sourceCategory.contains("ড্রাইভ") -> DocumentSourceType.GOOGLE_DRIVE
                        sourceCategory.contains("জিপ") -> DocumentSourceType.LOCAL_ZIP_ARCHIVE
                        else -> DocumentSourceType.ANDROID_SAF_FILE
                    }
                )
                val merged = mergeSummaries(_uiState.value.scanSummary, summary)
                val validNewFiles = summary.supportedFiles.filter { !it.isDuplicate }
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = merged,
                    statusMessage = "ফাইল ও জিপ যাচাই সম্পন্ন: নতুন ${validNewFiles.size} টি নথি যুক্ত হয়েছে (মোট প্রস্তুত: ${merged.supportedFiles.count { !it.isDuplicate }})"
                )
                onScanned?.invoke(validNewFiles)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "ফাইল স্ক্যান করতে সমস্যা: ${e.localizedMessage}"
                )
            }
        }
    }

    fun injectFromDriveUrl(
        urlOrId: String,
        onScanned: ((List<DiscoveredFile>) -> Unit)? = null
    ) {
        val driveId = GoogleDriveManager.extractDriveId(urlOrId)
        if (driveId == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "সঠিক গুগল ড্রাইভ লিঙ্ক বা ফাইল আইডি দিন (যেমন: https://drive.google.com/file/d/...)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingDrive = true,
                errorMessage = null,
                lastSourceLabel = "গুগল ড্রাইভ লিঙ্ক ($driveId)",
                statusMessage = "গুগল ড্রাইভ (${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL}) থেকে ডাউনলোড হচ্ছে..."
            )
            try {
                val destFile = File(
                    getApplication<Application>().cacheDir,
                    "drive_bulk_${UUID.randomUUID()}.bin"
                )
                GoogleDriveManager.downloadDriveFile(driveId, destFile) { bytes ->
                    val mb = bytes / (1024f * 1024f)
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "গুগল ড্রাইভ থেকে ডাউনলোড হচ্ছে: ${"%.1f".format(mb)} MB"
                    )
                }

                val isZip = try {
                    destFile.inputStream().use { ins ->
                        val header = ByteArray(4)
                        val read = ins.read(header)
                        read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                    }
                } catch (_: Exception) {
                    false
                }

                val finalFile = if (isZip) {
                    val zipFile = File(destFile.parentFile, "${destFile.nameWithoutExtension}.zip")
                    destFile.renameTo(zipFile)
                    zipFile
                } else {
                    val pdfOrImgFile = File(destFile.parentFile, "${destFile.nameWithoutExtension}.pdf")
                    destFile.renameTo(pdfOrImgFile)
                    pdfOrImgFile
                }

                _uiState.value = _uiState.value.copy(
                    isDownloadingDrive = false,
                    isScanning = true,
                    statusMessage = "ডাউনলোড সম্পন্ন! ফাইল ও জিপ ফোল্ডার বিশ্লেষণ চলছে..."
                )

                val summary = scanner.scanMultipleUris(
                    listOf(Uri.fromFile(finalFile)),
                    defaultCategory = "গুগল_ড্রাইভ_লিঙ্ক"
                )
                importManager.importRepository.enqueueDiscoveredFiles(
                    files = summary.supportedFiles,
                    sourceType = DocumentSourceType.GOOGLE_DRIVE
                )
                val merged = mergeSummaries(_uiState.value.scanSummary, summary)
                val validNewFiles = summary.supportedFiles.filter { !it.isDuplicate }

                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = merged,
                    statusMessage = "গুগল ড্রাইভ লিঙ্ক থেকে ${validNewFiles.size} টি নথি বাল্ক তালিকায় যুক্ত হয়েছে"
                )
                onScanned?.invoke(validNewFiles)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloadingDrive = false,
                    isScanning = false,
                    errorMessage = "গুগল ড্রাইভ ডাউনলোড ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Connects to the FastAPI Desktop/Web Companion server to pair or pull uploaded computer files.
     */
    fun createDesktopPairing(backendBaseUrl: String, apiKey: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncingDesktopCompanion = true,
                errorMessage = null,
                statusMessage = "পিসি ওয়েব কম্প্যানিয়ন পেয়ারিং টোকেন তৈরি হচ্ছে..."
            )
            val res = importManager.desktopCompanionSource.createPairingSession(backendBaseUrl, apiKey)
            if (res.isSuccess) {
                val session = res.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSyncingDesktopCompanion = false,
                    desktopPairingSession = session,
                    statusMessage = "পেয়ারিং প্রস্তুত! কোড: ${session.pairingCode} (${session.companionUploadUrl})"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncingDesktopCompanion = false,
                    errorMessage = "পেয়ারিং ব্যর্থ: ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun syncDesktopCompanionFiles(
        backendBaseUrl: String,
        pairingToken: String,
        apiKey: String? = null,
        onSynced: ((List<DiscoveredFile>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncingDesktopCompanion = true,
                errorMessage = null,
                statusMessage = "পিসি ওয়েব কম্প্যানিয়ন থেকে আপলোডকৃত নথি যাচাই ও ডাউনলোড হচ্ছে..."
            )
            val res = importManager.syncFromDesktopCompanion(backendBaseUrl, pairingToken, apiKey)
            if (res.isSuccess) {
                val queuedItems = res.getOrThrow()
                val discovered = queuedItems.map { q ->
                    DiscoveredFile(
                        uri = Uri.parse(q.sourceUri),
                        name = q.originalFilename,
                        parentFolder = q.parentFolder,
                        mimeType = q.mimeType,
                        sizeBytes = q.fileSizeBytes,
                        isZeroByte = q.isZeroByte,
                        format = q.fileFormat,
                        classifiedType = com.example.data.model.LandRecordType.fromFolderOrName(q.originalFilename),
                        sha256Hash = q.sha256,
                        isDuplicate = q.isDuplicate
                    )
                }
                val incomingSummary = FolderScanSummary(
                    rootUri = discovered.firstOrNull()?.uri ?: Uri.EMPTY,
                    totalFilesFound = discovered.size,
                    supportedFiles = discovered,
                    zeroByteFiles = discovered.filter { it.isZeroByte },
                    duplicateFiles = discovered.filter { it.isDuplicate },
                    ignoredFilesCount = 0,
                    categoriesFound = mapOf(DocumentSourceType.DESKTOP_COMPANION to discovered.size)
                )
                val merged = mergeSummaries(_uiState.value.scanSummary, incomingSummary)
                _uiState.value = _uiState.value.copy(
                    isSyncingDesktopCompanion = false,
                    scanSummary = merged,
                    statusMessage = "পিসি কম্প্যানিয়ন থেকে ${discovered.size} টি আসল নথি যাচাইসহ যুক্ত হয়েছে"
                )
                onSynced?.invoke(discovered.filter { !it.isDuplicate })
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncingDesktopCompanion = false,
                    errorMessage = "পিসি কম্প্যানিয়ন সিঙ্ক ব্যর্থ: ${res.exceptionOrNull()?.localizedMessage}"
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
