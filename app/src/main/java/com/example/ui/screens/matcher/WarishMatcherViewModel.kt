package com.example.ui.screens.matcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.InheritorRepository
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

data class WarishMatcherUiState(
    val majorWarishList: List<WarishCertificate> = emptyList(),
    val selectedCertificateIndex: Int = 0,
    val currentWarish: WarishCertificate? = null,
    val matchReport: WarishMatchReport? = null,
    val cadastralSheet: CadastralSheetData? = null,
    val selectedParcel: CadastralParcel? = null,
    val selectedTab: Int = 0, // 0: Matcher & Certificate, 1: Cadastral Map, 2: Forensic Audit
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val surveyRecordsCount: Int = 0
)

class WarishMatcherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)
    val inheritorRepository = InheritorRepository(application)
    private val inheritanceMatcher = InheritanceMatcher()
    private val mapDetector = CadastralMapDetector(surveyType = "CS")

    private val _uiState = MutableStateFlow(WarishMatcherUiState())
    val uiState: StateFlow<WarishMatcherUiState> = _uiState.asStateFlow()

    init {
        // Reactively observe Master Dataset changes from Room DB
        viewModelScope.launch {
            inheritorRepository.masterCertificatesFlow.collect { certs ->
                if (certs.isNotEmpty()) {
                    val currentIndex = _uiState.value.selectedCertificateIndex.coerceIn(certs.indices)
                    _uiState.value = _uiState.value.copy(
                        majorWarishList = certs,
                        selectedCertificateIndex = currentIndex,
                        currentWarish = certs[currentIndex]
                    )
                    runMatching()
                } else {
                    _uiState.value = _uiState.value.copy(
                        majorWarishList = emptyList(),
                        currentWarish = null,
                        matchReport = null,
                        cadastralSheet = null
                    )
                }
            }
        }
    }

    fun selectCertificateIndex(index: Int) {
        val list = _uiState.value.majorWarishList
        if (index in list.indices) {
            _uiState.value = _uiState.value.copy(
                selectedCertificateIndex = index,
                currentWarish = list[index]
            )
            runMatching()
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun selectParcel(parcel: CadastralParcel?) {
        _uiState.value = _uiState.value.copy(selectedParcel = parcel)
    }

    fun updateDeceasedName(name: String) {
        val current = _uiState.value.currentWarish ?: return
        val updated = current.copy(deceasedName = name)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetDags(dagsText: String) {
        val current = _uiState.value.currentWarish ?: return
        val dags = dagsText.split(",", " ", "।")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val updated = current.copy(targetDagNumbers = dags)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetMouza(mouza: String) {
        val current = _uiState.value.currentWarish ?: return
        val updated = current.copy(targetMouza = mouza)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun createNewCertificate(
        certificateNo: String,
        deceasedName: String,
        mouza: String,
        dagNo: String
    ) {
        val newCert = WarishCertificate(
            certificateNo = certificateNo.ifBlank { "নতুন-সনদ-${System.currentTimeMillis() % 10000}" },
            deceasedName = deceasedName,
            targetMouza = mouza,
            targetDagNumbers = listOfNotNull(dagNo.ifBlank { null }),
            notes = "ব্যবহারকারী কর্তৃক তৈরিকৃত ওয়ারিশ সনদ"
        )
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(newCert)
        }
    }

    fun addHeir(name: String, relation: String, share: Double, shareLabel: String) {
        val current = _uiState.value.currentWarish ?: return
        val nextSerial = com.example.data.model.BengaliNumberUtils.toBengaliDigits((current.heirs.size + 1).toLong())
        val newHeir = HeirRecord(
            serialNo = nextSerial,
            name = name,
            relationship = relation,
            shareHissa = share,
            shareFractionLabel = shareLabel,
            remarks = "জীবিত"
        )
        val currentHeirs = current.heirs.toMutableList()
        currentHeirs.add(newHeir)
        val updated = current.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun removeHeir(heirId: String) {
        val current = _uiState.value.currentWarish ?: return
        val currentHeirs = current.heirs.filter { it.id != heirId }
        val updated = current.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun updateHeirRecord(updatedHeir: HeirRecord) {
        val current = _uiState.value.currentWarish ?: return
        val currentHeirs = current.heirs.map {
            if (it.id == updatedHeir.id) updatedHeir else it
        }
        val updated = current.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun runMatching() {
        val currentWarish = _uiState.value.currentWarish ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val surveyDocs: List<LandDocumentEntity> = try {
                repository.allDocuments.first()
            } catch (_: Exception) {
                emptyList()
            }

            // 1. Run Inheritance Matcher
            val report = inheritanceMatcher.matchWarishToSurveys(currentWarish, surveyDocs)

            // 2. Generate Cadastral Sheet with Highlights on matched target Dags
            val sheetData = mapDetector.detectAndHighlightParcels(
                targetDags = currentWarish.targetDagNumbers,
                mouzaHint = currentWarish.targetMouza
            )

            _uiState.value = _uiState.value.copy(
                matchReport = report,
                cadastralSheet = sheetData,
                selectedParcel = sheetData.parcels.firstOrNull { it.isTargetMatched },
                isLoading = false,
                surveyRecordsCount = surveyDocs.size,
                statusMessage = "ওয়ারিশনামা (${currentWarish.certificateNo}) ও জরিপ খতিয়ান মিলকরণ সম্পন্ন (${report.matchedPlots.size} টি দাগ বিশ্লেষিত)"
            )
        }
    }
}
