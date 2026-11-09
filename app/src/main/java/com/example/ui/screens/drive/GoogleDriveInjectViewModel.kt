package com.example.ui.screens.drive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LandRecordRepository
import com.example.domain.drive.GoogleDriveManager
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class DriveInjectType {
    NONE,
    MASTER_FOLDER,
    ZIP_ARCHIVE,
    FOLDER,
    FILES,
    SHARED_LINK
}

data class GoogleDriveInjectUiState(
    val isScanning: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressText: String = "",
    val scanSummary: FolderScanSummary? = null,
    val injectType: DriveInjectType = DriveInjectType.NONE,
    val sourceLabel: String = "",
    val isProcessingBatch: Boolean = false,
    val currentProcessingIndex: Int = 0,
    val totalToProcess: Int = 0,
    val processingFileName: String = "",
    val extractedCount: Int = 0,
    val error: String? = null
)

class GoogleDriveInjectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)
    private val scanner = SafFolderScanner(application)

    private val _uiState = MutableStateFlow(GoogleDriveInjectUiState())
    val uiState: StateFlow<GoogleDriveInjectUiState> = _uiState.asStateFlow()

    fun injectMasterFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                injectType = DriveInjectType.MASTER_FOLDER,
                sourceLabel = "গুগল ড্রাইভ মাস্টার ফোল্ডার (সকল সাব-ফোল্ডারসহ)",
                error = null
            )
            try {
                val summary = scanner.scanDirectory(treeUri)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "মাস্টার ফোল্ডার স্ক্যান করতে ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    fun injectZipArchive(zipUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                injectType = DriveInjectType.ZIP_ARCHIVE,
                sourceLabel = "গুগল ড্রাইভ জিপ ফোল্ডার (.zip Archive)",
                error = null
            )
            try {
                val summary = scanner.scanMultipleUris(listOf(zipUri), "গুগল_ড্রাইভ_জিপ_আর্কাইভ")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "জিপ ফোল্ডার আনপ্যাক করতে ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    fun injectFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                injectType = DriveInjectType.FOLDER,
                sourceLabel = "গুগল ড্রাইভ ফোল্ডার",
                error = null
            )
            try {
                val summary = scanner.scanDirectory(treeUri)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "ড্রাইভ ফোল্ডার স্ক্যান করতে ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    fun injectFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                injectType = DriveInjectType.FILES,
                sourceLabel = "গুগল ড্রাইভ ফাইলসমূহ (${uris.size} টি ফাইল)",
                error = null
            )
            try {
                val summary = scanner.scanMultipleUris(uris, "গুগল_ড্রাইভ_আমদানি")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "ড্রাইভ ফাইলসমূহ স্ক্যান করতে ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    fun injectFromDriveUrl(urlOrId: String) {
        val driveId = GoogleDriveManager.extractDriveId(urlOrId)
        if (driveId == null) {
            _uiState.value = _uiState.value.copy(error = "সঠিক গুগল ড্রাইভ লিঙ্ক বা আইডি দিন (যেমন: https://drive.google.com/file/d/...)")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgressText = "গুগল ড্রাইভ থেকে ফাইল ডাউনলোড শুরু হচ্ছে...",
                injectType = DriveInjectType.SHARED_LINK,
                sourceLabel = "গুগল ড্রাইভ শেয়ার্ড লিঙ্ক (ID: $driveId)",
                error = null
            )

            try {
                val destFile = File(
                    getApplication<Application>().cacheDir,
                    "drive_import_${UUID.randomUUID()}.bin"
                )

                GoogleDriveManager.downloadDriveFile(driveId, destFile) { bytes ->
                    val mb = bytes / (1024f * 1024f)
                    _uiState.value = _uiState.value.copy(
                        downloadProgressText = "ডাউনলোড হচ্ছে: ${"%.1f".format(mb)} MB"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    isScanning = true,
                    downloadProgressText = "ডাউনলোড সম্পন্ন! ফাইল বিশ্লেষণ ও আনপ্যাক চলছে..."
                )

                // Inspect downloaded file: if ZIP or PDF
                val fileUri = Uri.fromFile(destFile)
                val summary = scanner.scanMultipleUris(listOf(fileUri), "গুগল_ড্রাইভ_লিঙ্ক_আমদানি")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    isScanning = false,
                    error = "গুগল ড্রাইভ থেকে ডাউনলোড করতে ব্যর্থ: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun reset() {
        _uiState.value = GoogleDriveInjectUiState()
    }

    fun startBatchExtraction(onComplete: (Int) -> Unit) {
        val summary = _uiState.value.scanSummary ?: return
        val filesToProcess = summary.supportedFiles
        if (filesToProcess.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingBatch = true,
                currentProcessingIndex = 0,
                totalToProcess = filesToProcess.size,
                extractedCount = 0,
                error = null
            )

            var processedCount = 0

            for ((index, file) in filesToProcess.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    currentProcessingIndex = index + 1,
                    processingFileName = file.name
                )

                try {
                    repository.processImportedFile(file)
                    processedCount++
                    _uiState.value = _uiState.value.copy(extractedCount = processedCount)
                } catch (e: Exception) {
                    // Continue with next file
                }
            }

            _uiState.value = _uiState.value.copy(
                isProcessingBatch = false,
                processingFileName = "নিষ্কাশন সম্পন্ন!"
            )
            onComplete(processedCount)
        }
    }
}
