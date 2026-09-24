package com.example.domain.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.model.LandRecordData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    JSON("json", "application/json", "স্ট্রাকচার্ড JSON ফাইল (.json)"),
    TEXT("txt", "text/plain", "টেক্সট সারসংক্ষেপ রিপোর্ট (.txt)"),
    CSV("csv", "text/csv", "স্প্রেডশিট CSV ফাইল (.csv)")
}

object ReportExportManager {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    const val LEGAL_DISCLAIMER_TEXT = """
================================================================================
⚠️ আইনগত সতর্কতা ও নির্দেশিকা (LEGAL DISCLAIMER)
================================================================================
১. এই অ্যাপ্লিকেশনে প্রদর্শিত অপটিক্যাল ক্যারেক্টার রিকগনিশন (OCR) ফলাফল আনুমানিক।
২. আহরিত সকল তথ্য অবশ্যই মূল দলিলের সাথে অক্ষরে অক্ষরে মিলিয়ে যাচাই করতে হবে।
৩. এই অ্যাপ্লিকেশনটি কোনোভাবেই আইনি মালিকানা (Legal Ownership) নির্ধারণ করে না।
৪. এই ফলাফল কোনো আইনি মতামত (Legal Opinion) প্রদান করে না।
৫. এটি কোনো উত্তরাধিকার (Inheritance) বা ভূমির চূড়ান্ত স্বত্ব (Title) প্রমাণ করে না।
৬. সকল অনিশ্চিত বা সম্ভাব্য ক্ষেত্রসমূহ 'অযাচাইকৃত' (Unverified/Probable) হিসেবে চিহ্নিত।
================================================================================
    """

    /**
     * Builds structured JSON string containing full document metadata,
     * page evidence, OCR confidence scores, and land record entities.
     */
    fun buildJsonReportContent(
        doc: LandDocumentEntity,
        pages: List<DocumentPageEntity>,
        data: LandRecordData
    ): String {
        val payload = mapOf(
            "app" to "Newaz Land Record Extractor",
            "version" to "1.0",
            "exportTimestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            "legalDisclaimer" to LEGAL_DISCLAIMER_TEXT.trim(),
            "documentMetadata" to mapOf(
                "id" to doc.id,
                "title" to doc.title,
                "sourceFileName" to doc.sourceFileName,
                "sourceCategory" to doc.sourceCategory,
                "classifiedType" to doc.classifiedType,
                "fileFormat" to doc.fileFormat,
                "fileSizeBytes" to doc.fileSizeBytes,
                "pageCount" to doc.pageCount,
                "status" to doc.status,
                "overallConfidence" to doc.overallConfidence,
                "processedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(doc.processedAt ?: System.currentTimeMillis()))
            ),
            "structuredRecord" to data,
            "pages" to pages.map { page ->
                mapOf(
                    "pageIndex" to page.pageIndex,
                    "ocrConfidence" to page.ocrConfidence,
                    "ocrDurationMs" to page.ocrDurationMs,
                    "rawOcrText" to page.rawOcrText
                )
            }
        )

        val adapter = moshi.adapter(Map::class.java)
        return adapter.indent("  ").toJson(payload)
    }

    /**
     * Builds clean, human-readable Bengali structured summary text.
     */
    fun buildTextSummaryContent(
        doc: LandDocumentEntity,
        data: LandRecordData
    ): String {
        val sb = StringBuilder()
        sb.append(LEGAL_DISCLAIMER_TEXT.trim()).append("\n\n")
        sb.append("📋 বাংলাদেশ ভূমি রেকর্ড রিপোর্ট (সারসংক্ষেপ)\n")
        sb.append("তৈরির তারিখ: ").append(SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())).append("\n")
        sb.append("নথির নাম: ").append(doc.title).append("\n")
        sb.append("মূল ফাইল: ").append(doc.sourceFileName).append(" (ক্যাটাগরি: ").append(doc.sourceCategory).append(")\n")
        sb.append("জরিপ/রেকর্ড ধরন: ").append(data.recordType.bengaliName).append("\n")
        sb.append("অবস্থা: ").append(doc.status).append(" (গড় নির্ভরযোগ্যতা: ").append(String.format(Locale.US, "%.1f%%", doc.overallConfidence)).append(")\n")
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("১. ভৌগোলিক ও অবস্থান সংক্রান্ত তথ্য:\n")
        sb.append("   • জেলা: ").append(data.district.value).append(" [").append(data.district.confidence).append("]\n")
        sb.append("   • উপজেলা/থানা: ").append(data.upazilaThana.value).append(" [").append(data.upazilaThana.confidence).append("]\n")
        sb.append("   • মৌজা: ").append(data.mouza.value).append(" [").append(data.mouza.confidence).append("]\n")
        sb.append("   • জে. এল. নং: ").append(data.jlNo.value).append(" [").append(data.jlNo.confidence).append("]\n")
        if (data.touziNo.value.isNotBlank()) {
            sb.append("   • তৌজি নং: ").append(data.touziNo.value).append("\n")
        }
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("২. খতিয়ান ও দাগের বিস্তারিত তথ্য:\n")
        sb.append("   • খতিয়ান নং: ").append(data.khatianNo.value).append(" [").append(data.khatianNo.confidence).append("]\n")
        if (data.formerKhatianNo.value.isNotBlank()) {
            sb.append("   • সাবেক খতিয়ান নং: ").append(data.formerKhatianNo.value).append("\n")
        }
        sb.append("   • দাগ নং: ").append(data.dagNo.value).append(" [").append(data.dagNo.confidence).append("]\n")
        if (data.formerDagNo.value.isNotBlank()) {
            sb.append("   • সাবেক দাগ নং: ").append(data.formerDagNo.value).append("\n")
        }
        if (data.halDagNo.value.isNotBlank()) {
            sb.append("   • হাল দাগ নং: ").append(data.halDagNo.value).append("\n")
        }
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("৩. জমির শ্রেণি ও পরিমাপ:\n")
        sb.append("   • শ্রেণি: ").append(data.landClass.value).append(" [").append(data.landClass.confidence).append("]\n")
        sb.append("   • অংশ / হিস্যা: ").append(data.landShareHissa.value).append("\n")
        sb.append("   • জমির পরিমাণ (একর): ").append(data.areaAcres.value).append(" একর\n")
        sb.append("   • জমির পরিমাণ (শতাংশ/শতক): ").append(data.areaDecimals.value).append(" শতাংশ\n")
        sb.append("   • বার্ষিক দাবি / খাজনা: ").append(data.annualRent.value).append("\n")
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("৪. মালিক ও রায়তের বিবরণ:\n")
        if (data.owners.isEmpty()) {
            sb.append("   (মালিকের তথ্য পাওয়া যায়নি)\n")
        } else {
            for ((idx, owner) in data.owners.withIndex()) {
                sb.append("   (").append(idx + 1).append(") ").append(owner.name)
                if (owner.fatherOrHusbandName.isNotBlank()) {
                    sb.append(", পিতা/স্বামী: ").append(owner.fatherOrHusbandName)
                }
                sb.append(", অংশ: ").append(owner.shareHissa)
                if (owner.address.isNotBlank()) {
                    sb.append(", ঠিকানা: ").append(owner.address)
                }
                sb.append("\n")
            }
        }
        sb.append("--------------------------------------------------------------------------------\n")
        if (data.validationWarnings.isNotEmpty()) {
            sb.append("⚠️ যাচাইকরণ সতর্কতা ও গাণিতিক নোট:\n")
            for (w in data.validationWarnings) {
                sb.append("   • ").append(w).append("\n")
            }
            sb.append("--------------------------------------------------------------------------------\n")
        }
        sb.append("স্মারকলিপি: মূল দলিলে উল্লেখিত সীল, স্বাক্ষর ও দাগ নম্বর স্বচক্ষে দেখে সিদ্ধান্ত নিন।\n")
        return sb.toString()
    }

    /**
     * Builds consolidated batch JSON report for multiple records.
     */
    fun buildBatchJsonReportContent(
        records: List<Pair<LandDocumentEntity, LandRecordData>>
    ): String {
        val payload = mapOf(
            "app" to "Newaz Land Record Extractor",
            "exportTimestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            "legalDisclaimer" to LEGAL_DISCLAIMER_TEXT.trim(),
            "totalRecords" to records.size,
            "records" to records.map { (doc, data) ->
                mapOf(
                    "documentId" to doc.id,
                    "title" to doc.title,
                    "sourceFileName" to doc.sourceFileName,
                    "sourceCategory" to doc.sourceCategory,
                    "recordType" to data.recordType.name,
                    "status" to doc.status,
                    "khatianNo" to data.khatianNo.value,
                    "dagNo" to data.dagNo.value,
                    "district" to data.district.value,
                    "upazila" to data.upazilaThana.value,
                    "mouza" to data.mouza.value,
                    "jlNo" to data.jlNo.value,
                    "landClass" to data.landClass.value,
                    "areaAcres" to data.areaAcres.value,
                    "areaDecimals" to data.areaDecimals.value,
                    "owners" to data.owners,
                    "validationWarnings" to data.validationWarnings
                )
            }
        )
        val adapter = moshi.adapter(Map::class.java)
        return adapter.indent("  ").toJson(payload)
    }

    /**
     * Builds consolidated batch human-readable text summary for all records.
     */
    fun buildBatchTextSummaryContent(
        records: List<Pair<LandDocumentEntity, LandRecordData>>
    ): String {
        val sb = StringBuilder()
        sb.append(LEGAL_DISCLAIMER_TEXT.trim()).append("\n\n")
        sb.append("📋 বাংলাদেশ ভূমি রেকর্ড সামগ্রিক সারসংক্ষেপ প্রতিবেদন\n")
        sb.append("মোট নথির সংখ্যা: ").append(records.size).append("\n")
        sb.append("তারিখ: ").append(SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())).append("\n")
        sb.append("================================================================================\n\n")

        for ((index, pair) in records.withIndex()) {
            val (doc, data) = pair
            sb.append("নথি #").append(index + 1).append(": ").append(doc.title).append("\n")
            sb.append("ফাইল: ").append(doc.sourceFileName).append(" | জরিপ: ").append(data.recordType.bengaliName).append("\n")
            sb.append("খতিয়ান নং: ").append(data.khatianNo.value).append(" | দাগ নং: ").append(data.dagNo.value).append("\n")
            sb.append("মৌজা: ").append(data.mouza.value).append(" | জে. এল. নং: ").append(data.jlNo.value).append(" | থানা: ").append(data.upazilaThana.value).append(" | জেলা: ").append(data.district.value).append("\n")
            sb.append("জমির শ্রেণি: ").append(data.landClass.value).append(" | পরিমাণ: ").append(data.areaDecimals.value).append(" শতাংশ (").append(data.areaAcres.value).append(" একর)\n")
            val owners = data.owners.joinToString(", ") { "${it.name} (${it.shareHissa})" }
            sb.append("মালিকগণ: ").append(if (owners.isNotBlank()) owners else "অনুল্লেখিত").append("\n")
            sb.append("--------------------------------------------------------------------------------\n\n")
        }

        return sb.toString()
    }

    /**
     * Safely escapes a string or number for standard RFC 4180 CSV export.
     * Encloses strings with quotes and escapes internal double-quotes.
     */
    fun escapeCsv(value: Any?): String {
        if (value == null) return "\"\""
        val str = value.toString()
        val escaped = str.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Builds comprehensive CSV content directly from Room database entities (LandDocumentEntity).
     * Includes UTF-8 BOM (\uFEFF) so Excel and Google Sheets correctly render Bengali Unicode characters.
     */
    fun buildDocumentsCsvContent(
        documents: List<LandDocumentEntity>
    ): String {
        val sb = StringBuilder()
        // Prepend UTF-8 Byte Order Mark for Excel/Spreadsheet compatibility with Bengali characters
        sb.append("\uFEFF")
        sb.append("Document_ID,Title,Survey_Type,Source_Category,Source_File,File_Format,File_Size_KB,Page_Count,Status,District,Upazila,Mouza,JL_No,Khatian_No,Dag_No,Land_Class,Area_Decimals,Owners,Confidence_Score,Date_Captured,Validation_Notes\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        for (doc in documents) {
            val dateStr = if (doc.dateCaptured > 0L) dateFormat.format(Date(doc.dateCaptured)) else ""
            val sizeKb = String.format(Locale.US, "%.1f", doc.fileSizeBytes / 1024.0)
            val confidenceStr = String.format(Locale.US, "%.1f%%", doc.overallConfidence)
            val notes = if (doc.isZeroByte) "০-বাইট খালি ফাইল সতর্কবার্তা" else (doc.userNotes ?: "")

            sb.append(escapeCsv(doc.id)).append(",")
            sb.append(escapeCsv(doc.title)).append(",")
            sb.append(escapeCsv(doc.classifiedType)).append(",")
            sb.append(escapeCsv(doc.sourceCategory)).append(",")
            sb.append(escapeCsv(doc.sourceFileName)).append(",")
            sb.append(escapeCsv(doc.fileFormat)).append(",")
            sb.append(escapeCsv(sizeKb)).append(",")
            sb.append(doc.pageCount).append(",")
            sb.append(escapeCsv(doc.status)).append(",")
            sb.append(escapeCsv(doc.primaryDistrict ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryUpazila ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryMouza ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryJlNo ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryKhatianNo ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryDagNo ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryLandClass ?: "")).append(",")
            sb.append(escapeCsv(doc.primaryAreaDecimals ?: "")).append(",")
            sb.append(escapeCsv(doc.ownersSummary ?: "")).append(",")
            sb.append(escapeCsv(confidenceStr)).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(notes)).append("\n")
        }

        return sb.toString()
    }

    /**
     * Builds CSV content for spreadsheet applications from record pairs.
     */
    fun buildBatchCsvContent(
        records: List<Pair<LandDocumentEntity, LandRecordData>>
    ): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Document_ID,Title,Survey_Type,District,Upazila,Mouza,JL_No,Khatian_No,Former_Dag,Hal_Dag,Land_Class,Area_Acres,Area_Decimals,Owners,Status,Validation_Notes\n")

        for ((doc, data) in records) {
            val ownersFormatted = data.owners.joinToString(" ; ") { "${it.name} (${it.shareHissa})" }
            val warningsFormatted = data.validationWarnings.joinToString(" ; ")

            sb.append(escapeCsv(doc.id)).append(",")
            sb.append(escapeCsv(doc.title)).append(",")
            sb.append(escapeCsv(data.recordType.name)).append(",")
            sb.append(escapeCsv(data.district.value)).append(",")
            sb.append(escapeCsv(data.upazilaThana.value)).append(",")
            sb.append(escapeCsv(data.mouza.value)).append(",")
            sb.append(escapeCsv(data.jlNo.value)).append(",")
            sb.append(escapeCsv(data.khatianNo.value)).append(",")
            sb.append(escapeCsv(data.formerDagNo.value)).append(",")
            sb.append(escapeCsv(data.halDagNo.value)).append(",")
            sb.append(escapeCsv(data.landClass.value)).append(",")
            sb.append(escapeCsv(data.areaAcres.value)).append(",")
            sb.append(escapeCsv(data.areaDecimals.value)).append(",")
            sb.append(escapeCsv(ownersFormatted)).append(",")
            sb.append(escapeCsv(doc.status)).append(",")
            sb.append(escapeCsv(warningsFormatted)).append("\n")
        }

        return sb.toString()
    }

    /**
     * Streams content string to the user-selected local destination Uri
     * using the Android Storage Access Framework (SAF).
     */
    suspend fun writeContentToSafUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IOException("Could not open output stream for SAF Uri: $uri")
        }
    }

    /**
     * Suggests a descriptive filename for saving via Storage Access Framework.
     */
    fun suggestFileName(
        doc: LandDocumentEntity?,
        format: ExportFormat
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return if (doc != null) {
            val khatian = doc.primaryKhatianNo?.replace(Regex("[^0-9a-zA-Z\\u0980-\\u09FF]"), "") ?: "record"
            if (format == ExportFormat.CSV) {
                "khatian_${khatian}_metadata_$timestamp.csv"
            } else {
                "khatian_${khatian}_report_$timestamp.${format.extension}"
            }
        } else {
            if (format == ExportFormat.CSV) {
                "land_records_metadata_$timestamp.csv"
            } else {
                "land_records_summary_$timestamp.${format.extension}"
            }
        }
    }

    suspend fun generateJsonReport(
        context: Context,
        doc: LandDocumentEntity,
        pages: List<DocumentPageEntity>,
        data: LandRecordData
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val jsonFile = File(exportDir, "record_${doc.primaryKhatianNo ?: doc.id}_export.json")
        val content = buildJsonReportContent(doc, pages, data)
        FileOutputStream(jsonFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        jsonFile
    }

    suspend fun generateTextSummary(
        context: Context,
        doc: LandDocumentEntity,
        data: LandRecordData
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val txtFile = File(exportDir, "record_${doc.primaryKhatianNo ?: doc.id}_summary.txt")
        val content = buildTextSummaryContent(doc, data)
        FileOutputStream(txtFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        txtFile
    }

    suspend fun generateCsvBatch(
        context: Context,
        docs: List<Pair<LandDocumentEntity, LandRecordData>>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val csvFile = File(exportDir, "land_records_batch_${System.currentTimeMillis()}.csv")
        val content = buildBatchCsvContent(docs)
        FileOutputStream(csvFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        csvFile
    }

    /**
     * Generates a physical CSV file containing all document metadata directly from Room entities.
     */
    suspend fun generateDocumentsCsv(
        context: Context,
        docs: List<LandDocumentEntity>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val csvFile = File(exportDir, "land_records_metadata_$timestamp.csv")
        val content = buildDocumentsCsvContent(docs)
        FileOutputStream(csvFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        csvFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "নথি রিপোর্ট শেয়ার করুন")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
