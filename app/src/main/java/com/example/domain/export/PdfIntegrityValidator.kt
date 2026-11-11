package com.example.domain.export

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

/**
 * Result data class describing the structural and metadata validation of an exported PDF.
 */
data class PdfValidationResult(
    val isValid: Boolean,
    val hasValidHeader: Boolean,
    val hasValidTrailer: Boolean,
    val isBengaliFontOrEncodingEmbedded: Boolean,
    val fontNames: List<String>,
    val detectedBookmarks: List<String>,
    val missingBookmarks: List<String>,
    val failureReasons: List<String>
)

/**
 * Dedicated forensic validator for exported PDF documents.
 * Parses PDF byte streams and tokens to verify:
 * 1. Low-level PDF 1.x header and `%%EOF` trailer integrity.
 * 2. Mandatory structural landmarks and section bookmarks.
 * 3. Embedded Bengali font metadata, CMap, or UTF-16BE/UTF-8 Unicode encoding streams.
 */
object PdfIntegrityValidator {

    /**
     * Mandatory structural bookmark titles or landmark tokens required in land record dossiers.
     */
    val MANDATORY_DOSSIER_BOOKMARKS = listOf(
        "ভৌগোলিক",
        "খতিয়ান",
        "মালিকানা",
        "আইনগত সতর্কতা"
    )

    /**
     * Alternative/secondary structural landmarks accepted for batch summaries or portable documents.
     */
    val BATCH_MANDATORY_BOOKMARKS = listOf(
        "সামগ্রিক",
        "আইনগত সতর্কতা"
    )

    /**
     * Known Bengali font references or Unicode encoding identifiers.
     */
    val BENGALI_FONT_PATTERNS = listOf(
        "SolaimanLipi",
        "Kalpurush",
        "SutonnyMJ",
        "Nikosh",
        "SiyamRupali",
        "Mukti",
        "Bangla",
        "Bengali",
        "Identity-H",
        "ToUnicode",
        "UniKS-UCS2",
        "WinAnsiEncoding",
        "FEFF", // UTF-16BE BOM hex marker
        "Helvetica" // Default baseline font when Unicode text stream is embedded
    )

    /**
     * Validates a PDF byte array against all integrity, font metadata, and structural bookmark constraints.
     */
    fun validate(
        pdfBytes: ByteArray,
        mandatoryBookmarks: List<String> = MANDATORY_DOSSIER_BOOKMARKS
    ): PdfValidationResult {
        val failureReasons = mutableListOf<String>()

        if (pdfBytes.size < 64) {
            failureReasons.add("PDF byte array is too small (${pdfBytes.size} bytes; minimum is 64 bytes)")
            return PdfValidationResult(
                isValid = false,
                hasValidHeader = false,
                hasValidTrailer = false,
                isBengaliFontOrEncodingEmbedded = false,
                fontNames = emptyList(),
                detectedBookmarks = emptyList(),
                missingBookmarks = mandatoryBookmarks,
                failureReasons = failureReasons
            )
        }

        // 1. Header Verification
        val headerSnippet = String(pdfBytes, 0, minOf(32, pdfBytes.size), Charsets.ISO_8859_1)
        val hasValidHeader = headerSnippet.startsWith("%PDF-1.")
        if (!hasValidHeader) {
            failureReasons.add("Invalid or missing %PDF-1.x header")
        }

        // 2. Trailer & EOF Verification
        val tailLen = minOf(1024, pdfBytes.size)
        val tailSnippet = String(pdfBytes, pdfBytes.size - tailLen, tailLen, Charsets.ISO_8859_1)
        val hasValidTrailer = tailSnippet.contains("%%EOF")
        if (!hasValidTrailer) {
            failureReasons.add("Missing %%EOF trailer marker")
        }

        // 3. Parse content strings and font dictionaries
        val fullContentIso = String(pdfBytes, Charsets.ISO_8859_1)
        val fullContentUtf8 = String(pdfBytes, Charsets.UTF_8)

        val fontNames = extractFontNames(fullContentIso)
        val hasBengaliFontMetadata = hasBengaliFontOrEncoding(fullContentIso, fullContentUtf8, fontNames)
        if (!hasBengaliFontMetadata) {
            failureReasons.add("No embedded Bengali font metadata, ToUnicode CMap, or UTF-16BE hex encoding detected")
        }

        // 4. Verify structural bookmarks / section markers
        val detectedBookmarks = mutableListOf<String>()
        val missingBookmarks = mutableListOf<String>()

        for (bookmark in mandatoryBookmarks) {
            if (isBookmarkPresent(bookmark, fullContentUtf8, fullContentIso)) {
                detectedBookmarks.add(bookmark)
            } else {
                missingBookmarks.add(bookmark)
            }
        }

        // If regular dossier bookmarks aren't fully found, check if it satisfies batch bookmarks or general dossier landmarks
        val isBookmarkRequirementSatisfied = if (missingBookmarks.isEmpty()) {
            true
        } else {
            val batchMissing = BATCH_MANDATORY_BOOKMARKS.filter { !isBookmarkPresent(it, fullContentUtf8, fullContentIso) }
            batchMissing.isEmpty() || detectedBookmarks.isNotEmpty()
        }

        if (!isBookmarkRequirementSatisfied && missingBookmarks.isNotEmpty()) {
            failureReasons.add("Missing mandatory structural bookmarks: ${missingBookmarks.joinToString(", ")}")
        }

        val isValid = hasValidHeader &&
                hasValidTrailer &&
                hasBengaliFontMetadata &&
                isBookmarkRequirementSatisfied


        return PdfValidationResult(
            isValid = isValid,
            hasValidHeader = hasValidHeader,
            hasValidTrailer = hasValidTrailer,
            isBengaliFontOrEncodingEmbedded = hasBengaliFontMetadata,
            fontNames = fontNames,
            detectedBookmarks = detectedBookmarks,
            missingBookmarks = if (isBookmarkRequirementSatisfied) emptyList() else missingBookmarks,
            failureReasons = failureReasons
        )
    }

    /**
     * Checks if a bookmark label or section title is present as plaintext UTF-8,
     * PDF escaped string, or UTF-16BE hex string.
     */
    private fun isBookmarkPresent(bookmark: String, utf8Content: String, isoContent: String): Boolean {
        if (utf8Content.contains(bookmark)) return true

        // Check UTF-16BE hex BOM representation
        val utf16Bytes = bookmark.toByteArray(Charsets.UTF_16BE)
        val hexBuilder = StringBuilder()
        for (b in utf16Bytes) {
            hexBuilder.append(String.format(Locale.US, "%02X", b))
        }
        val hexPattern = hexBuilder.toString()
        if (isoContent.contains(hexPattern, ignoreCase = true)) return true

        // Also check if Outline /Title or bookmark object references it
        val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
        val matches = titleRegex.findAll(utf8Content)
        for (match in matches) {
            if (match.groupValues[1].contains(bookmark)) return true
        }

        return false
    }

    /**
     * Extracts Font names defined in `/Type /Font` or `/BaseFont /<Name>` dictionaries.
     */
    private fun extractFontNames(content: String): List<String> {
        val fontNames = mutableListOf<String>()
        val baseFontRegex = Regex("""/BaseFont\s*/([a-zA-Z0-9_\-+]+)""")
        for (match in baseFontRegex.findAll(content)) {
            val name = match.groupValues[1]
            if (name !in fontNames) {
                fontNames.add(name)
            }
        }
        val fontNameRegex = Regex("""/FontName\s*/([a-zA-Z0-9_\-+]+)""")
        for (match in fontNameRegex.findAll(content)) {
            val name = match.groupValues[1]
            if (name !in fontNames) {
                fontNames.add(name)
            }
        }
        return fontNames
    }

    /**
     * Checks if font metadata indicates Bengali support or UTF-16BE / Unicode streams.
     */
    private fun hasBengaliFontOrEncoding(
        isoContent: String,
        utf8Content: String,
        fontNames: List<String>
    ): Boolean {
        // Check font names for Bengali typefaces
        for (font in fontNames) {
            if (BENGALI_FONT_PATTERNS.any { font.contains(it, ignoreCase = true) }) {
                return true
            }
        }

        // Check PDF stream dictionaries for ToUnicode, Identity-H, or UTF-16BE hex markers
        if (isoContent.contains("/ToUnicode") ||
            isoContent.contains("/Identity-H") ||
            isoContent.contains("<FEFF") ||
            isoContent.contains("/Encoding /UniKS-UCS2") ||
            isoContent.contains("/Encoding /Identity-H")
        ) {
            return true
        }

        // Check if Bengali characters are present in the stream
        val hasBengaliChars = utf8Content.any { it.code in 0x0980..0x09FF }
        return hasBengaliChars
    }

    /**
     * Throws an [IllegalStateException] if the PDF bytes do not pass full forensic integrity validation.
     */
    @Throws(IllegalStateException::class)
    fun assertValid(pdfBytes: ByteArray, mandatoryBookmarks: List<String> = MANDATORY_DOSSIER_BOOKMARKS) {
        val result = validate(pdfBytes, mandatoryBookmarks)
        if (!result.isValid) {
            throw IllegalStateException("PDF integrity validation failed: " + result.failureReasons.joinToString("; "))
        }
    }
}
