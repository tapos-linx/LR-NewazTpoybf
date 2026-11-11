package com.example.domain.ingestion

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.domain.drive.GoogleDriveManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class GoogleDriveAuthStatus {
    NOT_CONFIGURED,
    AUTHENTICATED,
    AUTH_EXPIRED,
    PERMISSION_DENIED,
    REVOKED
}

data class GoogleDriveRemoteFile(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String?,
    val sha256Checksum: String? = null
)

data class GoogleDriveDownloadOutcome(
    val file: File,
    val googleDriveFileId: String,
    val originalName: String,
    val effectiveMimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val modifiedTime: String? = null
)

/**
 * Genuine Google Drive Import Source (Phase 2C).
 * Implements:
 * - Official Google OAuth 2.0 Bearer authorization against Google Drive API v3
 *   with minimum scope (`https://www.googleapis.com/auth/drive.readonly`)
 * - Zero embedded OAuth client secrets in source code
 * - Explicit NOT_CONFIGURED, AUTH_EXPIRED, and PERMISSION_DENIED diagnostics
 * - Google Workspace export support:
 *   - Google Docs (`application/vnd.google-apps.document`) -> PDF or DOCX
 *   - Google Sheets (`application/vnd.google-apps.spreadsheet`) -> XLSX or CSV
 * - Resumable HTTP Range downloads and retry on transient network failures
 * - SHA-256 content deduplication
 * - Token disconnect/revocation (`https://oauth2.googleapis.com/revoke`)
 */
class GoogleDriveImportSource(
    private val context: Context
) {
    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3/files"
        const val OAUTH_REVOKE_URL = "https://oauth2.googleapis.com/revoke"

        const val MIME_GOOGLE_DOC = "application/vnd.google-apps.document"
        const val MIME_GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet"
        const val MIME_GOOGLE_FOLDER = "application/vnd.google-apps.folder"
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var configuredClientId: String? = null

    @Volatile
    private var authStatus: GoogleDriveAuthStatus = GoogleDriveAuthStatus.NOT_CONFIGURED

    fun configureOAuthClient(clientId: String?, bearerToken: String?) {
        configuredClientId = clientId?.trim()?.takeIf { it.isNotBlank() }
        accessToken = bearerToken?.trim()?.takeIf { it.isNotBlank() }
        authStatus = when {
            !accessToken.isNullOrBlank() -> GoogleDriveAuthStatus.AUTHENTICATED
            else -> GoogleDriveAuthStatus.NOT_CONFIGURED
        }
    }

    fun markAuthExpired() {
        authStatus = GoogleDriveAuthStatus.AUTH_EXPIRED
    }

    fun getAuthStatus(): GoogleDriveAuthStatus = authStatus

    fun getSetupInstructions(): String {
        return "Google Drive OAuth 2.0 is NOT_CONFIGURED. To enable direct Drive API v3 queries, " +
            "register your Android package (com.aistudio.newazlrecord.kxrtpq) and SHA-1 fingerprint " +
            "in Google Cloud Console with scope '$DRIVE_READONLY_SCOPE', or use the built-in " +
            "Android Google Drive SAF File/Folder Picker (${GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL})."
    }

    fun createSafFolderPickerIntent(accountEmail: String = GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL): Intent {
        return GoogleDriveManager.createDriveFolderTreeIntent(accountEmail)
    }

    fun createSafMultiFilesPickerIntent(accountEmail: String = GoogleDriveManager.DEFAULT_DRIVE_ACCOUNT_EMAIL): Intent {
        return GoogleDriveManager.createDriveMultiFilesPickerIntent(accountEmail)
    }

    /**
     * Lists files in a Google Drive folder (or root) using Drive REST API v3.
     */
    suspend fun listFilesInDrive(
        folderId: String? = null,
        baseUrlOverride: String = DRIVE_API_BASE
    ): Result<List<GoogleDriveRemoteFile>> = withContext(Dispatchers.IO) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            authStatus = GoogleDriveAuthStatus.NOT_CONFIGURED
            return@withContext Result.failure(IllegalStateException("NOT_CONFIGURED: ${getSetupInstructions()}"))
        }

        val query = if (!folderId.isNullOrBlank()) {
            "'$folderId' in parents and trashed = false"
        } else {
            "trashed = false"
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime,sha256Checksum)", "UTF-8")
        val url = URL("$baseUrlOverride?q=$encodedQuery&fields=$fields&pageSize=100")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                authStatus = GoogleDriveAuthStatus.AUTH_EXPIRED
                return@withContext Result.failure(SecurityException("AUTH_EXPIRED: Google OAuth access token expired (HTTP 401). Please re-authenticate."))
            }
            if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                authStatus = GoogleDriveAuthStatus.PERMISSION_DENIED
                return@withContext Result.failure(SecurityException("PERMISSION_DENIED: Insufficient Drive permissions (HTTP 403)."))
            }
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("Google Drive API error (HTTP $code)"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any>>(mapType)
            val parsed = adapter.fromJson(body).orEmpty()
            val rawFiles = (parsed["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

            val resultList = rawFiles.mapNotNull { item ->
                val id = item["id"]?.toString() ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "drive_document_$id"
                val mime = item["mimeType"]?.toString() ?: "application/octet-stream"
                val size = item["size"]?.toString()?.toLongOrNull() ?: 0L
                val modified = item["modifiedTime"]?.toString()
                val sha = item["sha256Checksum"]?.toString()
                GoogleDriveRemoteFile(
                    fileId = id,
                    name = name,
                    mimeType = mime,
                    sizeBytes = size,
                    modifiedTime = modified,
                    sha256Checksum = sha
                )
            }
            authStatus = GoogleDriveAuthStatus.AUTHENTICATED
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads a file or exports a Google Workspace document (Docs -> PDF/DOCX, Sheets -> XLSX/CSV)
     * with resumable HTTP Range support and retry after transient network failures.
     */
    suspend fun downloadFileOrWorkspaceExport(
        remoteFile: GoogleDriveRemoteFile,
        destinationDir: File,
        preferredWorkspaceExportMime: String? = null,
        maxRetries: Int = 3,
        baseUrlOverride: String = DRIVE_API_BASE,
        onProgress: (bytesDownloaded: Long) -> Unit = {}
    ): Result<GoogleDriveDownloadOutcome> = withContext(Dispatchers.IO) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            authStatus = GoogleDriveAuthStatus.NOT_CONFIGURED
            return@withContext Result.failure(IllegalStateException("NOT_CONFIGURED: ${getSetupInstructions()}"))
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val isWorkspaceDoc = remoteFile.mimeType == MIME_GOOGLE_DOC
        val isWorkspaceSheet = remoteFile.mimeType == MIME_GOOGLE_SHEET

        val (effectiveMime, extSuffix) = when {
            isWorkspaceDoc -> {
                if (preferredWorkspaceExportMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
                    Pair(preferredWorkspaceExportMime, ".docx")
                } else {
                    Pair("application/pdf", ".pdf")
                }
            }
            isWorkspaceSheet -> {
                if (preferredWorkspaceExportMime == "text/csv") {
                    Pair("text/csv", ".csv")
                } else {
                    Pair("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx")
                }
            }
            else -> Pair(remoteFile.mimeType, "")
        }

        val finalFileName = if (extSuffix.isNotEmpty() && !remoteFile.name.lowercase().endsWith(extSuffix)) {
            "${remoteFile.name}$extSuffix"
        } else {
            remoteFile.name
        }
        val safeName = finalFileName.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF]"), "_")
        val destFile = File(destinationDir, safeName)

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            attempt++
            val existingBytes = if (destFile.exists()) destFile.length() else 0L
            val endpoint = if (isWorkspaceDoc || isWorkspaceSheet) {
                val encodedMime = URLEncoder.encode(effectiveMime, "UTF-8")
                "$baseUrlOverride/${remoteFile.fileId}/export?mimeType=$encodedMime"
            } else {
                "$baseUrlOverride/${remoteFile.fileId}?alt=media"
            }

            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Authorization", "Bearer $token")
                    if (existingBytes > 0L && !isWorkspaceDoc && !isWorkspaceSheet) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    authStatus = GoogleDriveAuthStatus.AUTH_EXPIRED
                    destFile.delete()
                    return@withContext Result.failure(SecurityException("AUTH_EXPIRED: OAuth token expired (HTTP 401)."))
                }
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    authStatus = GoogleDriveAuthStatus.PERMISSION_DENIED
                    destFile.delete()
                    return@withContext Result.failure(SecurityException("PERMISSION_DENIED: Access denied to Drive file (HTTP 403)."))
                }
                if (code !in 200..299 && code != 206) {
                    throw IOException("HTTP $code while downloading ${remoteFile.name}")
                }

                val appendMode = (code == 206 && existingBytes > 0L)
                var totalWritten = if (appendMode) existingBytes else 0L

                FileOutputStream(destFile, appendMode).use { fos ->
                    conn.inputStream.use { ins ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        while (ins.read(buffer).also { read = it } != -1) {
                            totalWritten += read
                            if (totalWritten > DocumentValidationService.MAX_SINGLE_FILE_BYTES) {
                                throw IOException("File exceeds maximum allowed size (${DocumentValidationService.MAX_SINGLE_FILE_BYTES} bytes)")
                            }
                            fos.write(buffer, 0, read)
                            onProgress(totalWritten)
                        }
                    }
                }

                if (totalWritten == 0L || !destFile.exists() || destFile.length() == 0L) {
                    destFile.delete()
                    return@withContext Result.failure(IOException("ZERO_BYTE_DOWNLOAD: 0 bytes transferred from Google Drive"))
                }

                val sha256 = DocumentHashService.computeSha256(destFile)
                return@withContext Result.success(
                    GoogleDriveDownloadOutcome(
                        file = destFile,
                        googleDriveFileId = remoteFile.fileId,
                        originalName = finalFileName,
                        effectiveMimeType = effectiveMime,
                        sizeBytes = destFile.length(),
                        sha256 = sha256,
                        modifiedTime = remoteFile.modifiedTime
                    )
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(200L * attempt)
                }
            } finally {
                conn?.disconnect()
            }
        }

        if (destFile.exists() && destFile.length() == 0L) {
            destFile.delete()
        }
        Result.failure(lastException ?: IOException("Failed to download Google Drive file after $maxRetries attempts"))
    }

    /**
     * Revokes the current OAuth token and disconnects the Drive session.
     */
    suspend fun disconnectAndRevokeToken(revokeEndpointOverride: String = OAUTH_REVOKE_URL): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken
        accessToken = null
        authStatus = GoogleDriveAuthStatus.REVOKED
        if (token.isNullOrBlank()) return@withContext true

        return@withContext try {
            val conn = (URL("$revokeEndpointOverride?token=${URLEncoder.encode(token, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
