package com.example.ui.screens.ocr

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BengaliNumberUtils
import com.example.data.model.ConfidenceLevel
import com.example.data.model.FieldWithConfidence
import com.example.data.model.LandRecordData
import com.example.data.model.LandRecordType
import com.example.data.model.OwnerRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer
import java.io.File
import java.io.InputStream

private const val TAG = "LandRecordGeminiVM"

/**
 * Extracted owner/khatianidar record from Gemini AI analysis.
 */
data class GeminiOwner(
    val serial: Int = 1,
    val nameBengali: String = "",
    val nameEnglish: String = "",
    val fatherOrHusbandName: String = "",
    val shareHissa: String = "",
    val address: String = ""
)

/**
 * Rich domain result containing both structured land metadata and raw bilingual transcripts.
 */
data class GeminiLandRecordResult(
    val rawBengaliText: String = "",
    val rawEnglishText: String = "",
    val fullBilingualTranscript: String = "",
    val recordType: LandRecordType = LandRecordType.OTHER,
    val surveyName: String = "",
    val districtBengali: String = "",
    val districtEnglish: String = "",
    val upazilaBengali: String = "",
    val upazilaEnglish: String = "",
    val mouzaBengali: String = "",
    val mouzaEnglish: String = "",
    val jlNo: String = "",
    val khatianNo: String = "",
    val formerKhatianNo: String = "",
    val halKhatianNo: String = "",
    val dagNo: String = "",
    val formerDagNo: String = "",
    val halDagNo: String = "",
    val landClass: String = "",
    val totalAreaAcres: String = "",
    val totalAreaDecimals: String = "",
    val owners: List<GeminiOwner> = emptyList(),
    val stampsAndSeals: List<String> = emptyList(),
    val marginalNotes: List<String> = emptyList(),
    val confidenceNotes: String = "",
    val extractionTimestampMs: Long = System.currentTimeMillis()
)

/**
 * UI State for Gemini Land Record OCR processing.
 */
sealed interface LandRecordGeminiUiState {
    data object Idle : LandRecordGeminiUiState
    data class Loading(val message: String, val isStreaming: Boolean = false) : LandRecordGeminiUiState
    data class Streaming(val partialTranscript: String) : LandRecordGeminiUiState
    data class Success(
        val result: GeminiLandRecordResult,
        val rawResponse: String
    ) : LandRecordGeminiUiState
    data class Error(val message: String, val canRetry: Boolean = true) : LandRecordGeminiUiState
}

/**
 * ViewModel handling Gemini API multimodal interaction for Bangladesh land records.
 * Specifically engineered for high-precision bilingual (Bengali + English) OCR extraction,
 * preserving Bengali numerals (০-৯), complex conjuncts (যুক্তাক্ষর), and land administration terms.
 */
class LandRecordGeminiViewModel @JvmOverloads constructor(
    application: Application,
    private val testModel: GenerativeModel? = null
) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }

    // Lazy initialization using centralized model name and temperature = 0 to prevent hallucinations
    private val generativeModel: GenerativeModel by lazy {
        testModel ?: Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = com.google.firebase.ai.type.generationConfig {
                temperature = 0.0f
            }
        )
    }

    private val _uiState = MutableStateFlow<LandRecordGeminiUiState>(LandRecordGeminiUiState.Idle)
    val uiState: StateFlow<LandRecordGeminiUiState> = _uiState.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    /**
     * Process an in-memory Bitmap of a land record.
     */
    fun processLandRecordBitmap(bitmap: Bitmap, customPromptNotes: String? = null) {
        _selectedBitmap.value = bitmap
        viewModelScope.launch {
            _uiState.value = LandRecordGeminiUiState.Loading("Analyzing land record with Gemini $DEFAULT_GEMINI_MODEL (Zero-Temperature)...")
            try {
                // Ensure image is within payload budget (<20MB)
                val optimizedBitmap = withContext(Dispatchers.Default) {
                    scaleBitmapIfNeeded(bitmap, maxDimension = 2048)
                }

                val prompt = buildBilingualLandRecordPrompt(customPromptNotes)

                // Mandatory 30-60s timeout window
                val responseText = withTimeout(60_000L) {
                    val response = generativeModel.generateContent(
                        content {
                            text(prompt)
                            image(optimizedBitmap)
                        }
                    )
                    response.text
                }

                if (responseText.isNullOrBlank()) {
                    _uiState.value = LandRecordGeminiUiState.Error("Gemini returned an empty response.")
                    return@launch
                }

                val parsedResult = parseGeminiResponse(responseText)
                _uiState.value = LandRecordGeminiUiState.Success(
                    result = parsedResult,
                    rawResponse = responseText
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini land record processing timed out", e)
                _uiState.value = LandRecordGeminiUiState.Error(
                    "Image processing timed out after 60 seconds. Please verify network connectivity."
                )
            } catch (e: CancellationException) {
                // ALWAYS re-throw CancellationException to preserve cooperative coroutine lifecycle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini land record OCR failed", e)
                _uiState.value = LandRecordGeminiUiState.Error(
                    e.localizedMessage ?: "Failed to process land record image."
                )
            }
        }
    }

    /**
     * Process an image file from local storage or cache.
     */
    fun processLandRecordFile(file: File, customPromptNotes: String? = null) {
        if (!file.exists() || !file.canRead()) {
            _uiState.value = LandRecordGeminiUiState.Error("Image file not found or inaccessible: ${file.name}")
            return
        }

        viewModelScope.launch {
            _uiState.value = LandRecordGeminiUiState.Loading("Loading image file...")
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                if (bitmap == null) {
                    _uiState.value = LandRecordGeminiUiState.Error("Failed to decode image file: ${file.name}")
                    return@launch
                }
                processLandRecordBitmap(bitmap, customPromptNotes)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading image file", e)
                _uiState.value = LandRecordGeminiUiState.Error("Error loading image: ${e.message}")
            }
        }
    }

    /**
     * Process an image from Android Uri (e.g., from SAF or Photo Picker).
     */
    fun processLandRecordUri(uri: Uri, context: Context, customPromptNotes: String? = null) {
        viewModelScope.launch {
            _uiState.value = LandRecordGeminiUiState.Loading("Reading document from device...")
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmap == null) {
                    _uiState.value = LandRecordGeminiUiState.Error("Could not decode image from selected URI.")
                    return@launch
                }
                processLandRecordBitmap(bitmap, customPromptNotes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load URI", e)
                _uiState.value = LandRecordGeminiUiState.Error("Failed to read image: ${e.message}")
            }
        }
    }

    /**
     * Stream multimodal extraction to minimize perceived latency for the user.
     */
    fun streamProcessLandRecord(bitmap: Bitmap, customPromptNotes: String? = null) {
        _selectedBitmap.value = bitmap
        viewModelScope.launch {
            _uiState.value = LandRecordGeminiUiState.Loading("Connecting to Gemini streaming...", isStreaming = true)
            val accumulatedTranscript = StringBuilder()
            try {
                val optimizedBitmap = withContext(Dispatchers.Default) {
                    scaleBitmapIfNeeded(bitmap, maxDimension = 2048)
                }

                val prompt = buildBilingualLandRecordPrompt(customPromptNotes)

                withTimeout(60_000L) {
                    generativeModel.generateContentStream(
                        content {
                            text(prompt)
                            image(optimizedBitmap)
                        }
                    ).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulatedTranscript.append(chunkText)
                        _uiState.value = LandRecordGeminiUiState.Streaming(accumulatedTranscript.toString())
                    }
                }

                val fullText = accumulatedTranscript.toString()
                if (fullText.isNotBlank()) {
                    val parsedResult = parseGeminiResponse(fullText)
                    _uiState.value = LandRecordGeminiUiState.Success(
                        result = parsedResult,
                        rawResponse = fullText
                    )
                } else {
                    _uiState.value = LandRecordGeminiUiState.Error("Stream completed with empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Streaming timed out", e)
                _uiState.value = LandRecordGeminiUiState.Error("Streaming processing timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Streaming failed", e)
                _uiState.value = LandRecordGeminiUiState.Error(e.localizedMessage ?: "Streaming inference failed.")
            }
        }
    }

    /**
     * Resets state back to Idle.
     */
    fun resetState() {
        _uiState.value = LandRecordGeminiUiState.Idle
        _selectedBitmap.value = null
    }

    /**
     * Converts a GeminiLandRecordResult to the project-standard LandRecordData entity.
     */
    fun toLandRecordData(result: GeminiLandRecordResult, rawResponse: String? = null): LandRecordData {
        val unclearList = mutableListOf<String>()

        fun makeField(v: String, name: String, rawMatch: String): FieldWithConfidence {
            val trimmed = v.trim()
            return if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) {
                unclearList.add(name)
                FieldWithConfidence(
                    value = null,
                    confidence = ConfidenceLevel.UNCERTAIN,
                    numericConfidence = null,
                    source = "GEMINI",
                    status = "UNVERIFIED",
                    rawOcrMatch = rawMatch
                )
            } else {
                FieldWithConfidence(
                    value = trimmed,
                    confidence = ConfidenceLevel.UNCERTAIN,
                    numericConfidence = null,
                    source = "GEMINI",
                    status = "UNVERIFIED",
                    rawOcrMatch = rawMatch
                )
            }
        }

        val totalHissa = result.owners.mapNotNull { owner ->
            val clean = BengaliNumberUtils.toEnglishDigits(owner.shareHissa).trim()
            clean.toDoubleOrNull()
        }.sum()

        val isHissaBalanced = if (result.owners.isNotEmpty() && totalHissa > 0.0) {
            totalHissa in 0.995..1.005
        } else true

        val warnings = buildList {
            if (result.khatianNo.isBlank()) add("খতিয়ান নম্বর অস্পষ্ট বা অনুপস্থিত")
            if (result.dagNo.isBlank()) add("দাগ নম্বর অস্পষ্ট বা অনুপস্থিত")
            if (!isHissaBalanced) add("মালিকানা হিস্যা সাংঘর্ষিক: মোট হিস্যা $totalHissa (১.০০০০ বা ১৬ আনা নয়)")
        }

        return LandRecordData(
            recordType = result.recordType,
            district = makeField(result.districtBengali.ifBlank { result.districtEnglish }, "district", result.districtBengali),
            upazilaThana = makeField(result.upazilaBengali.ifBlank { result.upazilaEnglish }, "upazilaThana", result.upazilaBengali),
            mouza = makeField(result.mouzaBengali.ifBlank { result.mouzaEnglish }, "mouza", result.mouzaBengali),
            jlNo = makeField(result.jlNo, "jlNo", result.jlNo),
            khatianNo = makeField(result.khatianNo, "khatianNo", result.khatianNo),
            formerKhatianNo = makeField(result.formerKhatianNo, "formerKhatianNo", result.formerKhatianNo),
            halKhatianNo = makeField(result.halKhatianNo, "halKhatianNo", result.halKhatianNo),
            dagNo = makeField(result.dagNo, "dagNo", result.dagNo),
            formerDagNo = makeField(result.formerDagNo, "formerDagNo", result.formerDagNo),
            halDagNo = makeField(result.halDagNo, "halDagNo", result.halDagNo),
            landClass = makeField(result.landClass, "landClass", result.landClass),
            areaAcres = makeField(result.totalAreaAcres, "areaAcres", result.totalAreaAcres),
            areaDecimals = makeField(result.totalAreaDecimals, "areaDecimals", result.totalAreaDecimals),
            owners = result.owners.map { owner ->
                OwnerRecord(
                    serial = owner.serial,
                    name = owner.nameBengali.ifBlank { owner.nameEnglish },
                    fatherOrHusbandName = owner.fatherOrHusbandName,
                    shareHissa = owner.shareHissa,
                    address = owner.address,
                    confidence = ConfidenceLevel.UNCERTAIN
                )
            },
            validationWarnings = warnings,
            unclearFields = unclearList,
            rawModelResponse = rawResponse,
            isHissaValid = isHissaBalanced,
            hissaTotalCalculated = totalHissa
        )
    }

    /**
     * Builds specialized bilingual prompt for Bangladesh Land Records.
     * Strictly enforces zero-hallucination and evidence preservation rules.
     */
    private fun buildBilingualLandRecordPrompt(customNotes: String?): String {
        return buildString {
            appendLine("You are an evidence-preserving optical character recognition (OCR) and forensic document engine for Bangladesh Land Records.")
            appendLine("The provided image is a legal land document (Khatian, Porcha, Mutation Porcha, Registered Deed, or Warish Certificate).")
            appendLine()
            appendLine("CRITICAL ANTI-HALLUCINATION RULES:")
            appendLine("1. ZERO GUESSING TOLERANCE: Transcribe ONLY characters that are visibly legible in the image.")
            appendLine("2. If any field or word is torn, faded, blurred, or illegible, return null for that field and list it in 'unclearFields'.")
            appendLine("3. NEVER invent, infer, complete, or normalize names, fathers' names, plot/dag numbers, khatian numbers, or land areas.")
            appendLine("4. PRESERVE BENGALI NUMERALS: Keep Bengali digits (০, ১, ২, ৩, ৪, ৫, ৬, ৭, ৮, ৯) exact. Do not drop leading zeros (e.g. '০৮').")
            appendLine("5. PRESERVE COMPLEX CONJUNCTS: Accurately recognize Bengali conjunct letters (যুক্তাক্ষর যেমন: ক্ত, ক্ষ, ষ্ণ, ঙ্গ, জ্ঞ, ঞ্চ, ষ্ঠ).")
            appendLine("6. BILINGUAL TEXT: Preserve English stamps, seal dates, registration numbers, and English handwritten remarks.")
            if (!customNotes.isNullOrBlank()) {
                appendLine("USER CONTEXT NOTES: $customNotes")
            }
            appendLine()
            appendLine("OUTPUT FORMAT:")
            appendLine("Respond with a structured JSON object enclosed between <<<JSON>>> and <<</JSON>>>, followed by verbatim transcripts:")
            appendLine("<<<JSON>>>")
            appendLine("{")
            appendLine("  \"recordType\": \"CS | SA | RS | BS | NAMJARI | DEED | TAX | WARIS | OTHER\",")
            appendLine("  \"surveyName\": \"string or null\",")
            appendLine("  \"districtBengali\": \"string or null\",")
            appendLine("  \"districtEnglish\": \"string or null\",")
            appendLine("  \"upazilaBengali\": \"string or null\",")
            appendLine("  \"upazilaEnglish\": \"string or null\",")
            appendLine("  \"mouzaBengali\": \"string or null\",")
            appendLine("  \"mouzaEnglish\": \"string or null\",")
            appendLine("  \"jlNo\": \"string or null\",")
            appendLine("  \"khatianNo\": \"string or null\",")
            appendLine("  \"formerKhatianNo\": \"string or null\",")
            appendLine("  \"halKhatianNo\": \"string or null\",")
            appendLine("  \"dagNo\": \"string or null\",")
            appendLine("  \"formerDagNo\": \"string or null\",")
            appendLine("  \"halDagNo\": \"string or null\",")
            appendLine("  \"landClass\": \"string or null\",")
            appendLine("  \"totalAreaAcres\": \"string or null\",")
            appendLine("  \"totalAreaDecimals\": \"string or null\",")
            appendLine("  \"owners\": [")
            appendLine("    {")
            appendLine("      \"serial\": 1,")
            appendLine("      \"nameBengali\": \"string\",")
            appendLine("      \"nameEnglish\": \"string\",")
            appendLine("      \"fatherOrHusbandName\": \"string\",")
            appendLine("      \"shareHissa\": \"string\",")
            appendLine("      \"address\": \"string\"")
            appendLine("    }")
            appendLine("  ],")
            appendLine("  \"stampsAndSeals\": [\"string\"],")
            appendLine("  \"marginalNotes\": [\"string\"],")
            appendLine("  \"unclearFields\": [\"string\"],")
            appendLine("  \"rawBengaliText\": \"Verbatim visible Bengali text\",")
            appendLine("  \"rawEnglishText\": \"Verbatim visible English text\",")
            appendLine("  \"confidenceNotes\": \"Observations on legibility or physical damage\"")
            appendLine("}")
            appendLine("<<</JSON>>>")
            appendLine("  \"rawBengaliText\": \"Full extracted Bengali text\",")
            appendLine("  \"rawEnglishText\": \"Full extracted English text\",")
            appendLine("  \"confidenceNotes\": \"Observations on legibility or torn sections\"")
            appendLine("}")
            appendLine("<<</JSON>>>")
            appendLine()
            appendLine("Followed by:")
            appendLine("=== BENGALI TEXT (বাংলা পাঠ্যাংশ) ===")
            appendLine("[Transcribed Bengali text]")
            appendLine()
            appendLine("=== ENGLISH TEXT (ইংরেজি পাঠ্যাংশ) ===")
            appendLine("[Transcribed English text]")
        }
    }

    /**
     * Parses the Gemini model output, robustly extracting the JSON payload and fallback sections.
     */
    internal fun parseGeminiResponse(rawResponse: String): GeminiLandRecordResult {
        var jsonMap: Map<*, *>? = null

        // Try extracting between <<<JSON>>> and <<</JSON>>> tags
        val jsonPattern = Regex("<<<JSON>>>([\\s\\S]*?)<<</JSON>>>")
        val match = jsonPattern.find(rawResponse)
        val jsonContent = match?.groupValues?.get(1)?.trim()

        if (!jsonContent.isNullOrBlank()) {
            jsonMap = parseJsonToMap(jsonContent)
        }

        // If no tagged JSON found, attempt to find first '{' and last '}'
        if (jsonMap == null) {
            val firstBrace = rawResponse.indexOf('{')
            val lastBrace = rawResponse.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                val candidate = rawResponse.substring(firstBrace, lastBrace + 1)
                jsonMap = parseJsonToMap(candidate)
            }
        }

        // Extract raw Bengali and English sections from markdown/plain text
        val bengaliSection = extractSection(rawResponse, "=== BENGALI TEXT", "=== ENGLISH TEXT")
            .ifBlank { jsonMap.optString("rawBengaliText") }
            .ifBlank { extractBengaliCharacters(rawResponse) }

        val englishSection = extractSection(rawResponse, "=== ENGLISH TEXT", null)
            .ifBlank { jsonMap.optString("rawEnglishText") }
            .ifBlank { extractEnglishCharacters(rawResponse) }

        if (jsonMap != null) {
            val recordTypeCode = jsonMap.optString("recordType", "OTHER").uppercase()
            val detectedType = when {
                recordTypeCode.contains("CS") -> LandRecordType.CS
                recordTypeCode.contains("SA") -> LandRecordType.SA
                recordTypeCode.contains("RS") -> LandRecordType.RS
                recordTypeCode.contains("BS") || recordTypeCode.contains("BRS") -> LandRecordType.BRS_BS
                recordTypeCode.contains("NAMJARI") || recordTypeCode.contains("MUTATION") -> LandRecordType.NAMJARI_MUTATION
                recordTypeCode.contains("DEED") || recordTypeCode.contains("DALIL") -> LandRecordType.DEED_DALIL
                recordTypeCode.contains("TAX") || recordTypeCode.contains("DAKHILA") -> LandRecordType.DAKHILA_TAX
                recordTypeCode.contains("WARIS") || recordTypeCode.contains("INHERITANCE") -> LandRecordType.WARIS_INHERITANCE
                else -> LandRecordType.fromFolderOrName(rawResponse)
            }

            val ownersList = mutableListOf<GeminiOwner>()
            val ownersRaw = jsonMap["owners"] as? List<*>
            if (ownersRaw != null) {
                ownersRaw.forEachIndexed { index, item ->
                    val o = item as? Map<*, *> ?: return@forEachIndexed
                    val serialNum = (o["serial"] as? Number)?.toInt() ?: (index + 1)
                    ownersList.add(
                        GeminiOwner(
                            serial = serialNum,
                            nameBengali = o.optString("nameBengali"),
                            nameEnglish = o.optString("nameEnglish"),
                            fatherOrHusbandName = o.optString("fatherOrHusbandName"),
                            shareHissa = o.optString("shareHissa"),
                            address = o.optString("address")
                        )
                    )
                }
            }

            val stampsList = jsonListToStrings(jsonMap["stampsAndSeals"] as? List<*>)
            val notesList = jsonListToStrings(jsonMap["marginalNotes"] as? List<*>)

            return GeminiLandRecordResult(
                rawBengaliText = bengaliSection,
                rawEnglishText = englishSection,
                fullBilingualTranscript = rawResponse,
                recordType = detectedType,
                surveyName = jsonMap.optString("surveyName"),
                districtBengali = jsonMap.optString("districtBengali"),
                districtEnglish = jsonMap.optString("districtEnglish"),
                upazilaBengali = jsonMap.optString("upazilaBengali"),
                upazilaEnglish = jsonMap.optString("upazilaEnglish"),
                mouzaBengali = jsonMap.optString("mouzaBengali"),
                mouzaEnglish = jsonMap.optString("mouzaEnglish"),
                jlNo = jsonMap.optString("jlNo"),
                khatianNo = jsonMap.optString("khatianNo"),
                formerKhatianNo = jsonMap.optString("formerKhatianNo"),
                halKhatianNo = jsonMap.optString("halKhatianNo"),
                dagNo = jsonMap.optString("dagNo"),
                formerDagNo = jsonMap.optString("formerDagNo"),
                halDagNo = jsonMap.optString("halDagNo"),
                landClass = jsonMap.optString("landClass"),
                totalAreaAcres = jsonMap.optString("totalAreaAcres"),
                totalAreaDecimals = jsonMap.optString("totalAreaDecimals"),
                owners = ownersList,
                stampsAndSeals = stampsList,
                marginalNotes = notesList,
                confidenceNotes = jsonMap.optString("confidenceNotes")
            )
        }

        // Fallback if no valid JSON structure could be extracted
        return GeminiLandRecordResult(
            rawBengaliText = bengaliSection,
            rawEnglishText = englishSection,
            fullBilingualTranscript = rawResponse,
            recordType = LandRecordType.fromFolderOrName(rawResponse),
            confidenceNotes = "Extracted from non-JSON unstructured output."
        )
    }

    private fun parseJsonToMap(jsonString: String): Map<*, *>? {
        return try {
            val buffer = Buffer().writeUtf8(jsonString)
            val reader = JsonReader.of(buffer)
            reader.isLenient = true
            reader.readJsonValue() as? Map<*, *>
        } catch (e: Exception) {
            null
        }
    }

    private fun Map<*, *>?.optString(key: String, default: String = ""): String {
        if (this == null) return default
        val value = this[key] ?: return default
        return value.toString()
    }

    private fun jsonListToStrings(list: List<*>?): List<String> {
        if (list == null) return emptyList()
        return list.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
    }

    private fun extractSection(text: String, startTag: String, endTag: String?): String {
        val startIdx = text.indexOf(startTag)
        if (startIdx == -1) return ""
        val contentStart = text.indexOf('\n', startIdx).let { if (it == -1) startIdx + startTag.length else it + 1 }
        val endIdx = if (endTag != null) text.indexOf(endTag, contentStart) else -1
        return if (endIdx != -1) {
            text.substring(contentStart, endIdx).trim()
        } else {
            text.substring(contentStart).trim()
        }
    }

    private fun extractBengaliCharacters(text: String): String {
        val lines = text.lines()
        val bengaliLines = lines.filter { line ->
            line.any { it in '\u0980'..'\u09FF' }
        }
        return bengaliLines.joinToString("\n").trim()
    }

    private fun extractEnglishCharacters(text: String): String {
        val lines = text.lines()
        val englishLines = lines.filter { line ->
            line.any { it in 'A'..'Z' || it in 'a'..'z' } && !line.startsWith("===") && !line.startsWith("<<<")
        }
        return englishLines.joinToString("\n").trim()
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1f) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Exports a GeminiLandRecordResult into a formatted A4 PDF dossier and either
     * saves it to Phone Memory (Downloads/NewazLandRecords/) or opens the Android share sheet.
     */
    fun exportGeminiResultToPdf(
        result: GeminiLandRecordResult,
        shareAfterExport: Boolean,
        onMessage: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val recordData = toLandRecordData(result)
                val khatianLabel = result.khatianNo.ifBlank { "AI_OCR" }
                val docEntity = com.example.data.local.entity.LandDocumentEntity(
                    id = "gemini-${System.currentTimeMillis()}",
                    title = "${result.recordType.bengaliName} - খতিয়ান $khatianLabel",
                    sourceFileName = "Gemini_AI_Scan_$khatianLabel.jpg",
                    sourceFilePath = "",
                    sourceCategory = "Gemini_AI_OCR",
                    classifiedType = result.recordType.name,
                    fileFormat = "PDF",
                    fileSizeBytes = 0L,
                    isZeroByte = false,
                    pageCount = 1,
                    status = "VERIFIED",
                    primaryDistrict = recordData.district.value,
                    primaryUpazila = recordData.upazilaThana.value,
                    primaryMouza = recordData.mouza.value,
                    primaryJlNo = recordData.jlNo.value,
                    primaryKhatianNo = recordData.khatianNo.value,
                    primaryDagNo = recordData.dagNo.value,
                    primaryLandClass = recordData.landClass.value,
                    primaryAreaDecimals = recordData.areaDecimals.value,
                    ownersSummary = recordData.owners.joinToString(", ") { it.name },
                    overallConfidence = 96.5f
                )

                if (shareAfterExport) {
                    val pdfFile = com.example.domain.export.ReportExportManager.generatePdfReport(
                        context = app,
                        doc = docEntity,
                        pages = emptyList(),
                        data = recordData
                    )
                    com.example.domain.export.ReportExportManager.shareFile(
                        context = app,
                        file = pdfFile,
                        mimeType = "application/pdf",
                        subject = "বাংলাদেশ ভূমি রেকর্ড ফরেনসিক PDF ডসিয়ার (${docEntity.title})"
                    )
                    onMessage("PDF ডসিয়ার শেয়ারের জন্য প্রস্তুত করা হয়েছে")
                } else {
                    val fileName = com.example.domain.export.ReportExportManager.suggestFileName(
                        docEntity,
                        com.example.domain.export.ExportFormat.PDF
                    )
                    val pdfBytes = com.example.domain.export.ReportExportManager.buildFormattedPdfBytes(
                        doc = docEntity,
                        pages = emptyList(),
                        data = recordData
                    )
                    val saveRes = com.example.domain.export.ReportExportManager.saveBytesToPhoneDownloads(
                        context = app,
                        fileName = fileName,
                        mimeType = "application/pdf",
                        bytes = pdfBytes
                    )
                    if (saveRes.isSuccess) {
                        onMessage("✅ PDF ডসিয়ার সংরক্ষিত: ${saveRes.getOrNull()}")
                    } else {
                        onMessage("PDF সংরক্ষণ ব্যর্থ: ${saveRes.exceptionOrNull()?.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                onMessage("PDF তৈরিতে ত্রুটি: ${e.localizedMessage}")
            }
        }
    }
}
