package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.LandRecordData
import com.example.data.model.LandRecordType
import com.example.domain.image.ImagePreprocessor
import com.example.domain.image.PdfPageRenderer
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
import java.io.FileOutputStream
import java.util.UUID

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
        // Cleanup local sandbox files
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
            // Preprocess & create evidence copies
            val prepResult = ImagePreprocessor.preprocess(
                inputImageFile = capturedFile,
                outputDir = docDir,
                pageIndex = pageIdx
            )

            // Run OCR
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

        // Parse land record fields from combined OCR
        val extractedData = BangladeshLandRecordParser.parse(
            rawOcrText = combinedOcrBuilder.toString(),
            filenameHint = title,
            pageIndex = 1
        )

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
            processedAt = System.currentTimeMillis(),
            status = "UNVERIFIED",
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
            userNotes = notes
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pages)

        documentId
    }

    /**
     * Process an imported SAF file (PDF or Image).
     */
    suspend fun processImportedFile(discovered: DiscoveredFile): String = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()

        if (discovered.isZeroByte) {
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
                status = "ZERO_BYTE",
                errorMessage = "সতর্কতা: ফাইলটির আকার শূন্য বাইট (0-byte file detected)",
                overallConfidence = 0f
            )
            docDao.insertDocument(zeroDoc)
            return@withContext documentId
        }

        // Copy original file safely to sandbox
        val localSandboxedFile = safFolderScanner.copyToSandbox(discovered.uri, discovered.name, documentId)
        val pagesDir = File(context.filesDir, "records/$documentId/pages")
        pagesDir.mkdirs()

        val pages = mutableListOf<DocumentPageEntity>()
        val combinedOcrBuilder = StringBuilder()

        if (discovered.format.equals("PDF", ignoreCase = true)) {
            // Render all PDF pages
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
        } else {
            // Single page Image (JPG/PNG/WEBP/TIFF)
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

        // Structured parsing
        val extractedData = BangladeshLandRecordParser.parse(
            rawOcrText = combinedOcrBuilder.toString(),
            filenameHint = discovered.name,
            pageIndex = 1
        )

        val docEntity = LandDocumentEntity(
            id = documentId,
            title = discovered.name.substringBeforeLast('.'),
            sourceFileName = discovered.name,
            sourceFilePath = discovered.uri.toString(),
            sourceCategory = discovered.parentFolder,
            classifiedType = extractedData.recordType.name,
            fileFormat = discovered.format,
            fileSizeBytes = discovered.sizeBytes,
            isZeroByte = false,
            pageCount = pages.size,
            processedAt = System.currentTimeMillis(),
            status = "UNVERIFIED",
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
            extractedDataJson = recordDataAdapter.toJson(extractedData)
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pages)

        documentId
    }

    /**
     * Purges legacy test-run or invalid records to guarantee 100% genuine evidence fidelity.
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
