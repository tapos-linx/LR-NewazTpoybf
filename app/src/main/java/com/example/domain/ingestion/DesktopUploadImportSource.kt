package com.example.domain.ingestion

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class DesktopPairingSession(
    val pairingToken: String,
    val pairingCode: String,
    val companionUploadUrl: String,
    val expiresAtEpochSeconds: Long
)

data class DesktopUploadedDocumentMeta(
    val documentId: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val documentType: String,
    val status: String
)

/**
 * Desktop / Web Companion Import Source (Phase 2B).
 * Connects the native Android app with the FastAPI Desktop/Web Companion upload server
 * via short-lived pairing tokens (`/api/v1/upload/pair`, `/api/v1/upload/companion/session/{token}`,
 * `/api/v1/upload/files/{id}/download`), downloading real uploaded files and verifying SHA-256 checksums.
 */
class DesktopUploadImportSource {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    suspend fun createPairingSession(
        backendBaseUrl: String,
        apiKey: String? = null
    ): Result<DesktopPairingSession> = withContext(Dispatchers.IO) {
        val cleanBase = backendBaseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Backend server URL is required"))
        }

        val url = URL("$cleanBase/api/v1/upload/pair")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("X-API-Key", apiKey)
            }
            doOutput = true
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("Failed to create pairing session (HTTP $code)"))
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val data = moshi.adapter<Map<String, Any>>(mapType).fromJson(body).orEmpty()

            val token = data["pairing_token"]?.toString().orEmpty()
            val pairingCode = data["pairing_code"]?.toString().orEmpty()
            val expiresAt = (data["expires_at"] as? Number)?.toLong() ?: 0L
            if (token.isBlank()) {
                return@withContext Result.failure(IOException("Invalid pairing response from server"))
            }

            Result.success(
                DesktopPairingSession(
                    pairingToken = token,
                    pairingCode = pairingCode,
                    companionUploadUrl = "$cleanBase/api/v1/upload/companion?token=$token",
                    expiresAtEpochSeconds = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchPairedSessionUploads(
        backendBaseUrl: String,
        pairingToken: String,
        apiKey: String? = null
    ): Result<List<DesktopUploadedDocumentMeta>> = withContext(Dispatchers.IO) {
        val cleanBase = backendBaseUrl.trim().trimEnd('/')
        val url = URL("$cleanBase/api/v1/upload/companion/session/$pairingToken")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("X-API-Key", apiKey)
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("Pairing session check failed (HTTP $code)"))
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val data = moshi.adapter<Map<String, Any>>(mapType).fromJson(body).orEmpty()
            val rawFiles = (data["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

            val docs = rawFiles.mapNotNull { item ->
                val id = item["id"]?.toString() ?: return@mapNotNull null
                val fname = item["filename"]?.toString() ?: return@mapNotNull null
                val size = (item["size_bytes"] as? Number)?.toLong() ?: 0L
                val sha = item["sha256"]?.toString().orEmpty()
                val docType = item["document_type"]?.toString() ?: "UNCLASSIFIED_RECORD"
                val status = item["status"]?.toString() ?: "QUEUED"
                if (size <= 0L) return@mapNotNull null
                DesktopUploadedDocumentMeta(
                    documentId = id,
                    filename = fname,
                    sizeBytes = size,
                    sha256 = sha,
                    documentType = docType,
                    status = status
                )
            }
            Result.success(docs)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun downloadCompanionDocument(
        backendBaseUrl: String,
        docMeta: DesktopUploadedDocumentMeta,
        destinationDir: File,
        pairingToken: String? = null,
        apiKey: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanBase = backendBaseUrl.trim().trimEnd('/')
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val safeName = docMeta.filename.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF]"), "_")
        val destFile = File(destinationDir, safeName)
        val url = URL("$cleanBase/api/v1/upload/files/${docMeta.documentId}/download")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            if (!pairingToken.isNullOrBlank()) {
                setRequestProperty("X-Pairing-Token", pairingToken)
            }
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("X-API-Key", apiKey)
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("Failed to download companion document (HTTP $code)"))
            }

            var bytesWritten = 0L
            FileOutputStream(destFile).use { fos ->
                conn.inputStream.use { ins ->
                    val buf = ByteArray(16384)
                    var r: Int
                    while (ins.read(buf).also { r = it } != -1) {
                        bytesWritten += r
                        if (bytesWritten > DocumentValidationService.MAX_SINGLE_FILE_BYTES) {
                            throw IOException("Downloaded file exceeds maximum size limit")
                        }
                        fos.write(buf, 0, r)
                    }
                }
            }

            if (bytesWritten == 0L || !destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return@withContext Result.failure(IOException("ZERO_BYTES_TRANSFERRED: Rejecting empty companion transfer"))
            }

            if (docMeta.sha256.isNotBlank() && !DocumentHashService.verifyChecksum(destFile, docMeta.sha256)) {
                destFile.delete()
                return@withContext Result.failure(IOException("SHA256_MISMATCH: Downloaded file checksum does not match server SHA-256"))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }
}
