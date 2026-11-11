package com.example.ui.screens.importcenter

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.LandRecordRepository
import com.example.domain.ingestion.AndroidSafImportSource
import com.example.domain.ingestion.DocumentImportRepository
import com.example.domain.ingestion.LocalFolderImportSource
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state representing active URI processing, copied app-private files, and Room metadata status.
 */
data class DocumentImportUiState(
    val isProcessing: Boolean = false,
    val lastProcessedUris: List<Uri> = emptyList(),
    val lastImportedDocuments: List<LandDocumentEntity> = emptyList(),
    val totalImportedCount: Int = 0,
    val duplicateSkippedCount: Int = 0,
    val zeroByteRejectedCount: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel that provides [ActivityResultContracts.OpenDocument] and
 * [ActivityResultContracts.OpenDocumentTree] contracts and processes chosen [Uri]s
 * into application-private storage while tracking their metadata in Room ([AppDatabase]).
 */
class DocumentImportViewModel(
    application: Application,
    private val database: AppDatabase = AppDatabase.getInstance(application),
    private val scanner: SafFolderScanner = SafFolderScanner(application),
    private val safImportSource: AndroidSafImportSource = AndroidSafImportSource(application, scanner),
    private val folderImportSource: LocalFolderImportSource = LocalFolderImportSource(application, scanner),
    private val landRecordRepository: LandRecordRepository = LandRecordRepository(
        context = application,
        database = database,
        safFolderScanner = scanner
    ),
    private val documentImportRepository: DocumentImportRepository = DocumentImportRepository(
        context = application,
        database = database,
        landRecordRepository = landRecordRepository
    )
) : AndroidViewModel(application) {

    /**
     * Standard [ActivityResultContracts.OpenDocument] contract for single local file selection.
     */
    val openDocumentContract = ActivityResultContracts.OpenDocument()

    /**
     * Standard [ActivityResultContracts.OpenMultipleDocuments] contract for multi-file selection.
     */
    val openMultipleDocumentsContract = ActivityResultContracts.OpenMultipleDocuments()

    /**
     * Standard [ActivityResultContracts.OpenDocumentTree] contract for recursive folder tree selection.
     */
    val openDocumentTreeContract = ActivityResultContracts.OpenDocumentTree()

    /**
     * Supported MIME types for [openDocumentContract] and [openMultipleDocumentsContract].
     */
    val supportedMimeTypes: Array<String> = AndroidSafImportSource.SUPPORTED_SAF_MIME_TYPES

    private val _uiState = MutableStateFlow(DocumentImportUiState())
    val uiState: StateFlow<DocumentImportUiState> = _uiState.asStateFlow()

    /**
     * Reactive stream of all imported documents tracked in Room (`land_documents`).
     */
    val importedDocuments: StateFlow<List<LandDocumentEntity>> =
        documentImportRepository.allDocuments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Reactive stream of all queue items tracked in Room (`import_queue_items`).
     */
    val importQueueItems: StateFlow<List<ImportQueueItemEntity>> =
        documentImportRepository.allQueueItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Callback handler for [ActivityResultContracts.OpenDocument].
     */
    fun onOpenDocumentResult(
        uri: Uri?,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        if (uri == null) return
        processDocumentUri(uri, onComplete)
    }

    /**
     * Callback handler for [ActivityResultContracts.OpenMultipleDocuments].
     */
    fun onOpenMultipleDocumentsResult(
        uris: List<Uri>,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        processDocumentUris(uris, onComplete)
    }

    /**
     * Callback handler for [ActivityResultContracts.OpenDocumentTree].
     */
    fun onOpenDocumentTreeResult(
        treeUri: Uri?,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        if (treeUri == null) return
        processDocumentTreeUri(treeUri, onComplete)
    }

    /**
     * Processes a single document [Uri] selected via [ActivityResultContracts.OpenDocument],
     * copying it into application-private storage and persisting its metadata in Room.
     */
    fun processDocumentUri(
        uri: Uri,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        processDocumentUris(listOf(uri), onComplete)
    }

    /**
     * Processes one or more document [Uri]s into application-private storage and tracks metadata in Room.
     */
    fun processDocumentUris(
        uris: List<Uri>,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    lastProcessedUris = uris,
                    errorMessage = null,
                    statusMessage = "লোকাল ফাইল অ্যাপ-প্রাইভেট স্টোরেজে কপি ও রুম ডেটাবেসে সংরক্ষণ করা হচ্ছে..."
                )
            }
            try {
                val persistedDocs = processDocumentUrisSuspend(uris)
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        lastImportedDocuments = persistedDocs,
                        totalImportedCount = state.totalImportedCount + persistedDocs.size,
                        statusMessage = "${persistedDocs.size} টি ফাইল অ্যাপ-প্রাইভেট স্টোরেজ ও রুম ডেটাবেসে সংরক্ষিত হয়েছে"
                    )
                }
                onComplete(persistedDocs)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.localizedMessage ?: "ফাইল প্রক্রিয়াকরণে ত্রুটি ঘটেছে"
                    )
                }
            }
        }
    }

    /**
     * Processes a directory tree [Uri] selected via [ActivityResultContracts.OpenDocumentTree],
     * recursively copying supported files into application-private storage and recording metadata in Room.
     */
    fun processDocumentTreeUri(
        treeUri: Uri,
        onComplete: (List<LandDocumentEntity>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    lastProcessedUris = listOf(treeUri),
                    errorMessage = null,
                    statusMessage = "ফোল্ডার স্ক্যান ও অ্যাপ-প্রাইভেট স্টোরেজে সংরক্ষণ করা হচ্ছে..."
                )
            }
            try {
                val persistedDocs = processDocumentTreeUriSuspend(treeUri)
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        lastImportedDocuments = persistedDocs,
                        totalImportedCount = state.totalImportedCount + persistedDocs.size,
                        statusMessage = "ফোল্ডার থেকে ${persistedDocs.size} টি নথি প্রাইভেট স্টোরেজ ও রুমে সংরক্ষিত হয়েছে"
                    )
                }
                onComplete(persistedDocs)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.localizedMessage ?: "ফোল্ডার প্রক্রিয়াকরণে ত্রুটি ঘটেছে"
                    )
                }
            }
        }
    }

    /**
     * Suspend implementation that copies a single [Uri] into app-private storage and stores metadata in Room.
     */
    suspend fun processDocumentUriSuspend(uri: Uri): List<LandDocumentEntity> =
        processDocumentUrisSuspend(listOf(uri))

    /**
     * Suspend implementation that copies chosen document [Uri]s into app-private storage,
     * computes SHA-256 digests, enqueues & processes items in Room (`import_queue_items` and `land_documents`).
     */
    suspend fun processDocumentUrisSuspend(uris: List<Uri>): List<LandDocumentEntity> =
        withContext(Dispatchers.IO) {
            val scanSummary = safImportSource.scanSelectedDocuments(
                uris = uris,
                sourceCategory = DocumentSourceType.ANDROID_SAF_FILE
            )
            _uiState.update { state ->
                state.copy(
                    duplicateSkippedCount = state.duplicateSkippedCount + scanSummary.duplicateFiles.size,
                    zeroByteRejectedCount = state.zeroByteRejectedCount + scanSummary.zeroByteFiles.size
                )
            }

            val queueItems = documentImportRepository.enqueueDiscoveredFiles(
                files = scanSummary.supportedFiles,
                sourceType = DocumentSourceType.ANDROID_SAF_FILE
            )

            val savedDocuments = mutableListOf<LandDocumentEntity>()
            for (queueItem in queueItems) {
                val processedItem = documentImportRepository.processQueueItem(queueItem.queueItemId)
                val docId = processedItem?.documentId
                if (!docId.isNullOrBlank()) {
                    landRecordRepository.getDocumentOnce(docId)?.let { savedDocuments.add(it) }
                }
            }
            savedDocuments
        }

    /**
     * Suspend implementation that recursively scans a folder tree [Uri], copies discovered files
     * into app-private storage, and tracks their metadata in Room.
     */
    suspend fun processDocumentTreeUriSuspend(treeUri: Uri): List<LandDocumentEntity> =
        withContext(Dispatchers.IO) {
            val scanSummary = folderImportSource.scanFolderTreeRecursively(treeUri)
            _uiState.update { state ->
                state.copy(
                    duplicateSkippedCount = state.duplicateSkippedCount + scanSummary.duplicateFiles.size,
                    zeroByteRejectedCount = state.zeroByteRejectedCount + scanSummary.zeroByteFiles.size
                )
            }

            val queueItems = documentImportRepository.enqueueDiscoveredFiles(
                files = scanSummary.supportedFiles,
                sourceType = DocumentSourceType.ANDROID_SAF_FOLDER
            )

            val savedDocuments = mutableListOf<LandDocumentEntity>()
            for (queueItem in queueItems) {
                val processedItem = documentImportRepository.processQueueItem(queueItem.queueItemId)
                val docId = processedItem?.documentId
                if (!docId.isNullOrBlank()) {
                    landRecordRepository.getDocumentOnce(docId)?.let { savedDocuments.add(it) }
                }
            }
            savedDocuments
        }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    class Factory(
        private val context: Context,
        private val database: AppDatabase = AppDatabase.getInstance(context)
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as Application
            return DocumentImportViewModel(
                application = app,
                database = database
            ) as T
        }
    }
}
