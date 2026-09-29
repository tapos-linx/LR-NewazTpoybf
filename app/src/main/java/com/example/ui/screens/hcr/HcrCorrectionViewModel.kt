package com.example.ui.screens.hcr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.hcr.BanglaHcrEngine
import com.example.domain.hcr.HcrContextCategory
import com.example.domain.hcr.HcrToken
import com.example.domain.hcr.HcrTranscriptionResult
import com.example.domain.hcr.MarginalNoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HcrScreenUiState(
    val currentResult: HcrTranscriptionResult = HcrTranscriptionResult(
        originalText = "মৃত আব্দুল বারিক পিতা- হাজী জমিরুদ্দিন শেখ [Unclear: ঠিকানা অস্পষ্ট]",
        confidence = 0.74f,
        requiresManualReview = true,
        contextCategory = HcrContextCategory.OWNER_NAME
    ),
    val editingText: String = "মৃত আব্দুল বারিক পিতা- হাজী জমিরুদ্দিন শেখ [Unclear: ঠিকানা অস্পষ্ট]",
    val editorNotes: String = "",
    val marginalNotes: List<MarginalNoteItem> = listOf(
        MarginalNoteItem(
            marginLocation = "বাম প্রান্ত (Left Margin)",
            text = "নামজারি কেস নং ৩৪০/১৯৯৮ অনুযায়ী জমা খারিজ মঞ্জুর",
            confidence = 0.88f,
            isUnclear = false
        ),
        MarginalNoteItem(
            marginLocation = "শীর্ষ সিলমোহর (Top Seal)",
            text = "সহকারী কমিশনার (ভূমি) এর কার্যালয় [Unclear: তারিখ অস্পষ্ট]",
            confidence = 0.62f,
            isUnclear = true
        )
    ),
    val showSuccessSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val selectedCategory: HcrContextCategory = HcrContextCategory.OWNER_NAME
)

class HcrCorrectionViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = BanglaHcrEngine()
    private val _uiState = MutableStateFlow(HcrScreenUiState())
    val uiState: StateFlow<HcrScreenUiState> = _uiState.asStateFlow()

    init {
        loadSampleCategory(HcrContextCategory.OWNER_NAME)
    }

    fun selectCategory(category: HcrContextCategory) {
        loadSampleCategory(category)
    }

    private fun loadSampleCategory(category: HcrContextCategory) {
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
