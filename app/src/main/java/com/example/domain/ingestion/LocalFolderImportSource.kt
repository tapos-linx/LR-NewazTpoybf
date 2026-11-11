package com.example.domain.ingestion

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.domain.saf.FolderScanSummary
import com.example.domain.saf.SafFolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recursive Local Folder Import Source via ACTION_OPEN_DOCUMENT_TREE (Phase 2A).
 * Recursively traverses directories and nested .zip archives with ZipSlip & ZipBomb defense.
 */
class LocalFolderImportSource(
    private val context: Context,
    private val scanner: SafFolderScanner = SafFolderScanner(context)
) {

    companion object {
        fun createOpenDocumentTreeIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
        }
    }

    fun takePersistableTreePermission(treeUri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun scanFolderTreeRecursively(treeUri: Uri): FolderScanSummary = withContext(Dispatchers.IO) {
        takePersistableTreePermission(treeUri)
        scanner.scanDirectory(treeUri)
    }
}
