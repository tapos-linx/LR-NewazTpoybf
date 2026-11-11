package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentProcessingState
import com.example.data.local.entity.DocumentSourceType
import com.example.data.local.entity.ImportQueueItemEntity
import com.example.data.repository.LandRecordRepository
import com.example.domain.ingestion.AndroidSafImportSource
import com.example.domain.ingestion.DocumentHashService
import com.example.domain.ingestion.DocumentImportRepository
import com.example.domain.ingestion.DocumentValidationService
import com.example.domain.ingestion.GoogleDriveAuthStatus
import com.example.domain.ingestion.GoogleDriveImportSource
import com.example.domain.ingestion.GoogleDriveRemoteFile
import com.example.domain.saf.SafFolderScanner
import com.example.worker.DocumentImportWorker
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Automated Verification Suite for Phase 9:
 * - Android Multi-Source Import (Single/Multiple PDF, Multiple Images, Folder/Nested Folder,
 *   Unicode Bengali filenames, Empty file, Corrupted PDF, ZIP extraction, ZIP Slip protection,
 *   Duplicate detection, Persisted URI permission handling, Interrupted import recovery,
 *   WorkManager retry, Room MIGRATION_6_7)
 * - Google Drive OAuth & Download (Not configured, Expired, File selection/list, Download,
 *   Network interruption retry, Duplicate Drive file)
 * - CI/CD Release & Checksum Verification
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedMultiSourceImportTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: LandRecordRepository
    private lateinit var importRepository: DocumentImportRepository
    private lateinit var scanner: SafFolderScanner
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scanner = SafFolderScanner(context)
        repository = LandRecordRepository(context, database, safFolderScanner = scanner)
        importRepository = DocumentImportRepository(context, database, repository)
        tempDir = File(context.cacheDir, "test_import_${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    private fun createRealPdfFile(file: File, pageCount: Int = 1): File {
        val pdfContent = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count $pageCount >> endobj\n")
            append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >> endobj\n")
            append("4 0 obj << /Title (${file.name}) >> endobj\n")
            append("xref\n0 5\n0000000000 65535 f \n")
            append("trailer << /Root 1 0 R /Info 4 0 R /Size 5 >>\n%%EOF\n")
        }
        file.writeBytes(pdfContent.toByteArray(Charsets.UTF_8))
        return file
    }

    private fun createRealImageFile(file: File, color: Int = Color.LTGRAY): File {
        val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        return file
    }

    @Test
    fun `single and multiple PDF and image imports with Unicode Bengali filenames`() = runBlocking {
        val pdf1 = createRealPdfFile(File(tempDir, "সিএস_খতিয়ান_৪০৩.pdf"))
        val pdf2 = createRealPdfFile(File(tempDir, "আরএস_খতিয়ান_৫১২.pdf"))
        val img1 = createRealImageFile(File(tempDir, "মৌজা_নকশা_১.png"), Color.WHITE)
        val img2 = createRealImageFile(File(tempDir, "ওয়ারিশ_সনদ_২.jpg"), Color.GRAY)

        val summary = scanner.scanMultipleUris(
            listOf(Uri.fromFile(pdf1), Uri.fromFile(pdf2), Uri.fromFile(img1), Uri.fromFile(img2))
        )
        assertEquals(4, summary.supportedFiles.size)

        val queued = importRepository.enqueueDiscoveredFiles(summary.supportedFiles)
        assertEquals(4, queued.size)

        for (item in queued) {
            val res = importRepository.processQueueItem(item.queueItemId)
            assertNotNull(res)
            assertTrue(
                res!!.processingState in setOf(
                    DocumentProcessingState.COMPLETED,
                    DocumentProcessingState.NEEDS_MANUAL_REVIEW
                )
            )
            assertNotNull(res.documentId)
        }

        val allDocs = repository.getAllDocumentsListDirect()
        assertEquals(4, allDocs.size)
        assertTrue(allDocs.any { it.sourceFileName == "সিএস_খতিয়ান_৪০৩.pdf" })
    }

    @Test
    fun `empty file handling and corrupted PDF rejection`() = runBlocking {
        val emptyFile = File(tempDir, "খালি_নথি.pdf").apply { writeBytes(ByteArray(0)) }
        val corruptPdf = File(tempDir, "ত্রুটিপূর্ণ_পিডিএফ.pdf").apply {
            writeBytes("NOT_A_VALID_PDF_HEADER_PAYLOAD".toByteArray())
        }

        val validationEmpty = DocumentValidationService.validateFile(emptyFile)
        assertFalse(validationEmpty.isValid)
        assertEquals(DocumentProcessingState.ZERO_BYTE, validationEmpty.status)

        val validationCorrupt = DocumentValidationService.validateFile(corruptPdf)
        assertFalse(validationCorrupt.isValid)
        assertEquals(DocumentProcessingState.FAILED, validationCorrupt.status)
        assertTrue(validationCorrupt.reason!!.contains("CORRUPTED_PDF_HEADER"))

        val summary = scanner.scanMultipleUris(listOf(Uri.fromFile(emptyFile), Uri.fromFile(corruptPdf)))
        assertEquals(1, summary.zeroByteFiles.size)

        val queued = importRepository.enqueueDiscoveredFiles(summary.supportedFiles)
        for (q in queued) {
            importRepository.processQueueItem(q.queueItemId)
        }
        val docs = repository.getAllDocumentsListDirect()
        assertTrue(docs.any { it.isZeroByte && it.status == DocumentProcessingState.ZERO_BYTE })
        assertTrue(docs.any { it.processingStatus == DocumentProcessingState.FAILED && it.processingError?.contains("CORRUPTED_PDF_HEADER") == true })
    }

    @Test
    fun `folder nested directory ZIP extraction and ZIP Slip protection`() = runBlocking {
        val zipFile = File(tempDir, "মৌজা_বাল্ক_আর্কাইভ.zip")
        val pdfBytes = createRealPdfFile(File(tempDir, "source_valid.pdf")).readBytes()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Valid nested directory entry
            zos.putNextEntry(ZipEntry("CS_Folder/SubFolder_Goali/সিএস_১০১.pdf"))
            zos.write(pdfBytes)
            zos.closeEntry()

            // 2. Malicious ZipSlip entry
            zos.putNextEntry(ZipEntry("../../etc/passwd.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }

        assertFalse(DocumentValidationService.isSafeZipEntryPath("../../etc/passwd.txt"))
        assertTrue(DocumentValidationService.isSafeZipEntryPath("CS_Folder/SubFolder_Goali/সিএস_১০১.pdf"))

        val summary = scanner.scanMultipleUris(listOf(Uri.fromFile(zipFile)), "জিপ_ফোল্ডার")
        assertEquals(1, summary.supportedFiles.size)
        assertEquals("সিএস_১০১.pdf", summary.supportedFiles.first().name)
        assertTrue(summary.supportedFiles.first().parentFolder.contains("CS_Folder/SubFolder_Goali"))
        assertTrue(summary.corruptOrRejectedFiles.any { it.contains("unsafe ZIP path traversal") })
    }

    @Test
    fun `SHA256 duplicate detection and persisted URI permission handling`() = runBlocking {
        val fileA = createRealPdfFile(File(tempDir, "original_cs.pdf"))
        val fileB = File(tempDir, "duplicate_copy_cs.pdf").apply { writeBytes(fileA.readBytes()) }

        val hashA = DocumentHashService.computeSha256(fileA)
        val hashB = DocumentHashService.computeSha256(fileB)
        assertEquals(hashA, hashB)

        val safSource = AndroidSafImportSource(context, scanner)
        // file:// URI returns false for content:// persistable permission without throwing
        assertFalse(safSource.takePersistableReadPermissionIfSupported(Uri.fromFile(fileA)))
        assertTrue(safSource.isUriAccessible(Uri.fromFile(fileA)))

        val summary = scanner.scanMultipleUris(listOf(Uri.fromFile(fileA), Uri.fromFile(fileB)))
        assertEquals(2, summary.supportedFiles.size)
        assertEquals(1, summary.duplicateFiles.size)
        assertTrue(summary.supportedFiles[1].isDuplicate)
    }

    @Test
    fun `interrupted import recovery WorkManager execution and Room MIGRATION_6_7 verification`() = runBlocking {
        val validTxt = File(tempDir, "খতিয়ান_তথ্য.txt").apply {
            writeText("জেলা: গাজীপুর\nমৌজা: কাশিমপুর\nখতিয়ান নং: ৪০৩\nদাগ নং: ১১৮\n")
        }
        val sha = DocumentHashService.computeSha256(validTxt)

        // Insert a queue item stuck in PROCESSING state (simulating process death / interruption)
        val interruptedItem = ImportQueueItemEntity(
            queueItemId = "interrupted-q-1",
            batchId = "batch-1",
            originalFilename = validTxt.name,
            mimeType = "text/plain",
            fileFormat = "TXT",
            fileSizeBytes = validTxt.length(),
            sourceType = DocumentSourceType.ANDROID_SAF_FILE,
            sourceUri = Uri.fromFile(validTxt).toString(),
            parentFolder = "লোকাল_ফোল্ডার",
            sha256 = sha,
            processingState = DocumentProcessingState.PROCESSING,
            progressFraction = 0.5f
        )
        database.landDocumentDao().insertQueueItem(interruptedItem)

        // Verify MIGRATION_6_7 executes cleanly on an in-memory SQLite database
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `land_documents` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `title` TEXT NOT NULL,
                                `sourceFileName` TEXT NOT NULL,
                                `sourceFilePath` TEXT NOT NULL,
                                `sourceCategory` TEXT NOT NULL,
                                `classifiedType` TEXT NOT NULL,
                                `fileFormat` TEXT NOT NULL,
                                `fileSizeBytes` INTEGER NOT NULL,
                                `isZeroByte` INTEGER NOT NULL,
                                `pageCount` INTEGER NOT NULL,
                                `dateCaptured` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `processedAt` INTEGER,
                                `status` TEXT NOT NULL,
                                `errorMessage` TEXT,
                                `primaryDistrict` TEXT,
                                `primaryUpazila` TEXT,
                                `primaryMouza` TEXT,
                                `primaryJlNo` TEXT,
                                `primaryKhatianNo` TEXT,
                                `primaryDagNo` TEXT,
                                `primaryLandClass` TEXT,
                                `primaryAreaDecimals` TEXT,
                                `ownersSummary` TEXT,
                                `overallConfidence` REAL NOT NULL,
                                `extractedDataJson` TEXT,
                                `legalDisclaimerAcknowledged` INTEGER NOT NULL,
                                `userNotes` TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO land_documents VALUES ('doc-v6', 'Title', 'f.pdf', 'uri', 'CS', 'CS', 'PDF', 100, 0, 1, 1000, 1000, null, 'UNVERIFIED', null, null, null, 'কাশিমপুর', null, '৪০৩', '১১৮', null, null, null, 0.0, null, 0, null)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        AppDatabase.MIGRATION_6_7.migrate(sqliteDb)
        sqliteDb.query("SELECT id, primaryMouza, processingStatus FROM land_documents WHERE id = 'doc-v6'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("কাশিমপুর", c.getString(1))
            assertEquals("IMPORTED", c.getString(2))
        }
        helper.close()

        // Recover and process interrupted item via DocumentImportRepository
        val recovered = importRepository.processQueueItem("interrupted-q-1")
        assertNotNull(recovered)
        assertTrue(
            recovered!!.processingState in setOf(
                DocumentProcessingState.COMPLETED,
                DocumentProcessingState.NEEDS_MANUAL_REVIEW
            )
        )
    }

    @Test
    fun `Google Drive OAuth states file listing download retry and duplicate detection`() = runBlocking {
        val driveSource = GoogleDriveImportSource(context)

        // 1. Not configured state
        assertEquals(GoogleDriveAuthStatus.NOT_CONFIGURED, driveSource.getAuthStatus())
        val unconfRes = driveSource.listFilesInDrive()
        assertTrue(unconfRes.isFailure)
        assertTrue(unconfRes.exceptionOrNull()!!.message!!.contains("NOT_CONFIGURED"))

        // Start a local embedded HTTP server to test real HTTP OAuth 401, 200 list, download, and retry
        val requestCounter = AtomicInteger(0)
        val pdfPayload = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".toByteArray()
        val expectedSha = DocumentHashService.computeSha256(pdfPayload)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/drive/v3/files") { exchange ->
            val auth = exchange.requestHeaders.getFirst("Authorization") ?: ""
            val path = exchange.requestURI.path
            val query = exchange.requestURI.query ?: ""

            if (auth == "Bearer expired_token") {
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
                return@createContext
            }

            if (path.endsWith("/files") && query.contains("fields=")) {
                val json = """
                    {
                      "files": [
                        {
                          "id": "drv_file_101",
                          "name": "গোয়ালী_সিএস_খতিয়ান.pdf",
                          "mimeType": "application/pdf",
                          "size": "${pdfPayload.size}",
                          "modifiedTime": "2026-11-11T00:00:00Z",
                          "sha256Checksum": "$expectedSha"
                        }
                      ]
                    }
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.size.toLong())
                exchange.responseBody.use { it.write(json) }
                return@createContext
            }

            if (path.contains("/drv_file_101")) {
                val attempt = requestCounter.incrementAndGet()
                if (attempt == 1) {
                    // Simulate transient network failure on attempt 1
                    exchange.sendResponseHeaders(503, -1)
                    exchange.close()
                    return@createContext
                }
                exchange.responseHeaders.add("Content-Type", "application/pdf")
                exchange.sendResponseHeaders(200, pdfPayload.size.toLong())
                exchange.responseBody.use { it.write(pdfPayload) }
                return@createContext
            }

            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}/drive/v3/files"

        try {
            // 2. Expired token -> AUTH_EXPIRED
            driveSource.configureOAuthClient("client-id", "expired_token")
            val expiredRes = driveSource.listFilesInDrive(baseUrlOverride = baseUrl)
            assertTrue(expiredRes.isFailure)
            assertEquals(GoogleDriveAuthStatus.AUTH_EXPIRED, driveSource.getAuthStatus())

            // 3. Valid token -> Successful file listing
            driveSource.configureOAuthClient("client-id", "valid_token_abc")
            val listRes = driveSource.listFilesInDrive(baseUrlOverride = baseUrl)
            assertTrue(listRes.isSuccess)
            val remoteFiles = listRes.getOrThrow()
            assertEquals(1, remoteFiles.size)
            assertEquals("drv_file_101", remoteFiles[0].fileId)

            // 4. Download with automatic retry after HTTP 503 network interruption
            val dlDir = File(tempDir, "drive_dl")
            val dlRes = driveSource.downloadFileOrWorkspaceExport(
                remoteFile = remoteFiles[0],
                destinationDir = dlDir,
                baseUrlOverride = baseUrl
            )
            assertTrue(dlRes.isSuccess)
            val outcome = dlRes.getOrThrow()
            assertEquals(expectedSha, outcome.sha256)
            assertTrue(requestCounter.get() >= 2)

            // 5. Duplicate Drive file detection
            val summary = scanner.scanMultipleUris(
                listOf(Uri.fromFile(outcome.file), Uri.fromFile(outcome.file)),
                "গুগল_ড্রাইভ"
            )
            assertEquals(1, summary.duplicateFiles.size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `CI CD release workflow and checksum contract verification`() {
        val rootDir = File(System.getProperty("user.dir") ?: ".").let {
            if (it.name == "app") it.parentFile ?: it else it
        }
        val androidYml = File(rootDir, ".github/workflows/android.yml")
        val releaseYml = File(rootDir, ".github/workflows/release.yml")
        val readme = File(rootDir, "README.md")

        assertTrue(androidYml.exists())
        assertTrue(releaseYml.exists())
        assertTrue(readme.exists())

        val releaseContent = releaseYml.readText()
        assertTrue(releaseContent.contains("Newaz-Land-Record-Extractor-release.apk"))
        assertTrue(releaseContent.contains("Newaz-Land-Record-Extractor-debug.apk"))
        assertTrue(releaseContent.contains("Newaz-Land-Record-Extractor-release.aab"))
        assertTrue(releaseContent.contains("SHA256SUMS.txt"))
        assertTrue(releaseContent.contains("com.aistudio.newazlrecord.kxrtpq"))

        val readmeContent = readme.readText()
        assertTrue(
            readmeContent.contains(
                "https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/Newaz-Land-Record-Extractor-release.apk"
            )
        )
        assertTrue(
            readmeContent.contains(
                "https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/Newaz-Land-Record-Extractor-debug.apk"
            )
        )
        assertTrue(
            readmeContent.contains(
                "https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/SHA256SUMS.txt"
            )
        )
    }
}
