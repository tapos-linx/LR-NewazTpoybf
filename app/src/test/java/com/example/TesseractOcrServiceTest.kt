package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.ocr.TessDataManager
import com.example.domain.ocr.TessDataStatus
import com.example.domain.ocr.TesseractOcrService
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TesseractOcrServiceTest {

    private lateinit var context: Context
    private lateinit var tessDataManager: TessDataManager
    private lateinit var ocrService: TesseractOcrService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tessDataManager = TessDataManager(context)
        ocrService = TesseractOcrService(context, tessDataManager)
    }

    @Test
    fun testTessDataManagerPathsAndDefaults() {
        val dir = tessDataManager.tessdataDir
        assertTrue(dir.exists())
        assertEquals("tessdata", dir.name)

        val status = tessDataManager.getStatus()
        assertNotNull(status)
        assertNotNull(status.instructions)
    }

    @Test
    fun testEnsureTrainedDataAvailable() {
        // Without files installed, ensureTrainedDataAvailable returns false
        val available = ocrService.ensureTrainedDataAvailable("ben+eng")
        // Since no files are pre-downloaded in fresh test env:
        assertFalse(available)

        // If we create dummy ben.traineddata
        val benFile = tessDataManager.benFile
        benFile.writeBytes(ByteArray(1024))
        assertTrue(ocrService.ensureTrainedDataAvailable("ben"))

        // Cleanup
        benFile.delete()
    }

    @Test
    fun testRecognizeEmptyFileHandling() {
        runBlocking {
            val emptyFile = File(context.cacheDir, "empty_test.jpg")
            emptyFile.createNewFile()

            val result = ocrService.recognize(emptyFile, "ben+eng")
            assertEquals("", result.rawText)
            assertEquals(0f, result.confidence)

            emptyFile.delete()
        }
    }
}
