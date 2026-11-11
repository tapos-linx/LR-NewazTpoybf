package com.example.domain.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.LandRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class DiscoveredFile(
    val uri: Uri,
    val name: String,
    val parentFolder: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val isZeroByte: Boolean,
    val format: String,
    val classifiedType: LandRecordType,
    val sha256Hash: String = "",
    val isDuplicate: Boolean = false,
    val duplicateOriginalSource: String? = null
)

data class FolderScanSummary(
    val rootUri: Uri,
    val totalFilesFound: Int,
    val supportedFiles: List<DiscoveredFile>,
    val zeroByteFiles: List<DiscoveredFile>,
    val duplicateFiles: List<DiscoveredFile> = emptyList(),
    val corruptOrRejectedFiles: List<String> = emptyList(),
    val ignoredFilesCount: Int,
    val categoriesFound: Map<String, Int>
)

class SafFolderScanner(private val context: Context) {

    companion object {
        const val MAX_ZIP_ENTRIES = 500
        const val MAX_ZIP_UNCOMPRESSED_BYTES = 200L * 1024L * 1024L // 200 MB cap

        fun isSafeZipEntryPath(entryName: String): Boolean {
            if (entryName.isBlank()) return false
            val normalized = entryName.replace('\\', '/')
            if (normalized.startsWith("/") || normalized.contains("../") || normalized.endsWith("/..")) {
                return false
            }
            if (normalized.substringBefore('/').contains(':')) {
                return false
            }
            return true
        }
    }

    private val supportedExtensions = setOf(
        "pdf", "jpg", "jpeg", "png", "tiff", "tif", "webp", "docx", "xlsx", "zip", "bmp", "csv", "txt", "json"
    )

    suspend fun scanDirectory(treeUri: Uri): FolderScanSummary = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Cannot open document tree from URI: $treeUri")

        val supported = mutableListOf<DiscoveredFile>()
        val zeroBytes = mutableListOf<DiscoveredFile>()
        val duplicates = mutableListOf<DiscoveredFile>()
        val corruptOrRejected = mutableListOf<String>()
        val seenHashes = mutableMapOf<String, String>() // sha256 -> original path
        var ignoredCount = 0

        traverseDocument(
            rootDoc,
            parentName = rootDoc.name ?: "input",
            supportedOut = supported,
            zeroBytesOut = zeroBytes,
            duplicatesOut = duplicates,
            corruptOut = corruptOrRejected,
            seenHashes = seenHashes
        ) {
            ignoredCount++
        }

        val categories = supported.groupBy { it.parentFolder }.mapValues { it.value.size }

        FolderScanSummary(
            rootUri = treeUri,
            totalFilesFound = supported.size + ignoredCount + corruptOrRejected.size,
            supportedFiles = supported,
            zeroByteFiles = zeroBytes,
            duplicateFiles = duplicates,
            corruptOrRejectedFiles = corruptOrRejected,
            ignoredFilesCount = ignoredCount,
            categoriesFound = categories
        )
    }

    /**
     * Scans multiple individual file URIs picked from PC local disk, Downloads folder,
     * SD card, or phone memory via ActivityResultContracts.OpenMultipleDocuments().
     * Automatically unpacks any .zip archives into individual records with ZipSlip and ZipBomb protection.
     */
    suspend fun scanMultipleUris(
        uris: List<Uri>,
        defaultCategory: String = "পিসি_ও_ফোন_বাল্ক_আপলোড"
    ): FolderScanSummary = withContext(Dispatchers.IO) {
        val supported = mutableListOf<DiscoveredFile>()
        val zeroBytes = mutableListOf<DiscoveredFile>()
        val duplicates = mutableListOf<DiscoveredFile>()
        val corruptOrRejected = mutableListOf<String>()
        val seenHashes = mutableMapOf<String, String>()
        var ignoredCount = 0

        for ((idx, uri) in uris.withIndex()) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            val meta = resolveUriMetadata(uri, idx + 1)
            val ext = meta.ext.lowercase()

            if (ext == "zip") {
                val extractedCount = unpackZipUri(
                    zipUri = uri,
                    zipName = meta.name,
                    parentFolder = defaultCategory,
                    supportedOut = supported,
                    zeroBytesOut = zeroBytes,
                    duplicatesOut = duplicates,
                    corruptOut = corruptOrRejected,
                    seenHashes = seenHashes
                )
                if (extractedCount == 0 && corruptOrRejected.isEmpty()) {
                    addDiscoveredItem(
                        uri = uri,
                        name = meta.name,
                        parentName = defaultCategory,
                        mimeType = meta.mimeType,
                        size = meta.sizeBytes,
                        ext = ext,
                        supportedOut = supported,
                        zeroBytesOut = zeroBytes,
                        duplicatesOut = duplicates,
                        seenHashes = seenHashes
                    )
                }
            } else if (ext in supportedExtensions || meta.mimeType?.startsWith("image/") == true || meta.mimeType == "application/pdf") {
                val effectiveExt = when {
                    ext in supportedExtensions -> ext
                    meta.mimeType == "application/pdf" -> "pdf"
                    meta.mimeType?.contains("png") == true -> "png"
                    meta.mimeType?.contains("webp") == true -> "webp"
                    meta.mimeType?.startsWith("image/") == true -> "jpg"
                    else -> "pdf"
                }
                val effectiveName = if (meta.name.contains('.')) meta.name else "${meta.name}.$effectiveExt"
                addDiscoveredItem(
                    uri = uri,
                    name = effectiveName,
                    parentName = defaultCategory,
                    mimeType = meta.mimeType,
                    size = meta.sizeBytes,
                    ext = effectiveExt,
                    supportedOut = supported,
                    zeroBytesOut = zeroBytes,
                    duplicatesOut = duplicates,
                    seenHashes = seenHashes
                )
            } else {
                ignoredCount++
            }
        }

        val categories = supported.groupBy { it.parentFolder }.mapValues { it.value.size }
        FolderScanSummary(
            rootUri = uris.firstOrNull() ?: Uri.EMPTY,
            totalFilesFound = supported.size + ignoredCount + corruptOrRejected.size,
            supportedFiles = supported,
            zeroByteFiles = zeroBytes,
            duplicateFiles = duplicates,
            corruptOrRejectedFiles = corruptOrRejected,
            ignoredFilesCount = ignoredCount,
            categoriesFound = categories
        )
    }

    private data class UriMetadata(
        val name: String,
        val sizeBytes: Long,
        val mimeType: String?,
        val ext: String
    )

    private fun resolveUriMetadata(uri: Uri, fallbackIndex: Int): UriMetadata {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            val name = file.name.ifBlank { "আহরিত_নথি_$fallbackIndex.pdf" }
            val ext = name.substringAfterLast('.', "").lowercase()
            return UriMetadata(
                name = name,
                sizeBytes = if (file.exists()) file.length() else 0L,
                mimeType = guessMimeType(ext),
                ext = ext
            )
        }

        var displayName: String? = null
        var sizeBytes = -1L

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }

        val docFile = try {
            DocumentFile.fromSingleUri(context, uri)
        } catch (_: Exception) {
            null
        }

        if (displayName.isNullOrBlank()) {
            displayName = docFile?.name ?: uri.lastPathSegment?.substringAfterLast('/')
        }
        if (sizeBytes < 0L) {
            sizeBytes = docFile?.length() ?: -1L
        }
        if (sizeBytes <= 0L) {
            sizeBytes = try {
                openInputStreamAny(uri)?.use { stream ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    var r: Int
                    while (stream.read(buf).also { r = it } != -1) {
                        total += r
                    }
                    total
                } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

        val mimeType = try {
            context.contentResolver.getType(uri) ?: docFile?.type
        } catch (_: Exception) {
            docFile?.type
        }

        var ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext.isBlank()) {
            ext = when {
                mimeType == "application/pdf" -> "pdf"
                mimeType == "application/zip" || mimeType == "application/x-zip-compressed" -> "zip"
                mimeType?.contains("png") == true -> "png"
                mimeType?.contains("webp") == true -> "webp"
                mimeType?.contains("tiff") == true -> "tiff"
                mimeType?.startsWith("image/") == true -> "jpg"
                else -> "pdf"
            }
        }

        val finalName = if (displayName.isNullOrBlank()) {
            "আহরিত_নথি_${fallbackIndex}.$ext"
        } else if (!displayName!!.contains('.')) {
            "${displayName}.$ext"
        } else {
            displayName!!
        }

        return UriMetadata(
            name = finalName,
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            mimeType = mimeType ?: guessMimeType(ext),
            ext = ext
        )
    }

    private fun guessMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "tiff", "tif" -> "image/tiff"
            "zip" -> "application/zip"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun unpackZipUri(
        zipUri: Uri,
        zipName: String,
        parentFolder: String,
        supportedOut: MutableList<DiscoveredFile>,
        zeroBytesOut: MutableList<DiscoveredFile>,
        duplicatesOut: MutableList<DiscoveredFile>,
        corruptOut: MutableList<String>,
        seenHashes: MutableMap<String, String>
    ): Int {
        var count = 0
        var totalDecompressedBytes = 0L
        val unpackDir = File(context.cacheDir, "zip_unpack_${UUID.randomUUID()}")
        unpackDir.mkdirs()
        val unpackCanonical = unpackDir.canonicalPath + File.separator
        val archiveFolderLabel = "$parentFolder/${zipName.substringBeforeLast('.')}"

        try {
            openInputStreamAny(zipUri)?.use { rawIn ->
                ZipInputStream(rawIn).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (count >= MAX_ZIP_ENTRIES) {
                            corruptOut.add("$zipName: ZIP entry limit ($MAX_ZIP_ENTRIES) exceeded")
                            break
                        }
                        if (!isSafeZipEntryPath(entry.name)) {
                            corruptOut.add("$zipName/${entry.name}: Rejected unsafe ZIP path traversal")
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        if (!entry.isDirectory) {
                            val entryPath = entry.name.trimStart('/')
                            val subFolder = entryPath.substringBeforeLast('/', "").trim()
                            val entryName = entryPath.substringAfterLast('/')
                            val ext = entryName.substringAfterLast('.', "").lowercase()
                            if (entryName.isNotBlank() && !entryName.startsWith(".") && ext in supportedExtensions && ext != "zip") {
                                val safeName = entryName.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF]"), "_")
                                val outFile = File(unpackDir, "${count + 1}_$safeName")
                                if (!outFile.canonicalPath.startsWith(unpackCanonical)) {
                                    corruptOut.add("$zipName/$entryName: Canonical path traversal blocked")
                                    zis.closeEntry()
                                    entry = zis.nextEntry
                                    continue
                                }

                                var entryBytesWritten = 0L
                                var limitExceeded = false
                                FileOutputStream(outFile).use { fos ->
                                    val buffer = ByteArray(16384)
                                    var read: Int
                                    while (zis.read(buffer).also { read = it } != -1) {
                                        totalDecompressedBytes += read
                                        entryBytesWritten += read
                                        if (totalDecompressedBytes > MAX_ZIP_UNCOMPRESSED_BYTES) {
                                            limitExceeded = true
                                            break
                                        }
                                        fos.write(buffer, 0, read)
                                    }
                                }

                                if (limitExceeded) {
                                    outFile.delete()
                                    corruptOut.add("$zipName: ZIP decompression size limit exceeded (${MAX_ZIP_UNCOMPRESSED_BYTES / (1024 * 1024)} MB)")
                                    break
                                }

                                val effectiveParent = if (subFolder.isNotBlank()) "$archiveFolderLabel/$subFolder" else archiveFolderLabel
                                addDiscoveredItem(
                                    uri = Uri.fromFile(outFile),
                                    name = entryName,
                                    parentName = effectiveParent,
                                    mimeType = guessMimeType(ext),
                                    size = entryBytesWritten,
                                    ext = ext,
                                    supportedOut = supportedOut,
                                    zeroBytesOut = zeroBytesOut,
                                    duplicatesOut = duplicatesOut,
                                    seenHashes = seenHashes
                                )
                                count++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            corruptOut.add("$zipName: Corrupt or unreadable ZIP archive (${e.message ?: "decode error"})")
        }
        return count
    }

    private fun traverseDocument(
        doc: DocumentFile,
        parentName: String,
        supportedOut: MutableList<DiscoveredFile>,
        zeroBytesOut: MutableList<DiscoveredFile>,
        duplicatesOut: MutableList<DiscoveredFile>,
        corruptOut: MutableList<String>,
        seenHashes: MutableMap<String, String>,
        onIgnored: () -> Unit
    ) {
        if (doc.isDirectory) {
            val children = doc.listFiles()
            val folderName = doc.name ?: "folder"
            val nextParent = if (parentName.isBlank() || parentName == "input" || parentName == folderName) {
                folderName
            } else {
                "$parentName/$folderName"
            }
            for (child in children) {
                traverseDocument(
                    child,
                    parentName = nextParent,
                    supportedOut = supportedOut,
                    zeroBytesOut = zeroBytesOut,
                    duplicatesOut = duplicatesOut,
                    corruptOut = corruptOut,
                    seenHashes = seenHashes,
                    onIgnored = onIgnored
                )
            }
        } else if (doc.isFile) {
            val rawName = doc.name ?: "unnamed"
            val docMime = doc.type
            val rawExt = rawName.substringAfterLast('.', "").lowercase()
            val ext = when {
                rawExt in supportedExtensions -> rawExt
                docMime == "application/zip" || docMime == "application/x-zip-compressed" || docMime == "application/x-zip" -> "zip"
                docMime == "application/pdf" -> "pdf"
                docMime?.contains("png") == true -> "png"
                docMime?.contains("webp") == true -> "webp"
                docMime?.contains("tiff") == true -> "tiff"
                docMime?.startsWith("image/") == true -> "jpg"
                docMime == "text/csv" -> "csv"
                docMime == "application/json" -> "json"
                docMime == "text/plain" -> "txt"
                else -> rawExt
            }
            val effectiveName = if (rawName.contains('.') || ext.isBlank()) rawName else "$rawName.$ext"

            if (ext == "zip") {
                val extractedCount = unpackZipUri(
                    zipUri = doc.uri,
                    zipName = effectiveName,
                    parentFolder = parentName,
                    supportedOut = supportedOut,
                    zeroBytesOut = zeroBytesOut,
                    duplicatesOut = duplicatesOut,
                    corruptOut = corruptOut,
                    seenHashes = seenHashes
                )
                if (extractedCount == 0 && corruptOut.isEmpty()) {
                    addDiscoveredItem(
                        uri = doc.uri,
                        name = effectiveName,
                        parentName = parentName,
                        mimeType = docMime,
                        size = doc.length(),
                        ext = ext,
                        supportedOut = supportedOut,
                        zeroBytesOut = zeroBytesOut,
                        duplicatesOut = duplicatesOut,
                        seenHashes = seenHashes
                    )
                }
            } else if (ext in supportedExtensions) {
                addDiscoveredItem(
                    uri = doc.uri,
                    name = effectiveName,
                    parentName = parentName,
                    mimeType = docMime,
                    size = doc.length(),
                    ext = ext,
                    supportedOut = supportedOut,
                    zeroBytesOut = zeroBytesOut,
                    duplicatesOut = duplicatesOut,
                    seenHashes = seenHashes
                )
            } else {
                onIgnored()
            }
        }
    }

    private fun addDiscoveredItem(
        uri: Uri,
        name: String,
        parentName: String,
        mimeType: String?,
        size: Long,
        ext: String,
        supportedOut: MutableList<DiscoveredFile>,
        zeroBytesOut: MutableList<DiscoveredFile>,
        duplicatesOut: MutableList<DiscoveredFile>,
        seenHashes: MutableMap<String, String>
    ) {
        val isZero = size == 0L
        val hash = if (!isZero) computeSha256(uri) else ""

        val existingSource = if (hash.isNotBlank()) seenHashes[hash] else null
        val isDup = existingSource != null
        if (hash.isNotBlank() && !isDup) {
            seenHashes[hash] = "$parentName/$name"
        }

        val item = DiscoveredFile(
            uri = uri,
            name = name,
            parentFolder = parentName,
            mimeType = mimeType ?: guessMimeType(ext),
            sizeBytes = size,
            isZeroByte = isZero,
            format = ext.uppercase(),
            classifiedType = LandRecordType.fromFolderOrName("$parentName $name"),
            sha256Hash = hash,
            isDuplicate = isDup,
            duplicateOriginalSource = existingSource
        )

        supportedOut.add(item)
        if (isZero) {
            zeroBytesOut.add(item)
        }
        if (isDup) {
            duplicatesOut.add(item)
        }
    }

    private fun openInputStreamAny(uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (file.exists()) FileInputStream(file) else null
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    /**
     * Computes SHA-256 hash from Uri stream to enforce forensic deduplication.
     */
    fun computeSha256(uri: Uri): String {
        return try {
            openInputStreamAny(uri)?.use { stream ->
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Safely copies an external SAF or unpacked file into the app's local private sandbox directory
     * so original user files remain completely untouched and unmodified.
     */
    suspend fun copyToSandbox(uri: Uri, fileName: String, documentId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "records/$documentId")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF]"), "_")
        val destFile = File(dir, safeName)

        openInputStreamAny(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read stream from URI: $uri")

        destFile
    }
}
