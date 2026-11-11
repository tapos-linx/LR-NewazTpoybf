package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.DocumentProcessingState
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportAuditLogEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.LandRecordData
import com.example.domain.image.ImagePreprocessor
import com.example.domain.image.PdfPageRenderer
import com.example.domain.ingestion.DocumentHashService
import com.example.domain.ingestion.DocumentValidationService
import com.example.domain.ocr.OcrService
import com.example.domain.ocr.TessDataManager
import com.example.domain.ocr.TesseractOcrService
import com.example.domain.parser.BangladeshLandRecordParser
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.SafFolderScanner
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class LandRecordRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    val tessDataManager: TessDataManager = TessDataManager(context),
    val ocrService: OcrService = TesseractOcrService(context, tessDataManager),
    private val safFolderScanner: SafFolderScanner = SafFolderScanner(context)
) {
    private val docDao = database.landDocumentDao()
    private val pageDao = database.documentPageDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val recordDataAdapter = moshi.adapter(LandRecordData::class.java)

    val allDocuments: Flow<List<LandDocumentEntity>> = docDao.getAllDocuments()
    val verifiedCount: Flow<Int> = docDao.getVerifiedCount()
    val zeroByteCount: Flow<Int> = docDao.getZeroByteCount()

    fun getDocumentById(id: String): Flow<LandDocumentEntity?> = docDao.getDocumentById(id)

    fun getPagesForDocument(documentId: String): Flow<List<DocumentPageEntity>> =
        pageDao.getPagesForDocument(documentId)

    fun searchDocuments(query: String): Flow<List<LandDocumentEntity>> =
        docDao.searchDocuments(query)

    fun getDocumentsByCategory(category: String): Flow<List<LandDocumentEntity>> =
        docDao.getDocumentsByCategory(category)

    fun getDocumentsByType(type: String): Flow<List<LandDocumentEntity>> =
        docDao.getDocumentsByType(type)

    fun getDocumentsByOwner(ownerName: String): Flow<List<LandDocumentEntity>> =
        docDao.getDocumentsByOwner(ownerName)

    fun getDocumentsByDateCapturedRange(startDate: Long, endDate: Long): Flow<List<LandDocumentEntity>> =
        docDao.getDocumentsByDateCapturedRange(startDate, endDate)

    fun getDocumentsByTypeAndOwner(type: String, ownerName: String): Flow<List<LandDocumentEntity>> =
        docDao.getDocumentsByTypeAndOwner(type, ownerName)

    suspend fun insertDocument(doc: LandDocumentEntity) = docDao.insertDocument(doc)

    suspend fun insertPages(pages: List<DocumentPageEntity>) = pageDao.insertPages(pages)

    suspend fun getDocumentOnce(id: String): LandDocumentEntity? = docDao.getDocumentByIdOnce(id)

    suspend fun getPagesOnce(documentId: String): List<DocumentPageEntity> =
        pageDao.getPagesForDocumentOnce(documentId)

    suspend fun updateDocument(doc: LandDocumentEntity) = docDao.updateDocument(doc)

    suspend fun updatePage(page: DocumentPageEntity) = pageDao.updatePage(page)

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        docDao.deleteDocumentById(id)
        val dir = File(context.filesDir, "records/$id")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun parseDataJson(json: String?): LandRecordData {
        if (json.isNullOrBlank()) return LandRecordData()
        return try {
            recordDataAdapter.fromJson(json) ?: LandRecordData()
        } catch (_: Exception) {
            LandRecordData()
        }
    }

    fun serializeData(data: LandRecordData): String {
        return recordDataAdapter.toJson(data)
    }

    /**
     * Process a newly captured Camera document session.
     */
    suspend fun createAndProcessCameraDocument(
        title: String,
        capturedImageFiles: List<File>,
        notes: String? = null
    ): String = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val docDir = File(context.filesDir, "records/$documentId/pages")
        docDir.mkdirs()

        val pages = mutableListOf<DocumentPageEntity>()
        val combinedOcrBuilder = StringBuilder()

        for ((index, capturedFile) in capturedImageFiles.withIndex()) {
            val pageIdx = index + 1
            val prepResult = ImagePreprocessor.preprocess(
                inputImageFile = capturedFile,
                outputDir = docDir,
                pageIndex = pageIdx
            )

            val ocr = ocrService.recognize(prepResult.processedFile)
            combinedOcrBuilder.append(ocr.rawText).append("\n\n")

            val pageEntity = DocumentPageEntity(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                pageIndex = pageIdx,
                rawImagePath = prepResult.originalFile.absolutePath,
                processedImagePath = prepResult.processedFile.absolutePath,
                rotationDegrees = prepResult.rotationDegrees,
                width = prepResult.width,
                height = prepResult.height,
                rawOcrText = ocr.rawText,
                ocrLanguage = ocr.language,
                ocrConfidence = ocr.confidence,
                ocrDurationMs = ocr.durationMs
            )
            pages.add(pageEntity)
        }

        val extractedData = BangladeshLandRecordParser.parse(
            rawOcrText = combinedOcrBuilder.toString(),
            filenameHint = title,
            pageIndex = 1
        )

        val firstFileHash = capturedImageFiles.firstOrNull()?.let { DocumentHashService.computeSha256(it) }.orEmpty()
        val now = System.currentTimeMillis()

        val docEntity = LandDocumentEntity(
            id = documentId,
            title = title,
            sourceFileName = "$title.jpg",
            sourceFilePath = "camera_capture",
            sourceCategory = "camera_capture",
            classifiedType = extractedData.recordType.name,
            fileFormat = "JPG",
            fileSizeBytes = capturedImageFiles.sumOf { it.length() },
            isZeroByte = false,
            pageCount = pages.size,
            dateCaptured = now,
            createdAt = now,
            processedAt = now,
            status = DocumentProcessingState.UNVERIFIED,
            primaryDistrict = extractedData.district.value,
            primaryUpazila = extractedData.upazilaThana.value,
            primaryMouza = extractedData.mouza.value,
            primaryJlNo = extractedData.jlNo.value,
            primaryKhatianNo = extractedData.khatianNo.value,
            primaryDagNo = extractedData.dagNo.value,
            primaryLandClass = extractedData.landClass.value,
            primaryAreaDecimals = extractedData.areaDecimals.value,
            ownersSummary = extractedData.owners.joinToString(", ") { it.name },
            overallConfidence = if (pages.isNotEmpty()) pages.map { it.ocrConfidence }.average().toFloat() else 0f,
            extractedDataJson = recordDataAdapter.toJson(extractedData),
            userNotes = notes,
            storedFilename = pages.firstOrNull()?.rawImagePath.orEmpty(),
            mimeType = "image/jpeg",
            sourceType = DocumentSourceType.CAMERA_CAPTURE,
            sha256 = firstFileHash,
            importTimestamp = now,
            processingStatus = DocumentProcessingState.COMPLETED,
            verificationStatus = "UNVERIFIED"
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pages)
        docDao.insertAuditLog(
            ImportAuditLogEntity(
                documentId = documentId,
                eventType = "CAMERA_CAPTURE_COMPLETED",
                sourceType = DocumentSourceType.CAMERA_CAPTURE,
                sourceReference = "camera_capture",
                sha256 = firstFileHash,
                fileSizeBytes = docEntity.fileSizeBytes,
                details = "Captured ${pages.size} page(s) via CameraX"
            )
        )

        documentId
    }

    /**
     * Process an imported file (PDF, JPG/JPEG, PNG, TIFF, WEBP, CSV, XLSX, DOCX, TXT, JSON).
     * Enforces SHA-256 verification, duplicate detection, corruption checks, and lifecycle state tracking.
     */
    suspend fun processImportedFile(
        discovered: DiscoveredFile,
        sourceTypeOverride: String? = null,
        googleDriveFileId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val resolvedSourceType = sourceTypeOverride ?: inferSourceType(discovered.parentFolder, googleDriveFileId)

        if (discovered.isZeroByte || discovered.sizeBytes == 0L) {
            val zeroDoc = LandDocumentEntity(
                id = documentId,
                title = discovered.name,
                sourceFileName = discovered.name,
                sourceFilePath = discovered.uri.toString(),
                sourceCategory = discovered.parentFolder,
                classifiedType = discovered.classifiedType.name,
                fileFormat = discovered.format,
                fileSizeBytes = 0L,
                isZeroByte = true,
                pageCount = 0,
                createdAt = now,
                importTimestamp = now,
                status = DocumentProcessingState.ZERO_BYTE,
                processingStatus = DocumentProcessingState.FAILED,
                errorMessage = "সতর্কতা: ফাইলটির আকার শূন্য বাইট (0-byte file detected)",
                processingError = "ZERO_BYTE_EMPTY_FILE",
                mimeType = discovered.mimeType ?: "application/octet-stream",
                sourceType = resolvedSourceType,
                googleDriveFileId = googleDriveFileId,
                sha256 = "",
                verificationStatus = "UNVERIFIED"
            )
            docDao.insertDocument(zeroDoc)
            docDao.insertAuditLog(
                ImportAuditLogEntity(
                    documentId = documentId,
                    eventType = "ZERO_BYTE_REJECTED",
                    sourceType = resolvedSourceType,
                    sourceReference = discovered.uri.toString(),
                    sha256 = "",
                    fileSizeBytes = 0L,
                    details = "Rejected 0-byte empty file: ${discovered.name}"
                )
            )
            return@withContext documentId
        }

        // 1. Copy original file safely to private sandbox
        val localSandboxedFile = safFolderScanner.copyToSandbox(discovered.uri, discovered.name, documentId)
        val sha256 = discovered.sha256Hash.ifBlank { DocumentHashService.computeSha256(localSandboxedFile) }

        // 2. Validate file format, size, and header integrity
        val validation = DocumentValidationService.validateFile(
            file = localSandboxedFile,
            originalFileName = discovered.name,
            suppliedMimeType = discovered.mimeType
        )
        if (!validation.isValid) {
            val failedDoc = LandDocumentEntity(
                id = documentId,
                title = discovered.name.substringBeforeLast('.'),
                sourceFileName = discovered.name,
                sourceFilePath = discovered.uri.toString(),
                sourceCategory = discovered.parentFolder,
                classifiedType = discovered.classifiedType.name,
                fileFormat = validation.normalizedFormat,
                fileSizeBytes = localSandboxedFile.length(),
                isZeroByte = localSandboxedFile.length() == 0L,
                pageCount = 0,
                createdAt = now,
                importTimestamp = now,
                processedAt = now,
                status = validation.status,
                processingStatus = validation.status,
                errorMessage = validation.reason,
                processingError = validation.reason,
                storedFilename = localSandboxedFile.absolutePath,
                mimeType = validation.mimeType,
                sourceType = resolvedSourceType,
                googleDriveFileId = googleDriveFileId,
                sha256 = sha256,
                verificationStatus = "UNVERIFIED"
            )
            docDao.insertDocument(failedDoc)
            docDao.insertAuditLog(
                ImportAuditLogEntity(
                    documentId = documentId,
                    eventType = "VALIDATION_FAILED",
                    sourceType = resolvedSourceType,
                    sourceReference = discovered.uri.toString(),
                    sha256 = sha256,
                    fileSizeBytes = localSandboxedFile.length(),
                    details = validation.reason ?: "Validation failed"
                )
            )
            return@withContext documentId
        }

        // 3. Check if SHA-256 already exists in the Unified Document Library
        val existingByHash = if (sha256.isNotBlank()) docDao.findDocumentBySha256(sha256) else null
        val isDuplicateRecord = discovered.isDuplicate || existingByHash != null

        val pagesDir = File(context.filesDir, "records/$documentId/pages")
        pagesDir.mkdirs()

        val pages = mutableListOf<DocumentPageEntity>()
        val combinedOcrBuilder = StringBuilder()
        val fmtUpper = validation.normalizedFormat.uppercase()

        when {
            fmtUpper == "PDF" -> {
                val renderResult = PdfPageRenderer.renderPdfPages(context, localSandboxedFile, pagesDir)
                for ((index, rawPageFile) in renderResult.pageImageFiles.withIndex()) {
                    val pageIdx = index + 1
                    val prepResult = ImagePreprocessor.preprocess(rawPageFile, pagesDir, pageIdx)
                    val ocr = ocrService.recognize(prepResult.processedFile)
                    combinedOcrBuilder.append(ocr.rawText).append("\n\n")

                    pages.add(
                        DocumentPageEntity(
                            id = UUID.randomUUID().toString(),
                            documentId = documentId,
                            pageIndex = pageIdx,
                            rawImagePath = prepResult.originalFile.absolutePath,
                            processedImagePath = prepResult.processedFile.absolutePath,
                            rotationDegrees = prepResult.rotationDegrees,
                            width = prepResult.width,
                            height = prepResult.height,
                            rawOcrText = ocr.rawText,
                            ocrLanguage = ocr.language,
                            ocrConfidence = ocr.confidence,
                            ocrDurationMs = ocr.durationMs
                        )
                    )
                }
            }
            fmtUpper in setOf("CSV", "TXT", "JSON", "DOCX", "XLSX") -> {
                val extractedText = extractStructuredTextFromDocument(localSandboxedFile, fmtUpper)
                combinedOcrBuilder.append(extractedText)
                pages.add(
                    DocumentPageEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        pageIndex = 1,
                        rawImagePath = localSandboxedFile.absolutePath,
                        processedImagePath = localSandboxedFile.absolutePath,
                        rotationDegrees = 0,
                        width = 0,
                        height = 0,
                        rawOcrText = extractedText,
                        ocrLanguage = "ben+eng",
                        ocrConfidence = 0f,
                        ocrDurationMs = 1L
                    )
                )
            }
            else -> {
                // Single-page Image (JPG, JPEG, PNG, TIFF, TIF, WEBP)
                val prepResult = ImagePreprocessor.preprocess(localSandboxedFile, pagesDir, 1)
                val ocr = ocrService.recognize(prepResult.processedFile)
                combinedOcrBuilder.append(ocr.rawText).append("\n\n")

                pages.add(
                    DocumentPageEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        pageIndex = 1,
                        rawImagePath = prepResult.originalFile.absolutePath,
                        processedImagePath = prepResult.processedFile.absolutePath,
                        rotationDegrees = prepResult.rotationDegrees,
                        width = prepResult.width,
                        height = prepResult.height,
                        rawOcrText = ocr.rawText,
                        ocrLanguage = ocr.language,
                        ocrConfidence = ocr.confidence,
                        ocrDurationMs = ocr.durationMs
                    )
                )
            }
        }

        val extractedData = BangladeshLandRecordParser.parse(
            rawOcrText = combinedOcrBuilder.toString(),
            filenameHint = discovered.name,
            pageIndex = 1
        )

        val processingStatus = when {
            isDuplicateRecord -> DocumentProcessingState.DUPLICATE
            combinedOcrBuilder.isBlank() || extractedData.unclearFields.isNotEmpty() -> DocumentProcessingState.NEEDS_MANUAL_REVIEW
            else -> DocumentProcessingState.COMPLETED
        }

        val docEntity = LandDocumentEntity(
            id = documentId,
            title = discovered.name.substringBeforeLast('.'),
            sourceFileName = discovered.name,
            sourceFilePath = discovered.uri.toString(),
            sourceCategory = discovered.parentFolder,
            classifiedType = extractedData.recordType.name,
            fileFormat = fmtUpper,
            fileSizeBytes = localSandboxedFile.length(),
            isZeroByte = false,
            pageCount = pages.size.coerceAtLeast(1),
            dateCaptured = now,
            createdAt = now,
            processedAt = System.currentTimeMillis(),
            status = if (isDuplicateRecord) DocumentProcessingState.DUPLICATE else DocumentProcessingState.UNVERIFIED,
            primaryDistrict = extractedData.district.value,
            primaryUpazila = extractedData.upazilaThana.value,
            primaryMouza = extractedData.mouza.value,
            primaryJlNo = extractedData.jlNo.value,
            primaryKhatianNo = extractedData.khatianNo.value,
            primaryDagNo = extractedData.dagNo.value,
            primaryLandClass = extractedData.landClass.value,
            primaryAreaDecimals = extractedData.areaDecimals.value,
            ownersSummary = extractedData.owners.joinToString(", ") { it.name },
            overallConfidence = if (pages.isNotEmpty()) pages.map { it.ocrConfidence }.average().toFloat() else 0f,
            extractedDataJson = recordDataAdapter.toJson(extractedData),
            storedFilename = localSandboxedFile.absolutePath,
            mimeType = validation.mimeType,
            sourceType = resolvedSourceType,
            googleDriveFileId = googleDriveFileId,
            sha256 = sha256,
            importTimestamp = now,
            processingStatus = processingStatus,
            processingError = if (isDuplicateRecord) "DUPLICATE_SHA256: Matches existing document ${existingByHash?.sourceFileName ?: discovered.duplicateOriginalSource.orEmpty()}" else null,
            verificationStatus = "UNVERIFIED"
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pages)
        docDao.insertAuditLog(
            ImportAuditLogEntity(
                documentId = documentId,
                eventType = if (isDuplicateRecord) "DUPLICATE_IMPORTED_PRESERVED" else "IMPORT_COMPLETED",
                sourceType = resolvedSourceType,
                sourceReference = discovered.uri.toString(),
                sha256 = sha256,
                fileSizeBytes = localSandboxedFile.length(),
                details = "Imported ${discovered.name} ($fmtUpper, ${localSandboxedFile.length()} bytes, status=$processingStatus)"
            )
        )

        documentId
    }

    private fun inferSourceType(parentFolder: String, googleDriveFileId: String?): String {
        if (!googleDriveFileId.isNullOrBlank() || parentFolder.contains("ড্রাইভ") || parentFolder.lowercase().contains("drive")) {
            return DocumentSourceType.GOOGLE_DRIVE
        }
        if (parentFolder.contains("জিপ") || parentFolder.lowercase().contains("zip")) {
            return DocumentSourceType.LOCAL_ZIP_ARCHIVE
        }
        if (parentFolder.lowercase().contains("companion") || parentFolder.lowercase().contains("desktop")) {
            return DocumentSourceType.DESKTOP_COMPANION
        }
        if (parentFolder.contains("ফোল্ডার") || parentFolder.contains("/")) {
            return DocumentSourceType.ANDROID_SAF_FOLDER
        }
        return DocumentSourceType.ANDROID_SAF_FILE
    }

    private fun extractStructuredTextFromDocument(file: File, fmtUpper: String): String {
        return try {
            when (fmtUpper) {
                "CSV", "TXT", "JSON" -> file.readText(Charsets.UTF_8)
                "DOCX" -> extractXmlTextFromZipEntry(file, "word/document.xml")
                "XLSX" -> extractXmlTextFromZipEntry(file, "xl/sharedStrings.xml")
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractXmlTextFromZipEntry(file: File, entryPrefix: String): String {
        return try {
            ZipFile(file).use { zf ->
                val entries = zf.entries().toList().filter { !it.isDirectory && it.name.startsWith(entryPrefix.substringBefore('/')) && it.name.endsWith(".xml") }
                val sb = StringBuilder()
                val tagRegex = Regex("<[^>]+>")
                for (entry in entries) {
                    val xml = zf.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val stripped = xml.replace(tagRegex, " ").replace(Regex("\\s+"), " ").trim()
                    if (stripped.isNotBlank()) {
                        sb.append(stripped).append("\n")
                    }
                }
                sb.toString().trim()
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Purges legacy stale records if present.
     */
    suspend fun purgeLegacyStaleRecords() = withContext(Dispatchers.IO) {
        val allDocs = docDao.getAllDocumentsList()
        for (doc in allDocs) {
            if (doc.sourceCategory == "tituwhatsapp" || doc.sourceCategory == "inheritance" || doc.sourceFilePath.contains("input/")) {
                deleteDocument(doc.id)
            }
        }
    }

    suspend fun getAllDocumentsListDirect(): List<LandDocumentEntity> = withContext(Dispatchers.IO) {
        docDao.getAllDocumentsList()
    }
}
