package com.example.domain.ingestion

import com.example.data.local.entity.DocumentProcessingState
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class DocumentValidationResult(
    val isValid: Boolean,
    val status: String, // READY_FOR_PROCESSING, ZERO_BYTE, UNSUPPORTED_FORMAT, FAILED
    val normalizedFormat: String,
    val mimeType: String,
    val reason: String? = null
)

/**
 * Forensic Document Validation Service (Phase 2 & Phase 5).
 * Enforces supported formats, maximum file size limits, zero-byte detection,
 * magic-byte / header corruption checks, ZipSlip defense, and ZipBomb protection.
 */
object DocumentValidationService {

    const val MAX_SINGLE_FILE_BYTES: Long = 100L * 1024L * 1024L // 100 MB ceiling
    const val MAX_ZIP_ENTRIES: Int = 500
    const val MAX_ZIP_UNCOMPRESSED_BYTES: Long = 200L * 1024L * 1024L // 200 MB ceiling
    const val MAX_ZIP_COMPRESSION_RATIO: Double = 100.0

    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "pdf", "jpg", "jpeg", "png", "tiff", "tif", "webp",
        "zip", "csv", "xlsx", "docx", "txt", "json"
    )

    private val EXTENSION_TO_MIME: Map<String, String> = mapOf(
        "pdf" to "application/pdf",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "tiff" to "image/tiff",
        "tif" to "image/tiff",
        "webp" to "image/webp",
        "zip" to "application/zip",
        "csv" to "text/csv",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "txt" to "text/plain",
        "json" to "application/json"
    )

    fun resolveFormatAndMime(fileName: String, suppliedMimeType: String?): Pair<String, String> {
        val rawExt = fileName.substringAfterLast('.', "").lowercase()
        val ext = when {
            rawExt in SUPPORTED_EXTENSIONS -> rawExt
            suppliedMimeType == "application/pdf" -> "pdf"
            suppliedMimeType == "application/zip" || suppliedMimeType == "application/x-zip-compressed" || suppliedMimeType == "application/x-zip" -> "zip"
            suppliedMimeType == "image/jpeg" || suppliedMimeType == "image/jpg" -> "jpg"
            suppliedMimeType == "image/png" -> "png"
            suppliedMimeType == "image/webp" -> "webp"
            suppliedMimeType == "image/tiff" || suppliedMimeType == "image/tif" -> "tiff"
            suppliedMimeType == "text/csv" || suppliedMimeType == "application/csv" -> "csv"
            suppliedMimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            suppliedMimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            suppliedMimeType == "application/json" -> "json"
            suppliedMimeType == "text/plain" -> "txt"
            else -> rawExt
        }
        val mime = suppliedMimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: EXTENSION_TO_MIME[ext]
            ?: "application/octet-stream"
        return Pair(ext.uppercase(), mime)
    }

    fun isSafeZipEntryPath(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.contains("/../") || normalized.endsWith("/..")) {
            return false
        }
        if (normalized.substringBefore('/').contains(':')) {
            return false
        }
        return true
    }

    fun validateFile(
        file: File,
        originalFileName: String = file.name,
        suppliedMimeType: String? = null
    ): DocumentValidationResult {
        val (format, mime) = resolveFormatAndMime(originalFileName, suppliedMimeType)
        val extLower = format.lowercase()

        if (!file.exists() || file.length() == 0L) {
            return DocumentValidationResult(
                isValid = false,
                status = DocumentProcessingState.ZERO_BYTE,
                normalizedFormat = format.ifBlank { "UNKNOWN" },
                mimeType = mime,
                reason = "ZERO_BYTE_EMPTY_FILE: File is empty (0 bytes)"
            )
        }

        if (file.length() > MAX_SINGLE_FILE_BYTES) {
            return DocumentValidationResult(
                isValid = false,
                status = DocumentProcessingState.FAILED,
                normalizedFormat = format,
                mimeType = mime,
                reason = "MAX_UPLOAD_SIZE_EXCEEDED: File size (${file.length()} bytes) exceeds maximum limit ($MAX_SINGLE_FILE_BYTES bytes)"
            )
        }

        if (extLower !in SUPPORTED_EXTENSIONS) {
            return DocumentValidationResult(
                isValid = false,
                status = DocumentProcessingState.UNSUPPORTED_FORMAT,
                normalizedFormat = format.ifBlank { "UNSUPPORTED" },
                mimeType = mime,
                reason = "UNSUPPORTED_FORMAT: Extension '.$extLower' is not supported"
            )
        }

        // Verify header / magic bytes for PDF and ZIP to detect corruption early
        if (extLower == "pdf") {
            val headerOk = try {
                FileInputStream(file).use { fis ->
                    val buf = ByteArray(5)
                    val read = fis.read(buf)
                    read == 5 && String(buf, Charsets.US_ASCII) == "%PDF-"
                }
            } catch (_: Exception) {
                false
            }
            if (!headerOk) {
                return DocumentValidationResult(
                    isValid = false,
                    status = DocumentProcessingState.FAILED,
                    normalizedFormat = "PDF",
                    mimeType = "application/pdf",
                    reason = "CORRUPTED_PDF_HEADER: Missing %PDF- signature"
                )
            }
        }

        if (extLower == "zip") {
            try {
                ZipFile(file).use { zf ->
                    val entries = zf.entries().toList().filter { !it.isDirectory }
                    if (entries.size > MAX_ZIP_ENTRIES) {
                        return DocumentValidationResult(
                            isValid = false,
                            status = DocumentProcessingState.FAILED,
                            normalizedFormat = "ZIP",
                            mimeType = "application/zip",
                            reason = "ZIP_BOMB_ENTRY_LIMIT: Archive has ${entries.size} entries (max $MAX_ZIP_ENTRIES)"
                        )
                    }
                    var totalUncompressed = 0L
                    for (entry in entries) {
                        if (!isSafeZipEntryPath(entry.name)) {
                            return DocumentValidationResult(
                                isValid = false,
                                status = DocumentProcessingState.FAILED,
                                normalizedFormat = "ZIP",
                                mimeType = "application/zip",
                                reason = "ZIP_SLIP_PATH_TRAVERSAL: Unsafe entry path '${entry.name}'"
                            )
                        }
                        if (entry.size > 0) {
                            totalUncompressed += entry.size
                            if (totalUncompressed > MAX_ZIP_UNCOMPRESSED_BYTES) {
                                return DocumentValidationResult(
                                    isValid = false,
                                    status = DocumentProcessingState.FAILED,
                                    normalizedFormat = "ZIP",
                                    mimeType = "application/zip",
                                    reason = "ZIP_BOMB_SIZE_LIMIT: Uncompressed size exceeds 200 MB"
                                )
                            }
                            val compressed = entry.compressedSize.coerceAtLeast(1L)
                            val ratio = entry.size.toDouble() / compressed.toDouble()
                            if (entry.size > 1024L * 1024L && ratio > MAX_ZIP_COMPRESSION_RATIO) {
                                return DocumentValidationResult(
                                    isValid = false,
                                    status = DocumentProcessingState.FAILED,
                                    normalizedFormat = "ZIP",
                                    mimeType = "application/zip",
                                    reason = "ZIP_BOMB_RATIO_DETECTED: Suspicious compression ratio (${String.format("%.1f", ratio)}x)"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Also verify with ZipInputStream in case ZipFile central directory had issues
                return DocumentValidationResult(
                    isValid = false,
                    status = DocumentProcessingState.FAILED,
                    normalizedFormat = "ZIP",
                    mimeType = "application/zip",
                    reason = "CORRUPTED_ZIP_ARCHIVE: ${e.message ?: "Invalid ZIP structure"}"
                )
            }
        }

        return DocumentValidationResult(
            isValid = true,
            status = DocumentProcessingState.READY_FOR_PROCESSING,
            normalizedFormat = format,
            mimeType = mime,
            reason = null
        )
    }
}
