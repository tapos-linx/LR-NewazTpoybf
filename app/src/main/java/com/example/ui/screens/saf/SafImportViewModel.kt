package com.example.ui.screens.saf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LandRecordRepository
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SafImportUiState(
    val isScanning: Boolean = false,
    val isProcessingBatch: Boolean = false,
    val scanSummary: FolderScanSummary? = null,
    val currentProcessingIndex: Int = 0,
    val totalToProcess: Int = 0,
    val processingFileName: String = "",
    val errorMessage: String? = null,
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
                errorMessage = null
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
                    errorMessage = "ফোল্ডার স্ক্যান করতে সমস্যা: ${e.localizedMessage}"
                )
            }
        }
    }

    fun startBatchProcessing(onComplete: () -> Unit) {
        val summary = _uiState.value.scanSummary ?: return
        val files = summary.supportedFiles

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingBatch = true,
                totalToProcess = files.size,
                currentProcessingIndex = 0,
                processedCount = 0
            )

            for ((index, file) in files.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    currentProcessingIndex = index + 1,
                    processingFileName = file.name
                )

                try {
                    repository.processImportedFile(file)
                } catch (_: Exception) {
                    // Continue with remaining files even if one encounters an error
                }
            }

            _uiState.value = _uiState.value.copy(
                isProcessingBatch = false,
                processedCount = files.size
            )
            onComplete()
        }
    }
}
