package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.ConfidenceLevel
import com.example.data.model.FieldWithConfidence
import com.example.data.model.LandRecordData
import com.example.data.model.LandRecordType
import com.example.data.model.OwnerRecord
import com.example.domain.export.ExportFormat
import com.example.domain.export.ReportExportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportExportManagerTest {

    private lateinit var context: Context
    private lateinit var sampleDoc: LandDocumentEntity
    private lateinit var samplePages: List<DocumentPageEntity>
    private lateinit var sampleData: LandRecordData

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        sampleDoc = LandDocumentEntity(
            id = "doc-12345",
            title = "সি এস খতিয়ান ৪০৩",
            sourceFileName = "403 নং সি এস খতিয়ান.pdf",
            sourceFilePath = "input/CS/403 নং সি এস খতিয়ান.pdf",
            sourceCategory = "CS",
            classifiedType = "CS_KHATIAN",
            fileFormat = "PDF",
            fileSizeBytes = 245000L,
            isZeroByte = false,
            pageCount = 1,
            processedAt = System.currentTimeMillis(),
            status = "VERIFIED",
            primaryDistrict = "ঢাকা",
            primaryUpazila = "কেরানীগঞ্জ",
            primaryMouza = "শুভাঢ্যা",
            primaryJlNo = "৪৫",
            primaryKhatianNo = "৪০৩",
            primaryDagNo = "১২৫০",
            primaryLandClass = "নাল",
            primaryAreaDecimals = "৮৫",
            ownersSummary = "মোহাম্মদ আবদুর রহমান",
            overallConfidence = 94.2f,
            extractedDataJson = "{}"
        )

        samplePages = listOf(
            DocumentPageEntity(
                id = "page-1",
                documentId = "doc-12345",
                pageIndex = 1,
                rawImagePath = "/dummy/page1_raw.png",
                processedImagePath = "/dummy/page1_proc.png",
                rotationDegrees = 0,
                width = 1200,
                height = 1600,
                rawOcrText = "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার\nসি এস খতিয়ান ৪০৩\nমালিক: আবদুর রহমান",
                ocrLanguage = "ben+eng",
                ocrConfidence = 94.2f,
                ocrDurationMs = 380L
            )
        )

        sampleData = LandRecordData(
            recordType = LandRecordType.CS,
            khatianNo = FieldWithConfidence(value = "৪০৩", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "খতিয়ান", evidencePage = 1),
            dagNo = FieldWithConfidence(value = "১২৫০", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "দাগ", evidencePage = 1),
            district = FieldWithConfidence(value = "ঢাকা", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "জেলা", evidencePage = 1),
            upazilaThana = FieldWithConfidence(value = "কেরানীগঞ্জ", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "উপজেলা", evidencePage = 1),
            mouza = FieldWithConfidence(value = "শুভাঢ্যা", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "মৌজা", evidencePage = 1),
            jlNo = FieldWithConfidence(value = "৪৫", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "জেএল", evidencePage = 1),
            landClass = FieldWithConfidence(value = "নাল", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "শ্রেণি", evidencePage = 1),
            areaDecimals = FieldWithConfidence(value = "৮৫", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "শতক", evidencePage = 1),
            areaAcres = FieldWithConfidence(value = "০.৮৫", confidence = ConfidenceLevel.VERIFIED, rawOcrMatch = "একর", evidencePage = 1),
            owners = listOf(
                OwnerRecord(
                    serial = 1,
                    name = "মোহাম্মদ আবদুর রহমান",
                    fatherOrHusbandName = "মৌলভী আবদুল জব্বার",
                    shareHissa = "১৬ আনা",
                    confidence = ConfidenceLevel.VERIFIED
                )
            )
        )
    }

    @Test
    fun testBuildJsonReportContentContainsStructuredDataAndDisclaimer() {
        val json = ReportExportManager.buildJsonReportContent(sampleDoc, samplePages, sampleData)
        assertNotNull(json)
        assertTrue(json.contains("Newaz Land Record Extractor"))
        assertTrue(json.contains("LEGAL DISCLAIMER"))
        assertTrue(json.contains("৪০৩"))
        assertTrue(json.contains("১২৫০"))
        assertTrue(json.contains("মোহাম্মদ আবদুর রহমান"))
        assertTrue(json.contains("structuredRecord"))
        assertTrue(json.contains("documentMetadata"))
    }

    @Test
    fun testBuildTextSummaryContent() {
        val txt = ReportExportManager.buildTextSummaryContent(sampleDoc, sampleData)
        assertNotNull(txt)
        assertTrue(txt.contains("আইনগত সতর্কতা"))
        assertTrue(txt.contains("বাংলাদেশ ভূমি রেকর্ড রিপোর্ট"))
        assertTrue(txt.contains("খতিয়ান নং: ৪০৩"))
        assertTrue(txt.contains("দাগ নং: ১২৫০"))
        assertTrue(txt.contains("মোহাম্মদ আবদুর রহমান"))
        assertTrue(txt.contains("মৌলভী আবদুল জব্বার"))
    }

    @Test
    fun testBuildBatchReports() {
        val records = listOf(Pair(sampleDoc, sampleData))
        val batchJson = ReportExportManager.buildBatchJsonReportContent(records)
        assertTrue(batchJson.contains("totalRecords"))
        assertTrue(batchJson.contains("doc-12345"))

        val batchTxt = ReportExportManager.buildBatchTextSummaryContent(records)
        assertTrue(batchTxt.contains("মোট নথির সংখ্যা: 1"))
        assertTrue(batchTxt.contains("নথি #1: সি এস খতিয়ান ৪০৩"))

        val batchCsv = ReportExportManager.buildBatchCsvContent(records)
        assertTrue(batchCsv.contains("Document_ID,Title,Survey_Type"))
        assertTrue(batchCsv.contains("\"doc-12345\""))
    }

    @Test
    fun testSuggestFileName() {
        val jsonName = ReportExportManager.suggestFileName(sampleDoc, ExportFormat.JSON)
        assertTrue(jsonName.startsWith("khatian_৪০৩_report_"))
        assertTrue(jsonName.endsWith(".json"))

        val textName = ReportExportManager.suggestFileName(sampleDoc, ExportFormat.TEXT)
        assertTrue(textName.startsWith("khatian_৪০৩_report_"))
        assertTrue(textName.endsWith(".txt"))

        val csvName = ReportExportManager.suggestFileName(sampleDoc, ExportFormat.CSV)
        assertTrue(csvName.startsWith("khatian_৪০৩_metadata_"))
        assertTrue(csvName.endsWith(".csv"))

        val batchName = ReportExportManager.suggestFileName(null, ExportFormat.JSON)
        assertTrue(batchName.startsWith("land_records_summary_"))
        assertTrue(batchName.endsWith(".json"))

        val batchCsvName = ReportExportManager.suggestFileName(null, ExportFormat.CSV)
        assertTrue(batchCsvName.startsWith("land_records_metadata_"))
        assertTrue(batchCsvName.endsWith(".csv"))
    }

    @Test
    fun testBuildDocumentsCsvContentContainsMetadataAndBom() {
        val csv = ReportExportManager.buildDocumentsCsvContent(listOf(sampleDoc))
        assertNotNull(csv)
        // Verify UTF-8 BOM is present for Excel/Google Sheets Bengali compatibility
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Document_ID,Title,Survey_Type"))
        assertTrue(csv.contains("Khatian_No,Dag_No,Land_Class"))
        assertTrue(csv.contains("\"doc-12345\""))
        assertTrue(csv.contains("\"সি এস খতিয়ান ৪০৩\""))
        assertTrue(csv.contains("\"৪০৩\""))
        assertTrue(csv.contains("\"১২৫০\""))
        assertTrue(csv.contains("\"ঢাকা\""))
        assertTrue(csv.contains("\"কেরানীগঞ্জ\""))
        assertTrue(csv.contains("\"শুভাঢ্যা\""))
        assertTrue(csv.contains("\"মোহাম্মদ আবদুর রহমান\""))
    }

    @Test
    fun testGenerateDocumentsCsvFile() {
        runBlocking {
            val file = ReportExportManager.generateDocumentsCsv(context, listOf(sampleDoc))
            assertNotNull(file)
            assertTrue(file.exists())
            assertTrue(file.length() > 0)
            val text = file.readText(Charsets.UTF_8)
            assertTrue(text.contains("\"doc-12345\""))
            assertTrue(text.contains("\"৪০৩\""))
            file.delete()
        }
    }

    @Test
    fun testWriteContentToSafUri() {
        runBlocking {
            val testFile = File(context.cacheDir, "test_saf_out.json")
            testFile.createNewFile()
            val uri = Uri.fromFile(testFile)

            val content = ReportExportManager.buildJsonReportContent(sampleDoc, samplePages, sampleData)
            val result = ReportExportManager.writeContentToSafUri(context, uri, content)

            assertTrue(result.isSuccess)
            val writtenText = testFile.readText(Charsets.UTF_8)
            assertEquals(content, writtenText)

            testFile.delete()
        }
    }

    @Test
    fun testPdfExportIntegrityAndBengaliUnicode() {
        val pdfBytes = ReportExportManager.buildFormattedPdfBytes(sampleDoc, samplePages, sampleData)
        assertTrue("PDF must exceed minimum byte threshold", pdfBytes.size > 200)
        assertTrue("PDF must pass header and EOF integrity check", ReportExportManager.validatePdfIntegrity(pdfBytes))

        val pdfText = String(pdfBytes, Charsets.UTF_8)
        assertTrue("PDF must start with %PDF-1.", pdfText.startsWith("%PDF-1."))
        assertTrue("PDF must contain %%EOF trailer", pdfText.contains("%%EOF"))
        // Verify Bengali Unicode text is preserved inside the PDF stream
        assertTrue("PDF must preserve Bengali Khatian number ৪০৩", pdfText.contains("৪০৩"))
        assertTrue("PDF must preserve Bengali Owner name", pdfText.contains("মোহাম্মদ আবদুর রহমান"))
        // Verify UTF-16BE BOM hex string (<FEFF...>) is included for forensic Unicode extraction
        assertTrue("PDF must contain UTF-16BE hex string <FEFF", pdfText.contains("<FEFF"))
    }

    @Test
    fun testMassExportZipIncludesValidatedPdfsAndReportsMissingFiles() = runBlocking {
        val zipBytes = ReportExportManager.buildFullMassExportZipBytes(
            context = context,
            documents = listOf(sampleDoc),
            records = listOf(Pair(sampleDoc, sampleData))
        )
        assertTrue(zipBytes.isNotEmpty())

        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("ZIP must contain master PDF dossier", entries.containsKey("land_records_dossier.pdf"))
        assertTrue(
            "Master PDF dossier inside ZIP must be valid",
            ReportExportManager.validatePdfIntegrity(entries["land_records_dossier.pdf"]!!)
        )
        // Because sampleDoc has no local filesDir/records/doc-12345 directory, MISSING_SOURCE_FILES_REPORT.txt must be generated
        assertTrue(
            "ZIP must explicitly report missing source files instead of failing silently",
            entries.containsKey("MISSING_SOURCE_FILES_REPORT.txt")
        )
        val missingReport = String(entries["MISSING_SOURCE_FILES_REPORT.txt"]!!, Charsets.UTF_8)
        assertTrue(missingReport.contains("doc-12345"))
    }

    @Test
    fun testPdfIntegrityValidatorDirectly() {
        val validPdfBytes = ReportExportManager.buildFormattedPdfBytes(sampleDoc, samplePages, sampleData)
        val validationResult = com.example.domain.export.PdfIntegrityValidator.validate(validPdfBytes)

        assertTrue("Validation must pass for formatted dossier", validationResult.isValid)
        assertTrue("Header must be valid", validationResult.hasValidHeader)
        assertTrue("Trailer must be valid", validationResult.hasValidTrailer)
        assertTrue("Bengali font/encoding must be detected", validationResult.isBengaliFontOrEncodingEmbedded)
        assertTrue("Failure reasons must be empty", validationResult.failureReasons.isEmpty())

        // Test invalid PDF rejection
        val corruptedBytes = "Not a PDF document".toByteArray()
        val corruptResult = com.example.domain.export.PdfIntegrityValidator.validate(corruptedBytes)
        org.junit.Assert.assertFalse("Corrupted byte stream must fail validation", corruptResult.isValid)
        assertTrue("Failure reasons must describe the failure", corruptResult.failureReasons.isNotEmpty())
    }
}



