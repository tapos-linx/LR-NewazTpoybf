package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentPageEntity
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.LandRecordRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LandRecordRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LandRecordRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LandRecordRepository(
            context = context,
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRepositoryStoreAndRetrieveMetadata() = runBlocking {
        val captureTime = System.currentTimeMillis()

        val sampleDoc = LandDocumentEntity(
            id = "repo-doc-1",
            title = "বি আর এস খতিয়ান ৫৫০",
            sourceFileName = "brs_550.pdf",
            sourceFilePath = "/docs/brs_550.pdf",
            sourceCategory = "BRS",
            classifiedType = "BRS",
            fileFormat = "PDF",
            dateCaptured = captureTime,
            primaryDistrict = "কুমিল্লা",
            primaryUpazila = "চান্দিনা",
            primaryMouza = "মাইজখার",
            primaryKhatianNo = "৫৫০",
            primaryDagNo = "২০১",
            ownersSummary = "মোঃ রফিকুল ইসলাম"
        )

        val samplePage = DocumentPageEntity(
            id = "page-1",
            documentId = "repo-doc-1",
            pageIndex = 1,
            rawImagePath = "/path/p1.png",
            rawOcrText = "বি আর এস খতিয়ান ৫৫০",
            ocrConfidence = 91.5f
        )

        // Store via repository
        repository.insertDocument(sampleDoc)
        repository.insertPages(listOf(samplePage))

        // Retrieve once
        val retrieved = repository.getDocumentOnce("repo-doc-1")
        assertNotNull(retrieved)
        assertEquals("বি আর এস খতিয়ান ৫৫০", retrieved?.title)
        assertEquals("BRS", retrieved?.classifiedType)
        assertEquals("মোঃ রফিকুল ইসলাম", retrieved?.ownersSummary)
        assertEquals(captureTime, retrieved?.dateCaptured)

        // Query by Document Type
        val brsList = repository.getDocumentsByType("BRS").first()
        assertEquals(1, brsList.size)
        assertEquals("repo-doc-1", brsList[0].id)

        // Query by Owner Name
        val ownerList = repository.getDocumentsByOwner("রফিকুল").first()
        assertEquals(1, ownerList.size)
        assertEquals("repo-doc-1", ownerList[0].id)

        // Query by Date Captured Range
        val dateList = repository.getDocumentsByDateCapturedRange(captureTime - 1000, captureTime + 1000).first()
        assertEquals(1, dateList.size)

        // Pages check
        val pages = repository.getPagesOnce("repo-doc-1")
        assertEquals(1, pages.size)
        assertEquals("বি আর এস খতিয়ান ৫৫০", pages[0].rawOcrText)

        // Delete check
        repository.deleteDocument("repo-doc-1")
        val deleted = repository.getDocumentOnce("repo-doc-1")
        assertNull(deleted)
    }
}
