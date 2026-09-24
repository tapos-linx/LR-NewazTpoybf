package com.example.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.ConfidenceLevel
import com.example.data.model.FieldWithConfidence
import com.example.data.model.LandRecordData
import com.example.data.model.OwnerRecord
import com.example.data.repository.LandRecordRepository
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DocumentDetailUiState(
    val document: LandDocumentEntity? = null,
    val pages: List<DocumentPageEntity> = emptyList(),
    val recordData: LandRecordData = LandRecordData(),
    val selectedPageIndex: Int = 0,
    val showProcessedImage: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val exportFile: File? = null,
    val exportFormat: String? = null,
    val showEditFieldDialog: Boolean = false,
    val editingFieldName: String = "",
    val editingFieldValue: String = "",
    val showExportDialog: Boolean = false,
    val exportSuccessMessage: String? = null
)

class DocumentDetailViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getDocumentById(documentId).collect { doc ->
                if (doc != null) {
                    val pages = repository.getPagesOnce(doc.id)
                    val data = repository.parseDataJson(doc.extractedDataJson)

                    _uiState.value = _uiState.value.copy(
                        document = doc,
                        pages = pages,
                        recordData = data,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectPage(index: Int) {
        _uiState.value = _uiState.value.copy(selectedPageIndex = index)
    }

    fun toggleProcessedImage(showProcessed: Boolean) {
        _uiState.value = _uiState.value.copy(showProcessedImage = showProcessed)
    }

    fun updateField(fieldName: String, newValue: String) {
        val currentData = _uiState.value.recordData
        val updatedData = when (fieldName) {
            "khatianNo" -> currentData.copy(khatianNo = currentData.khatianNo.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "dagNo" -> currentData.copy(dagNo = currentData.dagNo.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "formerDagNo" -> currentData.copy(formerDagNo = currentData.formerDagNo.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "halDagNo" -> currentData.copy(halDagNo = currentData.halDagNo.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "district" -> currentData.copy(district = currentData.district.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "upazilaThana" -> currentData.copy(upazilaThana = currentData.upazilaThana.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "mouza" -> currentData.copy(mouza = currentData.mouza.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "jlNo" -> currentData.copy(jlNo = currentData.jlNo.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "landClass" -> currentData.copy(landClass = currentData.landClass.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "areaDecimals" -> currentData.copy(areaDecimals = currentData.areaDecimals.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "areaAcres" -> currentData.copy(areaAcres = currentData.areaAcres.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            "annualRent" -> currentData.copy(annualRent = currentData.annualRent.copy(value = newValue, confidence = ConfidenceLevel.VERIFIED, userEdited = true))
            else -> currentData
        }

        saveUpdatedRecordData(updatedData)
    }

    fun addOrUpdateOwner(index: Int, name: String, father: String, share: String) {
        val currentOwners = _uiState.value.recordData.owners.toMutableList()
        val owner = OwnerRecord(
            serial = index + 1,
            name = name,
            fatherOrHusbandName = father,
            shareHissa = share,
            confidence = ConfidenceLevel.VERIFIED
        )
        if (index < currentOwners.size) {
            currentOwners[index] = owner
        } else {
            currentOwners.add(owner)
        }
        val updatedData = _uiState.value.recordData.copy(owners = currentOwners)
        saveUpdatedRecordData(updatedData)
    }

    fun markDocumentVerified() {
        val currentDoc = _uiState.value.document ?: return
        viewModelScope.launch {
            val updatedDoc = currentDoc.copy(status = "VERIFIED")
            repository.updateDocument(updatedDoc)
            _uiState.value = _uiState.value.copy(document = updatedDoc)
        }
    }

    private fun saveUpdatedRecordData(data: LandRecordData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val currentDoc = _uiState.value.document ?: return@launch
            val json = repository.serializeData(data)

            val updatedDoc = currentDoc.copy(
                primaryDistrict = data.district.value,
                primaryUpazila = data.upazilaThana.value,
                primaryMouza = data.mouza.value,
                primaryJlNo = data.jlNo.value,
                primaryKhatianNo = data.khatianNo.value,
                primaryDagNo = data.dagNo.value,
                primaryLandClass = data.landClass.value,
                primaryAreaDecimals = data.areaDecimals.value,
                ownersSummary = data.owners.joinToString(", ") { it.name },
                extractedDataJson = json
            )

            repository.updateDocument(updatedDoc)
            _uiState.value = _uiState.value.copy(
                document = updatedDoc,
                recordData = data,
                isSaving = false
            )
        }
    }

    fun setShowExportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showExportDialog = show)
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportSuccessMessage = null)
    }

    fun suggestFileName(format: ExportFormat): String {
        return ReportExportManager.suggestFileName(_uiState.value.document, format)
    }

    fun getReportContentPreview(format: ExportFormat): String {
        val doc = _uiState.value.document ?: return ""
        val pages = _uiState.value.pages
        val data = _uiState.value.recordData

        return when (format) {
            ExportFormat.JSON -> ReportExportManager.buildJsonReportContent(doc, pages, data)
            ExportFormat.TEXT -> ReportExportManager.buildTextSummaryContent(doc, data)
            ExportFormat.CSV -> ReportExportManager.buildDocumentsCsvContent(listOf(doc))
        }
    }

    /**
     * Saves the structured report directly to a local file location
     * selected by the user via the Storage Access Framework (SAF).
     */
    fun saveReportToSafUri(
        uri: Uri,
        format: ExportFormat,
        onComplete: (Boolean, String) -> Unit
    ) {
        val doc = _uiState.value.document ?: return
        val pages = _uiState.value.pages
        val data = _uiState.value.recordData

        viewModelScope.launch {
            val content = when (format) {
                ExportFormat.JSON -> ReportExportManager.buildJsonReportContent(doc, pages, data)
                ExportFormat.TEXT -> ReportExportManager.buildTextSummaryContent(doc, data)
                ExportFormat.CSV -> ReportExportManager.buildDocumentsCsvContent(listOf(doc))
            }

            val result = ReportExportManager.writeContentToSafUri(getApplication(), uri, content)
            if (result.isSuccess) {
                val msg = "রিপোর্ট সফলভাবে ডিভাইসে সংরক্ষিত হয়েছে (${format.extension.uppercase()})"
                _uiState.value = _uiState.value.copy(
                    exportSuccessMessage = msg,
                    showExportDialog = false
                )
                onComplete(true, msg)
            } else {
                val err = "সংরক্ষণ ব্যর্থ হয়েছে: ${result.exceptionOrNull()?.localizedMessage}"
                _uiState.value = _uiState.value.copy(exportSuccessMessage = err)
                onComplete(false, err)
            }
        }
    }

    fun exportJson(onSuccess: (File) -> Unit) {
        val doc = _uiState.value.document ?: return
        val pages = _uiState.value.pages
        val data = _uiState.value.recordData

        viewModelScope.launch {
            val file = ReportExportManager.generateJsonReport(getApplication(), doc, pages, data)
            _uiState.value = _uiState.value.copy(exportFile = file, exportFormat = "application/json")
            onSuccess(file)
        }
    }

    fun exportTextSummary(onSuccess: (File) -> Unit) {
        val doc = _uiState.value.document ?: return
        val data = _uiState.value.recordData

        viewModelScope.launch {
            val file = ReportExportManager.generateTextSummary(getApplication(), doc, data)
            _uiState.value = _uiState.value.copy(exportFile = file, exportFormat = "text/plain")
            onSuccess(file)
        }
    }

    fun exportCsv(onSuccess: (File) -> Unit) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val file = ReportExportManager.generateDocumentsCsv(getApplication(), listOf(doc))
            _uiState.value = _uiState.value.copy(exportFile = file, exportFormat = "text/csv")
            onSuccess(file)
        }
    }
}
