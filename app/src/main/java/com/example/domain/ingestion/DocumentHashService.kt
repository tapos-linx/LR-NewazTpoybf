package com.example.domain.ingestion

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Forensic SHA-256 Hashing & Content Integrity Verification Service (Phase 2 & Phase 5).
 */
object DocumentHashService {

    private const val STREAM_BUFFER_BYTES = 16384

    fun computeSha256(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(file: File): String {
        if (!file.exists() || !file.isFile || file.length() == 0L) return ""
        return try {
            FileInputStream(file).use { computeSha256FromStream(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun computeSha256(context: Context, uri: Uri): String {
        return try {
            val stream: InputStream? = if (uri.scheme == "file") {
                val f = File(uri.path ?: return "")
                if (f.exists() && f.length() > 0L) FileInputStream(f) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { computeSha256FromStream(it) } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun computeSha256FromStream(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var totalRead = 0L
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            if (read > 0) {
                digest.update(buffer, 0, read)
                totalRead += read
            }
        }
        return if (totalRead == 0L) "" else digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        val actual = computeSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
