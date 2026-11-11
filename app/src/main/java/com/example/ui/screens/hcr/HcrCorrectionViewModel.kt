package com.example.ui.screens.hcr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.hcr.BanglaHcrEngine
import com.example.domain.hcr.HcrContextCategory
import com.example.domain.hcr.HcrTranscriptionResult
import com.example.domain.hcr.MarginalNoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HcrScreenUiState(
    val currentResult: HcrTranscriptionResult = HcrTranscriptionResult(
        originalText = "",
        confidence = 0.0f,
        requiresManualReview = true,
        contextCategory = HcrContextCategory.OWNER_NAME
    ),
    val editingText: String = "",
    val editorNotes: String = "",
    val marginalNotes: List<MarginalNoteItem> = emptyList(),
    val showSuccessSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val selectedCategory: HcrContextCategory = HcrContextCategory.OWNER_NAME
)

class HcrCorrectionViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = BanglaHcrEngine()
    private val _uiState = MutableStateFlow(HcrScreenUiState())
    val uiState: StateFlow<HcrScreenUiState> = _uiState.asStateFlow()

    init {
        loadCategoryContext(HcrContextCategory.OWNER_NAME)
    }

    fun selectCategory(category: HcrContextCategory) {
        loadCategoryContext(category)
    }

    private fun loadCategoryContext(category: HcrContextCategory) {
        val result = engine.processHandwrittenCrop(null, null, category)
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentResult = result,
                editingText = result.originalText,
                editorNotes = ""
            )
        }
    }

    fun updateEditingText(newText: String) {
        _uiState.update { it.copy(editingText = newText) }
    }

    fun updateEditorNotes(notes: String) {
        _uiState.update { it.copy(editorNotes = notes) }
    }

    fun insertForensicTag(tag: String) {
        val current = _uiState.value.editingText
        val updated = if (current.endsWith(" ") || current.isEmpty()) {
            "$current$tag "
        } else {
            "$current $tag "
        }
        _uiState.update { it.copy(editingText = updated) }
    }

    fun saveCorrection() {
        viewModelScope.launch {
            val state = _uiState.value
            val verified = engine.applyCorrection(
                original = state.currentResult,
                correctedText = state.editingText,
                editorNotes = state.editorNotes.ifBlank { "ফরেনসিক গবেষক দ্বারা হস্তলিপি যাচাই সম্পন্ন" }
            )
            _uiState.update {
                it.copy(
                    currentResult = verified,
                    showSuccessSnackbar = true,
                    snackbarMessage = "হস্তলিপি সংশোধন সফলভাবে সংরক্ষিত হয়েছে (Zero-Data-Loss)"
                )
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(showSuccessSnackbar = false) }
    }
}
