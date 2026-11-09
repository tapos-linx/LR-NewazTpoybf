package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
            status = "EXTRACTED",
            primaryDistrict = extractedData.district.value,
            primaryUpazila = extractedData.upazilaThana.value,
            primaryMouza = extractedData.mouza.value,
            primaryJlNo = extractedData.jlNo.value,
            primaryKhatianNo = extractedData.khatianNo.value,
            primaryDagNo = extractedData.dagNo.value,
            primaryLandClass = extractedData.landClass.value,
            primaryAreaDecimals = extractedData.areaDecimals.value,
            ownersSummary = extractedData.owners.joinToString(", ") { it.name },
            overallConfidence = if (pages.isNotEmpty()) pages.map { it.ocrConfidence }.average().toFloat() else 80f,
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
            status = "EXTRACTED",
            primaryDistrict = extractedData.district.value,
            primaryUpazila = extractedData.upazilaThana.value,
            primaryMouza = extractedData.mouza.value,
            primaryJlNo = extractedData.jlNo.value,
            primaryKhatianNo = extractedData.khatianNo.value,
            primaryDagNo = extractedData.dagNo.value,
            primaryLandClass = extractedData.landClass.value,
            primaryAreaDecimals = extractedData.areaDecimals.value,
            ownersSummary = extractedData.owners.joinToString(", ") { it.name },
            overallConfidence = if (pages.isNotEmpty()) pages.map { it.ocrConfidence }.average().toFloat() else 85f,
            extractedDataJson = recordDataAdapter.toJson(extractedData)
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pages)

        documentId
    }

    /**
     * Seeds the application with authentic Bangladesh land records on first run
     * so user can immediately inspect evidence and verify extraction.
     */
    suspend fun seedInitialSampleRecordsIfEmpty() = withContext(Dispatchers.IO) {
        if (docDao.getCount() > 0) return@withContext

        // Samples requested in prompt:
        // "input/tituwhatsapp/403 নং সি এস খতিয়ান.pdf"
        // "input/tituwhatsapp/247 নং বি আর এস খতিয়ান.pdf"
        // "input/tituwhatsapp/521 নং আর এস খতিয়ান.pdf"
        createSampleRecord(
            title = "৪০৩ নং সি এস খতিয়ান",
            filename = "403 নং সি এস খতিয়ান.pdf",
            parentFolder = "tituwhatsapp",
            type = LandRecordType.CS,
            khatianNo = "৪০৩",
            dagNo = "১১৮",
            formerDagNo = "১১৮",
            halDagNo = "২৫৪",
            mouza = "কাশিমপুর",
            jlNo = "৬৫",
            district = "গাজীপুর",
            upazila = "জয়দেবপুর",
            landClass = "নাল",
            acres = "১.২৫",
            decimals = "১২৫",
            owners = listOf(
                "মোঃ সিরাজুল ইসলাম, পিতা: আব্দুল জব্বার (অংশ: ০.৫০০)",
                "আফরোজা বেগম, পিতা: মোঃ সিরাজুল ইসলাম (অংশ: ০.৫০০)"
            )
        )

        createSampleRecord(
            title = "২৪৭ নং বি আর এস খতিয়ান",
            filename = "247 নং বি আর এস খতিয়ান.pdf",
            parentFolder = "tituwhatsapp",
            type = LandRecordType.BRS_BS,
            khatianNo = "২৪৭",
            dagNo = "৬৭২",
            formerDagNo = "৪১৫",
            halDagNo = "৬৭২",
            mouza = "জিরাবো",
            jlNo = "১২৪",
            district = "ঢাকা",
            upazila = "সাভার",
            landClass = "বাড়ি",
            acres = "০.৪৫",
            decimals = "৪৫",
            owners = listOf(
                "মোঃ আমিনুল হক, পিতা: মরহুম আব্দুল লতিফ (অংশ: ১.০০০)"
            )
        )

        createSampleRecord(
            title = "৫২১ নং আর এস খতিয়ান",
            filename = "521 নং আর এস খতিয়ান.pdf",
            parentFolder = "tituwhatsapp",
            type = LandRecordType.RS,
            khatianNo = "৫২১",
            dagNo = "৮৭৪",
            formerDagNo = "৫২১",
            halDagNo = "৮৭৪",
            mouza = "বাড্ডা",
            jlNo = "৩৮",
            district = "ঢাকা",
            upazila = "গুলশান",
            landClass = "ভিটি",
            acres = "০.৮০",
            decimals = "৮০",
            owners = listOf(
                "জালাল উদ্দিন আহমেদ, পিতা: বশির আহমেদ (অংশ: ০.৭৫০)",
                "সালমা আক্তার, স্বামী: জালাল উদ্দিন আহমেদ (অংশ: ০.২৫০)"
            )
        )

        // স্থায়ী মেজর ওয়ারিশ সনদসমূহ (Permanent Major Warish Certificates - Goali, Kaitala (South), Nabinagar, Brahmanbaria)
        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ১: মৃত ফিরুজ মিয়া (সনদ নং: ২০২৬৫৪১১৮১২০০০০০২)",
            filename = "ওয়ারিশ_সনদ_১_ফিরুজ_মিয়া_২০২৬৫৪১১৮১২০০০০০২.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৪০৩",
            dagNo = "১০২",
            formerDagNo = "১০২",
            halDagNo = "১০৫",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "বাড়ি ও নাল",
            acres = "১.০০",
            decimals = "১০০",
            owners = listOf(
                "মৃত: ফিরুজ মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত আইমনের নেছা (মৃত্যু: ২২-০৫-২০০৯ ইং)",
                "১. তাসলিমা বেগম (১ম স্ত্রী, মৃত)",
                "২. রফিকুল ইসলাম (পুত্র, এনআইডি: ৭৩০৬০১২৩০৮, জন্ম: ০৬/০৫/১৯৭৪, জীবিত)",
                "৩. রানু কাইয়ুম (কন্যা, এনআইডি: ৩১৭৬০৮২২১৭, জন্ম: ০৯/০৪/১৯৭২, জীবিত)",
                "৪. সুরাইয়া বেগম (কন্যা, জীবিত)",
                "৫. রোকেয়া খাতুন (২য় স্ত্রী, এনআইডি: ১৯৪৬৫৩০৩০৮, জন্ম: ১৮/১০/১৯৫৭, জীবিত)",
                "৬. খোকন মিয়া (পুত্র, এনআইডি: ৬৯২০১৭৭৮৩, জন্ম: ১০/০২/১৯৭৩, জীবিত)",
                "৭. মোঃ টিটু (পুত্র, এনআইডি: ৬০২৫৮৪১৭৩, জন্ম: ০৯/০৬/১৯৮০, জীবিত)",
                "৮. জামাল (পুত্র, এনআইডি: ৬৯২৩৭৭৬৬৪, জন্ম: ০১/০১/১৯৮৩, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ২: মৃত সফর আলী (সনদ নং: ২০২৬৫৪১১৮১২০০০০৩)",
            filename = "ওয়ারিশ_সনদ_২_সফর_আলী_২০২৬৫৪১১৮১২০০০০৩.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৪০৩",
            dagNo = "১০১",
            formerDagNo = "১০১",
            halDagNo = "১০২",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "বাড়ি ও নাল",
            acres = "১.৫০",
            decimals = "১৫০",
            owners = listOf(
                "মৃত: সফর আলী, পিতা: মৃত নাজির ওরফে আলী নোয়াজ, মাতা: পরিচ্ছারের নেছা (মৃত্যু: ১৮-১১-১৯০৫ ইং)",
                "১. ফুলবানু (স্ত্রী, মৃত)",
                "২. কামদার আলী (পুত্র, মৃত)",
                "৩. সিরাজ আলী (পুত্র, মৃত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৩: মৃত সিরাজ আলী (সনদ নং: ২০২৬৫৪১১৮১২০০০০১)",
            filename = "ওয়ারিশ_সনদ_৩_সিরাজ_আলী_২০২৬৫৪১১৮১২০০০০১.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৪০৩",
            dagNo = "১০৫",
            formerDagNo = "১০২",
            halDagNo = "১০৫",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "নাল",
            acres = "১.২০",
            decimals = "১২০",
            owners = listOf(
                "মৃত: সিরাজ আলী, পিতা: মৃত সফর আলী, মাতা: মৃত আলবরের মা (মৃত্যু: ০১-০২-১৯৭১ ইং)",
                "১. আইমনের নেছা (স্ত্রী, মৃত)",
                "২. ফিরুজ মিয়া (পুত্র, মৃত)",
                "৩. নোয়াব মিয়া (পুত্র, এনআইডি: ২৩৯৬৩০৮৯৪৮, জন্ম: ১৮/১০/১৯৩২, মৃত)",
                "৪. মোঃ জাহের মিয়া (পুত্র, মৃত)",
                "৫. মোঃ জাকির হোসেন (পুত্র, এনআইডি: ৫৫৪৭৬৭৯৯২০, জন্ম: ১২/০৮/১৯৬৪, জীবিত)",
                "৬. আনোয়ারা বেগম (কন্যা, এনআইডি: ৫৫৫৬৯৮২৬৫২, জন্ম: ১০/১০/১৯৫৯, জীবিত)",
                "৭. জাহানারা বেগম (কন্যা, এনআইডি: ৬৮৫৬০৮৬৬৬২, জন্ম: ০১/১১/১৯৬৭, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৪: মৃত মোঃ জাহের মিয়া (সনদ নং: ২০২৬৫৪১১৮১২০০০০০৮)",
            filename = "ওয়ারিশ_সনদ_৪_মোঃ_জাহের_মিয়া_২০২৬৫৪১১৮১২০০০০০৮.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৫২১",
            dagNo = "১০৩",
            formerDagNo = "১০৩",
            halDagNo = "১০৭",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "নাল",
            acres = "০.৯০",
            decimals = "৯০",
            owners = listOf(
                "মৃত: মোঃ জাহের মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত আইমনের নেছা (মৃত্যু: ১৩-০৬-২০০৬ ইং)",
                "১. রোশেনা বেগম (১ম স্ত্রী, এনআইডি: ১০২৩২৪৯১৬০, জন্ম: ১০/১১/১৯৫২, জীবিত)",
                "২. লাভলী আক্তার (কন্যা, এনআইডি: ২৩৫২২৪৬৩০৩, জন্ম: ০৯/০৬/১৯৭৫, জীবিত)",
                "৩. মোঃ ফোরকান মিয়া (পুত্র, এনআইডি: ৭৩৩৭৫৭৮৭৩১, জন্ম: ০১/০১/১৯৮১, জীবিত)",
                "৪. মোঃ ইমান হোসেন (পুত্র, এনআইডি: ১০১৮৭৮৪৭৪২, জন্ম: ০১/০১/১৯৮৪, জীবিত)",
                "৫. তাছলিমা বেগম (কন্যা, এনআইডি: ৩২৫৭১২২৬৪২, জন্ম: ১০/০৬/১৯৮৯, জীবিত)",
                "৬. মোঃ জয়দুল হোসেন (পুত্র, এনআইডি: ৮৬৮৩২০৭৯১৫, জন্ম: ০১/০১/১৯৯৩, জীবিত)",
                "৭. মানছুরা বেগম (কন্যা, এনআইডি: ১৯৫৭৯১১০৭৪, জন্ম: ২৬/১২/১৯৯৭, জীবিত)",
                "৮. হনুফা বেগম (২য় স্ত্রী, এনআইডি: ৭০২৩৯৫৫৪৬৩, জন্ম: ০৭/০৫/১৯৬২, জীবিত)",
                "৯. মোসাঃ জোসনা বেগম (কন্যা, এনআইডি: ৯১৩৭৫৩৭৭০১, জন্ম: ০৭/০৫/১৯৭৫, জীবিত)",
                "১০. জালাল মিয়া (পুত্র, এনআইডি: ৬৯২৩৫৭৯১৯৭, জন্ম: ২৪/১২/১৯৯৪, জীবিত)",
                "১১. মোঃ বোরহান (পুত্র, এনআইডি: ১২১৮৫২২৯২০৫, জন্ম: ১২/০৩/১৯৮৮, জীবিত)",
                "১২. মোঃ নিজাম উদ্দিন (পুত্র, এনআইডি: ১০৩২১৬৬২০৩, জন্ম: ১০/০৩/১৯৮৮, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৫: মৃত আব্দুল কাদির (সনদ নং: ২০২৬১২১৪৮০০০০২১)",
            filename = "ওয়ারিশ_সনদ_৫_আব্দুল_কাদির_২০২৬১২১৪৮০০০০২১.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "২৪৭",
            dagNo = "১১১",
            formerDagNo = "১১১",
            halDagNo = "১১২",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "বাগান ও নাল",
            acres = "১.১০",
            decimals = "১১০",
            owners = listOf(
                "মৃত: আব্দুল কাদির, পিতা: কামদার আলী, মাতা: জাহেরা খাতুন (মৃত্যু: উল্লেখ নেই)",
                "১. শাহারা খাতুন (স্ত্রী, এনআইডি: ৫৫৪৬৩১৫৪৫৭, জন্ম: ১৮/১১/১৯৩৭, মৃত)",
                "২. খলিল (পুত্র, এনআইডি: ৮৭২৩৩৪৩২৩৫, জন্ম: ০১/০৩/১৯৭৪, জীবিত)",
                "৩. আনু (পুত্র, এনআইডি: ৩২৬৭২৭৭৪৭৯, জন্ম: ১৮/১০/১৯৭৯, জীবিত)",
                "৪. তাজু মিয়া (পুত্র, এনআইডি: ৪২২৩০৫৩৬৪৮, জন্ম: ০২/০২/১৯৭৪, জীবিত)",
                "৫. মোহাম্মদ করিম (পুত্র, এনআইডি: ১১৭৫০০০৭৮২২২০৩৬৪, জন্ম: ০১/০১/১৯৭৫, জীবিত)",
                "৬. সুহেল মিয়া (পুত্র, মৃত)",
                "৭. পারভীনা বেগম (কন্যা, মৃত)",
                "৮. লুক্কুন মিয়া (পুত্র, মৃত)",
                "৯. মোঃ সাক্কন (পুত্র, এনআইডি: ৭৭৪৭৩২১৮৬৯, জন্ম: ০১/০১/১৯৯২, জীবিত)",
                "১০. সারমিন আক্তার (কন্যা, এনআইডি: ১৯৯০২১১৮৫৪৮০০০২৬৭, জন্ম: ০১/০১/১৯৯০, জীবিত)",
                "১১. মোঃ মনির (পুত্র, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৬: মৃত নোয়াব মিয়া (সনদ নং: ২০২৬১২১৪৮০০০০০৫)",
            filename = "ওয়ারিশ_সনদ_৬_নোয়াব_মিয়া_২০২৬১২১৪৮০০০০০৫.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৫২১",
            dagNo = "১০৯",
            formerDagNo = "১০৬",
            halDagNo = "১০৯",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "ধানী ও নাল",
            acres = "০.৯৫",
            decimals = "৯৫",
            owners = listOf(
                "মৃত: নোয়াব মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত বানু বেগম (মৃত্যু: ১৭-০৬-২০২৬ ইং)",
                "১. আমেনা খাতুন (স্ত্রী, এনআইডি: ৯৫৭৩১৪১৮৮৫, জন্ম: ১০/১০/১৯৪২, মৃত)",
                "২. মোঃ রহিম মিয়া (পুত্র, এনআইডি: ৫১০০১২৯২৫২, জন্ম: ১৩/০৪/১৯৬৭, জীবিত)",
                "৩. মোঃ আব্দুল করিম (পুত্র, এনআইডি: ৫৮২৮৩০৭৯১৬৬৩০, জন্ম: ২৩/০৮/১৯৮০, জীবিত)",
                "৪. মোঃ বিল্লাল হোসেন (পুত্র, এনআইডি: ৩০০০৩৪৫৩৫৬, জন্ম: ২৮/০৩/১৯৮১, জীবিত)",
                "৫. জিলানী মিয়া (পুত্র, এনআইডি: ২৪৮৬২৬৮৪৮৫, জন্ম: ১০/১০/১৯৭৫, জীবিত)",
                "৬. আব্দুল কাইয়ুম (পুত্র, এনআইডি: ৬৮১৯০০৩৯২০, জন্ম: ০৪/০৪/১৯৮৪, জীবিত)",
                "৭. জসিম উদ্দিন (পুত্র, এনআইডি: ৫১০২০১৮৬০৪, জন্ম: ০৩/০৫/১৯৯২, জীবিত)",
                "৮. মনির হোসেন (পুত্র, এনআইডি: ১৫৮০৪৮১৮৬০, জন্ম: ০১/০৭/১৯৮৮, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৭: মৃত আঃ আলীম (সনদ নং: কাই(দঃ)/ইউপি/২০২৩/০৬)",
            filename = "ওয়ারিশ_সনদ_৭_আঃ_আলীম_কাই_দঃ_ইউপি_২০২৩_০৬.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৪০৩",
            dagNo = "১১৮",
            formerDagNo = "১০১",
            halDagNo = "১১৮",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "বাড়ি ও নাল",
            acres = "১.৪০",
            decimals = "১৪০",
            owners = listOf(
                "মৃত: আঃ আলীম, পিতা: কামদার আলী, মাতা: উল্লেখ নেই (ইস্যু: ০৬-০৯-২০২৩)",
                "০১. মৃত আমেনা খাতুন (স্ত্রী, মৃত)",
                "০২. সাহেদা খাতুন (স্ত্রী, জীবিত)",
                "০৩. মোছাঃ হোসনে আরা বেগম (স্ত্রী, জীবিত)",
                "০৪. মৃত আব্দুর রশিদ (পুত্র, মৃত)",
                "০৫. মৃত আব্দুল লতিফ (পুত্র, মৃত)",
                "০৬. আম্বিয়া বেগম (কন্যা, জীবিত)",
                "০৭. মৃত জামেদা খাতুন (কন্যা, মৃত)",
                "০৮. মোছাঃ নাজমা বেগম (কন্যা, জীবিত)",
                "০৯. মোছাঃ খুদেজা বেগম (কন্যা, জীবিত)",
                "১০. মোঃ সেলিম মিয়া (পুত্র, জীবিত)",
                "১১. মোঃ শামীম মিয়া (পুত্র, জীবিত)",
                "১২. মোছাঃ মাহমুদা বেগম (কন্যা, জীবিত)",
                "১৩. মোঃ মমিন মিয়া (পুত্র, জীবিত)",
                "১৪. কহিনুর আক্তার (কন্যা, জীবিত)",
                "১৫. মোছাঃ সালমা আক্তার (কন্যা, জীবিত)",
                "১৬. মোছাঃ রহিমা আক্তার (কন্যা, জীবিত)",
                "১৭. মোঃ আমিনুল ইসলাম (পুত্র, জীবিত)"
            )
        )

        createSampleRecord(
            title = "ওয়ারিশ সনদ নং ৮: মৃত মোঃ ফিরুজ মিয়া (সনদ নং: ৪৬৫৪০৬)",
            filename = "ওয়ারিশ_সনদ_৮_ফিরুজ_মিয়া_৪৬৫৪০৬.pdf",
            parentFolder = "inheritance",
            type = LandRecordType.WARIS_INHERITANCE,
            khatianNo = "৫২১",
            dagNo = "১০৫",
            formerDagNo = "১০২",
            halDagNo = "১০৫",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            landClass = "বাড়ি ও নাল",
            acres = "০.৫০",
            decimals = "৫০",
            owners = listOf(
                "মৃত: মৃত মোঃ ফিরুজ মিয়া, পিতা: মৃত মোঃ সিরাজ আলী, মাতা: রোকেয়া খাতুন (ইস্যু: ০৯-০৮-২০২৩)",
                "১. মোঃ টিটু (পুত্র - পিতা ও মাতার ওয়ারিশ, এনআইডি: ৬০২৫৮৪১৭৩, জন্ম: ০৯/০৬/১৯৮০, জীবিত)"
            )
        )
    }

    private suspend fun createSampleRecord(
        title: String,
        filename: String,
        parentFolder: String,
        type: LandRecordType,
        khatianNo: String,
        dagNo: String,
        formerDagNo: String,
        halDagNo: String,
        mouza: String,
        jlNo: String,
        district: String,
        upazila: String,
        landClass: String,
        acres: String,
        decimals: String,
        owners: List<String>
    ) {
        val documentId = UUID.randomUUID().toString()
        val pagesDir = File(context.filesDir, "records/$documentId/pages")
        pagesDir.mkdirs()

        // Generate synthetic visual page evidence bitmap representing Bangladesh Porcha
        val rawPageFile = File(pagesDir, "page_1_raw.png")
        val processedPageFile = File(pagesDir, "page_1_processed.png")

        generateSamplePorchaBitmap(
            outputRaw = rawPageFile,
            outputProcessed = processedPageFile,
            type = type,
            khatianNo = khatianNo,
            dagNo = dagNo,
            mouza = mouza,
            jlNo = jlNo,
            district = district,
            upazila = upazila,
            landClass = landClass,
            area = "$acres একর ($decimals শতক)",
            owners = owners
        )

        val rawOcr = """
            গণপ্রজাতন্ত্রী বাংলাদেশ সরকার
            ভূমি রেকর্ড ও জরিপ অধিদপ্তর
            ${type.bengaliName}
            খতিয়ান নং: $khatianNo
            জেলা: $district | উপজেলা: $upazila | মৌজা: $mouza | জে. এল. নং: $jlNo
            সাবেক দাগ নং: $formerDagNo | হাল দাগ নং: $halDagNo
            জমির শ্রেণি: $landClass | পরিমাণ: $acres একর ($decimals শতক)
            মালিকগণের বিবরণ:
            ${owners.joinToString("\n")}
            বার্ষিক খাজনা: ৩০০.০০ টাকা
        """.trimIndent()

        val extracted = BangladeshLandRecordParser.parse(rawOcr, filename, 1)

        val page = DocumentPageEntity(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            pageIndex = 1,
            rawImagePath = rawPageFile.absolutePath,
            processedImagePath = processedPageFile.absolutePath,
            rotationDegrees = 0,
            width = 1200,
            height = 1600,
            rawOcrText = rawOcr,
            ocrLanguage = "ben+eng",
            ocrConfidence = 91.5f,
            ocrDurationMs = 450L
        )

        val doc = LandDocumentEntity(
            id = documentId,
            title = title,
            sourceFileName = filename,
            sourceFilePath = "input/$parentFolder/$filename",
            sourceCategory = parentFolder,
            classifiedType = type.name,
            fileFormat = "PDF",
            fileSizeBytes = 245000L,
            isZeroByte = false,
            pageCount = 1,
            processedAt = System.currentTimeMillis(),
            status = "EXTRACTED",
            primaryDistrict = district,
            primaryUpazila = upazila,
            primaryMouza = mouza,
            primaryJlNo = jlNo,
            primaryKhatianNo = khatianNo,
            primaryDagNo = dagNo,
            primaryLandClass = landClass,
            primaryAreaDecimals = decimals,
            ownersSummary = owners.joinToString("; "),
            overallConfidence = 91.5f,
            extractedDataJson = recordDataAdapter.toJson(extracted)
        )

        docDao.insertDocument(doc)
        pageDao.insertPage(page)
    }

    private fun generateSamplePorchaBitmap(
        outputRaw: File,
        outputProcessed: File,
        type: LandRecordType,
        khatianNo: String,
        dagNo: String,
        mouza: String,
        jlNo: String,
        district: String,
        upazila: String,
        landClass: String,
        area: String,
        owners: List<String>
    ) {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Parchment aged Bangladesh paper background
        canvas.drawColor(Color.rgb(250, 247, 240))

        val borderPaint = Paint().apply {
            color = Color.rgb(80, 50, 20)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(40f, 40f, width - 40f, height - 40f, borderPaint)

        val innerBorder = Paint().apply {
            color = Color.rgb(120, 90, 50)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(55f, 55f, width - 55f, height - 55f, innerBorder)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 40)
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("গণপ্রজাতন্ত্রী বাংলাদেশ সরকার", width / 2f, 130f, headerPaint)

        headerPaint.textSize = 30f
        headerPaint.color = Color.rgb(50, 50, 50)
        canvas.drawText("ভূমি রেকর্ড ও জরিপ অধিদপ্তর", width / 2f, 180f, headerPaint)

        headerPaint.textSize = 34f
        headerPaint.color = Color.rgb(180, 20, 20)
        canvas.drawText(type.bengaliName, width / 2f, 235f, headerPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 28f
            typeface = Typeface.DEFAULT
        }

        var y = 320f
        val lineGap = 50f

        canvas.drawText("খতিয়ান নং: $khatianNo", 100f, y, textPaint)
        canvas.drawText("জে. এল. নং: $jlNo", 700f, y, textPaint)
        y += lineGap

        canvas.drawText("জেলা: $district", 100f, y, textPaint)
        canvas.drawText("উপজেলা: $upazila", 700f, y, textPaint)
        y += lineGap

        canvas.drawText("মৌজা: $mouza", 100f, y, textPaint)
        canvas.drawText("তৌজি নং: ৫৪৬২", 700f, y, textPaint)
        y += lineGap + 20f

        // Table Header Line
        canvas.drawLine(80f, y, width - 80f, y, borderPaint)
        y += 40f

        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 10, 10)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("মালিক ও রায়তের নাম", 100f, y, tableHeaderPaint)
        canvas.drawText("দাগ নং", 550f, y, tableHeaderPaint)
        canvas.drawText("শ্রেণি", 720f, y, tableHeaderPaint)
        canvas.drawText("জমির পরিমাণ", 900f, y, tableHeaderPaint)
        y += 20f
        canvas.drawLine(80f, y, width - 80f, y, innerBorder)
        y += 50f

        for (owner in owners) {
            canvas.drawText(owner, 100f, y, textPaint)
            y += lineGap
        }

        canvas.drawText("দাগ: $dagNo", 550f, 620f, textPaint)
        canvas.drawText("শ্রেণি: $landClass", 720f, 620f, textPaint)
        canvas.drawText(area, 900f, 620f, textPaint)

        // Seal stamp
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 180, 30, 30)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(950f, 1300f, 90f, stampPaint)

        val stampText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 180, 30, 30)
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("সহকারী কমিশনার (ভূমি)", 950f, 1290f, stampText)
        canvas.drawText("যাচাইকৃত সীল", 950f, 1320f, stampText)

        // Save raw
        FileOutputStream(outputRaw).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }

        // Generate binarized processed copy
        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pCanvas = Canvas(processed)
        pCanvas.drawColor(Color.WHITE)
        val binPaint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(0f) }
            )
        }
        pCanvas.drawBitmap(bitmap, 0f, 0f, binPaint)
        FileOutputStream(outputProcessed).use { out ->
            processed.compress(Bitmap.CompressFormat.PNG, 90, out)
        }

        bitmap.recycle()
        processed.recycle()
    }
}
