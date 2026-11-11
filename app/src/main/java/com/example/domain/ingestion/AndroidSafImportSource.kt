package com.example.domain.ingestion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.entity.DocumentSourceType
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android Storage Access Framework (SAF) Document Import Source (Phase 2A).
 * Handles ACTION_OPEN_DOCUMENT (single & multiple files), persistable URI permissions,
 * revoked permission checks, and app-private sandbox copying.
 */
class AndroidSafImportSource(
    private val context: Context,
    private val scanner: SafFolderScanner = SafFolderScanner(context)
) {

    companion object {
        val SUPPORTED_SAF_MIME_TYPES: Array<String> = arrayOf(
            "application/pdf",
            "image/*",
            "application/zip",
            "application/x-zip-compressed",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/json"
        )

        fun createOpenMultipleDocumentsIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_SAF_MIME_TYPES)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        }

        fun createOpenSingleDocumentIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_SAF_MIME_TYPES)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        }
    }

    fun takePersistableReadPermissionIfSupported(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return false)
                f.exists() && f.canRead()
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun scanSelectedDocuments(
        uris: List<Uri>,
        sourceCategory: String = DocumentSourceType.ANDROID_SAF_FILE
    ): FolderScanSummary = withContext(Dispatchers.IO) {
        uris.forEach { takePersistableReadPermissionIfSupported(it) }
        scanner.scanMultipleUris(uris, defaultCategory = sourceCategory)
    }

    suspend fun copyDocumentToPrivateStorage(
        uri: Uri,
        originalFileName: String,
        documentId: String
    ): File = withContext(Dispatchers.IO) {
        if (!isUriAccessible(uri)) {
            throw SecurityException("URI permission revoked or file inaccessible: $uri")
        }
        scanner.copyToSandbox(uri, originalFileName, documentId)
    }
}
