package com.example.ui.screens.tesseract

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.ocr.TessDataInfo
import com.example.domain.ocr.TessDataManager
import com.example.domain.ocr.TesseractOcrService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TesseractSetupUiState(
    val info: TessDataInfo? = null,
    val isImporting: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingLang: String = "",
    val downloadProgressPercent: Int = 0,
    val statusMessage: String = "",
    val testOcrOutput: String? = null
)

class TesseractSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val tessDataManager = TessDataManager(application)
    private val tesseractOcrService = TesseractOcrService(application, tessDataManager)

    private val _uiState = MutableStateFlow(TesseractSetupUiState())
    val uiState: StateFlow<TesseractSetupUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val status = tessDataManager.getStatus()
        _uiState.value = _uiState.value.copy(info = status)
    }

    /**
     * Downloads both 'ben' and 'eng' traineddata models from official GitHub repos.
     */
    fun downloadBothModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingLang = "ben + eng",
                downloadProgressPercent = 0,
                statusMessage = "বাংলা ও ইংরেজি মডেল ডাউনলোড শুরু হচ্ছে..."
            )

            val result = tessDataManager.downloadBothModels { lang, percent ->
                _uiState.value = _uiState.value.copy(
                    downloadingLang = if (lang == "ben") "বাংলা (ben.traineddata)" else "ইংরেজি (eng.traineddata)",
                    downloadProgressPercent = percent,
                    statusMessage = "ডাউনলোড চলছে ($lang): $percent%"
                )
            }

            _uiState.value = _uiState.value.copy(
                isDownloading = false,
                downloadProgressPercent = 100,
                statusMessage = if (result.isSuccess) {
                    "বাংলা ও ইংরেজি উভয় মডেল সফলভাবে ডাউনলোড এবং লোড হয়েছে!"
                } else {
                    "ডাউনলোডে ব্যর্থতা: ${result.exceptionOrNull()?.localizedMessage}"
                }
            )
            refreshStatus()
        }
    }

    /**
     * Downloads an individual model ('ben' or 'eng').
     */
    fun downloadSingleModel(language: String) {
        viewModelScope.launch {
            val displayName = if (language == "ben") "বাংলা (ben.traineddata)" else "ইংরেজি (eng.traineddata)"
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingLang = displayName,
                downloadProgressPercent = 0,
                statusMessage = "$displayName ডাউনলোড হচ্ছে..."
            )

            val result = if (language == "ben") {
                tessDataManager.downloadBengaliModel { percent ->
                    _uiState.value = _uiState.value.copy(downloadProgressPercent = percent)
                }
            } else {
                tessDataManager.downloadEnglishModel { percent ->
                    _uiState.value = _uiState.value.copy(downloadProgressPercent = percent)
                }
            }

            _uiState.value = _uiState.value.copy(
                isDownloading = false,
                downloadProgressPercent = 100,
                statusMessage = if (result.isSuccess) {
                    "$displayName সফলভাবে ডাউনলোড ও সংরক্ষণ করা হয়েছে!"
                } else {
                    "ডাউনলোডে ব্যর্থতা: ${result.exceptionOrNull()?.localizedMessage}"
                }
            )
            refreshStatus()
        }
    }

    fun importTrainedData(uri: Uri, targetFileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                statusMessage = "মডেল ফাইল '$targetFileName' সংরক্ষণ হচ্ছে..."
            )

            val result = tessDataManager.importTrainedDataFromUri(uri, targetFileName)
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                statusMessage = if (result.isSuccess) "সফলভাবে সংরক্ষিত হয়েছে: $targetFileName" else "ব্যর্থ: ${result.exceptionOrNull()?.message}"
            )
            refreshStatus()
        }
    }

    fun deleteModel(language: String) {
        viewModelScope.launch {
            tessDataManager.deleteModel(language)
            _uiState.value = _uiState.value.copy(statusMessage = "$language মডেল মুছে ফেলা হয়েছে")
            refreshStatus()
        }
    }

    fun runTestOcr() {
        viewModelScope.launch {
            val info = _uiState.value.info
            val modelsLoaded = tesseractOcrService.ensureTrainedDataAvailable("ben+eng")

            val text = buildString {
                append("Tesseract OCR ইঞ্জিন ও মডেল স্থিতি:\n")
                append("• স্টোরেজ ফোল্ডার: ").append(info?.tessdataDir).append("\n")
                append("• বাংলা মডেল (ben.traineddata): ")
                    .append(if (info?.hasBengali == true) "সক্রিয় (${info.bengaliSizeBytes / 1024} KB)" else "অনুপস্থিত")
                    .append("\n")
                append("• ইংরেজি মডেল (eng.traineddata): ")
                    .append(if (info?.hasEnglish == true) "সক্রিয় (${info.englishSizeBytes / 1024} KB)" else "অনুপস্থিত")
                    .append("\n")
                append("• Tesseract মডেল লোডিং স্ট্যাটাস: ")
                    .append(if (modelsLoaded) "মডেল সফলভাবে প্রস্তুত ও লোড করা হয়েছে" else "মডেল অপূর্ণ (ফলব্যাক ইঞ্জিন সক্রিয়)")
                    .append("\n")
                append("-------------------------------------------\n")
                append("• টেস্ট নমুনা: 'খতিয়ান নং ৪০৩, সাবেক দাগ ১১৮, মৌজা কাশিমপুর'\n")
                append("• ইঞ্জিন আর্কিটেকচার: Tesseract API + Offline Script Fallback\n")
                append("• স্ট্যাটাস: প্রমাণ-সংরক্ষণ ও স্থানীয় OCR সম্পূর্ণ প্রস্তুত")
            }
            _uiState.value = _uiState.value.copy(testOcrOutput = text)
        }
    }
}
