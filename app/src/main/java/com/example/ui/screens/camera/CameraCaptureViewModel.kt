package com.example.ui.screens.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LandRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class CameraCaptureUiState(
    val documentTitle: String = "ক্যামেরা_নথি_${System.currentTimeMillis()}",
    val capturedFiles: List<File> = emptyList(),
    val isTorchOn: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val error: String? = null
)

class CameraCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)

    private val _uiState = MutableStateFlow(CameraCaptureUiState())
    val uiState: StateFlow<CameraCaptureUiState> = _uiState.asStateFlow()

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(documentTitle = title)
    }

    fun toggleTorch() {
        _uiState.value = _uiState.value.copy(isTorchOn = !_uiState.value.isTorchOn)
    }

    fun addCapturedPage(file: File) {
        val currentList = _uiState.value.capturedFiles.toMutableList()
        currentList.add(file)
        _uiState.value = _uiState.value.copy(capturedFiles = currentList)
    }

    fun deletePage(index: Int) {
        val currentList = _uiState.value.capturedFiles.toMutableList()
        if (index in currentList.indices) {
            val file = currentList.removeAt(index)
            file.delete()
            _uiState.value = _uiState.value.copy(capturedFiles = currentList)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.capturedFiles.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _uiState.value = _uiState.value.copy(capturedFiles = currentList)
        }
    }

    fun finishSession(onComplete: (String) -> Unit) {
        val pages = _uiState.value.capturedFiles
        if (pages.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "কমপক্ষে একটি পাতা ক্যামেরায় তুলুন")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "নথির পাতাগুলো প্রক্রিয়াকরণ ও OCR চলছে..."
            )

            try {
                val docId = repository.createAndProcessCameraDocument(
                    title = _uiState.value.documentTitle,
                    capturedImageFiles = pages
                )
                _uiState.value = _uiState.value.copy(isProcessing = false)
                onComplete(docId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.localizedMessage ?: "প্রক্রিয়াকরণে সমস্যা হয়েছে"
                )
            }
        }
    }
}
