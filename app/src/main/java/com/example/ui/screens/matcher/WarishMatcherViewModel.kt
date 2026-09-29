package com.example.ui.screens.matcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.LandRecordRepository
import com.example.domain.gis.CadastralMapDetector
import com.example.domain.gis.CadastralParcel
import com.example.domain.gis.CadastralSheetData
import com.example.domain.matcher.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class WarishMatcherUiState(
    val currentWarish: WarishCertificate = createDefaultWarish(),
    val matchReport: WarishMatchReport? = null,
    val cadastralSheet: CadastralSheetData? = null,
    val selectedParcel: CadastralParcel? = null,
    val selectedTab: Int = 0, // 0: Matcher & Certificate, 1: Cadastral Map, 2: Forensic Audit
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val surveyRecordsCount: Int = 0
)

fun createDefaultWarish(): WarishCertificate {
    return WarishCertificate(
        id = UUID.randomUUID().toString(),
        certificateNo = "ওয়ারিশ-২০২৪/০৮২",
        deceasedName = "মরহুম হাজী আব্দুল করিম",
        fatherOrHusbandName = "মৃত মৌলভী ওসমান গণি",
        villageOrArea = "চর শুভাঢ্যা",
        unionOrWard = "শুভাঢ্যা ইউনিয়ন পরিষদ",
        upazila = "কেরানীগঞ্জ",
        district = "ঢাকা",
        dateOfDeath = "১৫ মার্চ ২০২৩",
        issuerDesignation = "চেয়ারম্যান, শুভাঢ্যা ইউনিয়ন পরিষদ",
        targetDagNumbers = listOf("১০২", "১০৫", "১০৯"),
        targetMouza = "শুভাঢ্যা",
        targetKhatianNumbers = listOf("৪০৩", "১২৫০"),
        notes = "সাফ-কবলা ও উত্তরাধিকার সূত্রে স্বত্বাধিকারী।",
        heirs = listOf(
            HeirRecord(
                name = "মোসাঃ রহিমা খাতুন",
                relationship = "স্ত্রী",
                shareHissa = 0.125, // ১/৮ অংশ (২ আনা)
                shareFractionLabel = "২ আনা (১/৮)",
                nidOrBirthCert = "১৯৬৫০১২৩৪৯৮৭"
            ),
            HeirRecord(
                name = "মোঃ তারেক করিম",
                relationship = "জ্যেষ্ঠ পুত্র",
                shareHissa = 0.350,
                shareFractionLabel = "৫ আনা ১২ গণ্ডা",
                nidOrBirthCert = "১৯৮৫০১২৩৪৯৮৮"
            ),
            HeirRecord(
                name = "মোঃ রাশেদ করিম",
                relationship = "কনিষ্ঠ পুত্র",
                shareHissa = 0.350,
                shareFractionLabel = "৫ আনা ১২ গণ্ডা",
                nidOrBirthCert = "১৯৯১০১২৩৪৯৮৯"
            ),
            HeirRecord(
                name = "মোসাঃ ফাতেমা আক্তার",
                relationship = "কন্যা",
                shareHissa = 0.175,
                shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                nidOrBirthCert = "১৯৯৫০১২৩৪৯৯০"
            )
        )
    )
}

class WarishMatcherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)
    private val inheritanceMatcher = InheritanceMatcher()
    private val mapDetector = CadastralMapDetector(surveyType = "CS")

    private val _uiState = MutableStateFlow(WarishMatcherUiState())
    val uiState: StateFlow<WarishMatcherUiState> = _uiState.asStateFlow()

    init {
        runMatching()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun selectParcel(parcel: CadastralParcel?) {
        _uiState.value = _uiState.value.copy(selectedParcel = parcel)
    }

    fun updateDeceasedName(name: String) {
        val updated = _uiState.value.currentWarish.copy(deceasedName = name)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetDags(dagsText: String) {
        val dags = dagsText.split(",", " ", "।")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val updated = _uiState.value.currentWarish.copy(targetDagNumbers = dags)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetMouza(mouza: String) {
        val updated = _uiState.value.currentWarish.copy(targetMouza = mouza)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun addHeir(name: String, relation: String, share: Double, shareLabel: String) {
        val newHeir = HeirRecord(
            name = name,
            relationship = relation,
            shareHissa = share,
            shareFractionLabel = shareLabel
        )
        val currentHeirs = _uiState.value.currentWarish.heirs.toMutableList()
        currentHeirs.add(newHeir)
        val updated = _uiState.value.currentWarish.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        runMatching()
    }

    fun removeHeir(heirId: String) {
        val currentHeirs = _uiState.value.currentWarish.heirs.filter { it.id != heirId }
        val updated = _uiState.value.currentWarish.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        runMatching()
    }

    fun resetToSample() {
        _uiState.value = _uiState.value.copy(currentWarish = createDefaultWarish())
        runMatching()
    }

    fun runMatching() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val surveyDocs: List<LandDocumentEntity> = try {
                repository.allDocuments.first()
            } catch (_: Exception) {
                emptyList()
            }

            val currentWarish = _uiState.value.currentWarish

            // 1. Run Inheritance Matcher
            val report = inheritanceMatcher.matchWarishToSurveys(currentWarish, surveyDocs)

            // 2. Generate Cadastral Sheet with Highlights on matched target Dags
            val sheetData = mapDetector.detectAndHighlightParcels(
                targetDags = currentWarish.targetDagNumbers,
                mouzaHint = currentWarish.targetMouza.ifBlank { "শুভাঢ্যা" }
            )

            _uiState.value = _uiState.value.copy(
                matchReport = report,
                cadastralSheet = sheetData,
                selectedParcel = sheetData.parcels.firstOrNull { it.isTargetMatched },
                isLoading = false,
                surveyRecordsCount = surveyDocs.size,
                statusMessage = "ওয়ারিশনামা ও জরিপ খতিয়ান মিলকরণ সম্পন্ন (${report.matchedPlots.size} টি দাগ বিশ্লেষিত)"
            )
        }
    }
}
