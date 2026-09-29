package com.example.domain.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.LandRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
    val ignoredFilesCount: Int,
    val categoriesFound: Map<String, Int>
)

class SafFolderScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "pdf", "jpg", "jpeg", "png", "tiff", "tif", "webp", "docx", "xlsx", "zip", "bmp"
    )

    suspend fun scanDirectory(treeUri: Uri): FolderScanSummary = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Cannot open document tree from URI: $treeUri")

        val supported = mutableListOf<DiscoveredFile>()
        val zeroBytes = mutableListOf<DiscoveredFile>()
        val duplicates = mutableListOf<DiscoveredFile>()
        val seenHashes = mutableMapOf<String, String>() // sha256 -> original path
        var ignoredCount = 0

        traverseDocument(
            rootDoc,
            parentName = rootDoc.name ?: "input",
            supportedOut = supported,
            zeroBytesOut = zeroBytes,
            duplicatesOut = duplicates,
            seenHashes = seenHashes
        ) {
            ignoredCount++
        }

        val categories = supported.groupBy { it.parentFolder }.mapValues { it.value.size }

        FolderScanSummary(
            rootUri = treeUri,
            totalFilesFound = supported.size + ignoredCount,
            supportedFiles = supported,
            zeroByteFiles = zeroBytes,
            duplicateFiles = duplicates,
            ignoredFilesCount = ignoredCount,
            categoriesFound = categories
        )
    }

    private fun traverseDocument(
        doc: DocumentFile,
        parentName: String,
        supportedOut: MutableList<DiscoveredFile>,
        zeroBytesOut: MutableList<DiscoveredFile>,
        duplicatesOut: MutableList<DiscoveredFile>,
        seenHashes: MutableMap<String, String>,
        onIgnored: () -> Unit
    ) {
        if (doc.isDirectory) {
            val children = doc.listFiles()
            for (child in children) {
                traverseDocument(
                    child,
                    parentName = doc.name ?: parentName,
                    supportedOut = supportedOut,
                    zeroBytesOut = zeroBytesOut,
                    duplicatesOut = duplicatesOut,
                    seenHashes = seenHashes,
                    onIgnored = onIgnored
                )
            }
        } else if (doc.isFile) {
            val name = doc.name ?: "unnamed"
            val ext = name.substringAfterLast('.', "").lowercase()

            if (ext in supportedExtensions) {
                val size = doc.length()
                val isZero = size == 0L
                val hash = if (!isZero) computeSha256(doc.uri) else ""

                // Forensic Duplicate Rule:
                // Only consider duplicate if hash is identical, non-empty, and from same document content.
                val existingSource = if (hash.isNotBlank()) seenHashes[hash] else null
                val isDup = existingSource != null
                if (hash.isNotBlank() && !isDup) {
                    seenHashes[hash] = "$parentName/$name"
                }

                val item = DiscoveredFile(
                    uri = doc.uri,
                    name = name,
                    parentFolder = parentName,
                    mimeType = doc.type,
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
            } else {
                onIgnored()
            }
        }
    }

    /**
     * Computes SHA-256 hash from Uri stream to enforce forensic deduplication.
     */
    fun computeSha256(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
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
     * Safely copies an external SAF file into the app's local private cache directory
     * so original user files remain completely untouched and unmodified.
     */
    suspend fun copyToSandbox(uri: Uri, fileName: String, documentId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "records/$documentId")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF]"), "_")
        val destFile = File(dir, safeName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read stream from URI: $uri")

        destFile
    }
}
