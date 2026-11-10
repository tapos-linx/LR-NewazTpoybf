package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.LandDocumentDao
import com.example.data.local.entity.LandDocumentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LandDocumentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LandDocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.landDocumentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveByDocumentTypeOwnerAndDateCaptured() = runBlocking {
        val captureTime = System.currentTimeMillis()

        val doc1 = LandDocumentEntity(
            id = "doc-1",
            title = "সি এস খতিয়ান ৪০৩",
            sourceFileName = "cs_403.pdf",
            sourceFilePath = "/docs/cs_403.pdf",
            sourceCategory = "CS",
            classifiedType = "CS",
            fileFormat = "PDF",
            dateCaptured = captureTime - 10000,
            primaryDistrict = "ঢাকা",
            primaryUpazila = "কেরানীগঞ্জ",
            primaryKhatianNo = "৪০৩",
            primaryDagNo = "১২৫০",
            ownersSummary = "মোহাম্মদ আবদুর রহমান"
        )

        val doc2 = LandDocumentEntity(
            id = "doc-2",
            title = "আর এস খতিয়ান ১০৭",
            sourceFileName = "rs_107.jpg",
            sourceFilePath = "/docs/rs_107.jpg",
            sourceCategory = "RS",
            classifiedType = "RS",
            fileFormat = "JPG",
            dateCaptured = captureTime,
            primaryDistrict = "ঢাকা",
            primaryUpazila = "কেরানীগঞ্জ",
            primaryKhatianNo = "১০৭",
            primaryDagNo = "৭৫০",
            ownersSummary = "ফাতেমা বেগম ও আবদুল জলিল"
        )

        dao.insertDocuments(listOf(doc1, doc2))

        // Query by Document Type
        val csDocs = dao.getDocumentsByType("CS").first()
        assertEquals(1, csDocs.size)
        assertEquals("CS", csDocs[0].classifiedType)
        assertEquals("মোহাম্মদ আবদুর রহমান", csDocs[0].ownersSummary)

        // Query by Owner Name
        val fatemaDocs = dao.getDocumentsByOwner("ফাতেমা বেগম").first()
        assertEquals(1, fatemaDocs.size)
        assertEquals("doc-2", fatemaDocs[0].id)
        assertEquals("RS", fatemaDocs[0].classifiedType)

        // Query by Date Captured Range
        val rangeDocs = dao.getDocumentsByDateCapturedRange(captureTime - 20000, captureTime + 5000).first()
        assertEquals(2, rangeDocs.size)

        // Query by Type and Owner
        val typedOwnerDocs = dao.getDocumentsByTypeAndOwner("CS", "আবদুর রহমান").first()
        assertEquals(1, typedOwnerDocs.size)
        assertEquals("doc-1", typedOwnerDocs[0].id)
    }

    @Test
    fun testRoomMigrationsPreservePopulatedData() = runBlocking {
        // Insert populated document and verify all explicit migrations (1->2->3->4->5->6) execute safely
        val doc = LandDocumentEntity(
            id = "migrated-doc-900",
            title = "বি আর এস খতিয়ান ৯০০",
            sourceFileName = "brs_900.pdf",
            sourceFilePath = "/docs/brs_900.pdf",
            sourceCategory = "BRS",
            classifiedType = "BRS",
            fileFormat = "PDF",
            dateCaptured = 1710000000000L,
            primaryDistrict = "ঢাকা",
            primaryUpazila = "সাভার",
            primaryKhatianNo = "৯০০",
            primaryDagNo = "১৮২০",
            ownersSummary = "মোঃ রফিকুল ইসলাম"
        )
        dao.insertDocument(doc)

        // Execute the exact migration SQL statements against the underlying SupportSQLiteDatabase
        val sqliteDb = database.openHelper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(sqliteDb)
        AppDatabase.MIGRATION_2_3.migrate(sqliteDb)
        AppDatabase.MIGRATION_3_4.migrate(sqliteDb)
        AppDatabase.MIGRATION_4_5.migrate(sqliteDb)
        AppDatabase.MIGRATION_5_6.migrate(sqliteDb)

        // Verify existing populated record survived all migrations intact
        val survived = dao.getDocumentByIdOnce("migrated-doc-900")
        assertNotNull(survived)
        assertEquals("বি আর এস খতিয়ান ৯০০", survived?.title)
        assertEquals("৯০০", survived?.primaryKhatianNo)
        assertEquals("১৮২০", survived?.primaryDagNo)
        assertEquals("মোঃ রফিকুল ইসলাম", survived?.ownersSummary)
    }
}


