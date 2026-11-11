package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.InheritorDao
import com.example.data.local.entity.InheritorEntity
import com.example.data.local.entity.WarishCertificateEntity
import com.example.data.repository.InheritorRepository
import com.example.fixtures.SyntheticTestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InheritorDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: InheritorDao
    private lateinit var repository: InheritorRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.inheritorDao()
        repository = InheritorRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSeedAndPersistSixMasterWarishCertificatesAndAllHeirs() = runBlocking {
        val masterList = SyntheticTestData.createSyntheticMajorWarishList()
        assertEquals(8, masterList.size)

        // Seed master dataset into Room database
        repository.persistMasterCertificatesList(masterList)

        // Verify Certificate count
        val certCount = dao.getCertificateCount()
        assertEquals(8, certCount)

        // Verify Inheritor (Warishan) total count
        val heirCount = dao.getInheritorCount()
        // w1 (8) + w2 (3) + w3 (7) + w4 (12) + w5 (11) + w6 (8) + w7 (17) + w8 (1) = 67 heirs
        assertEquals(67, heirCount)

        // Verify Composite 1-to-many relationship
        val allWithInheritors = dao.getAllCertificatesWithInheritorsOnce()
        assertEquals(8, allWithInheritors.size)

        // Check Certificate 1: Firuz Mia
        val cert1 = allWithInheritors.first { it.certificate.id == "major-warish-1" }
        assertEquals("২০২৬৫৪১১৮১২০০০০০২", cert1.certificate.certificateNo)
        assertEquals("ফিরুজ মিয়া", cert1.certificate.deceasedName)
        assertEquals("মৃত সিরাজ আলী", cert1.certificate.fatherOrHusbandName)
        assertEquals("মৃত আইমনের নেছা", cert1.certificate.motherName)
        assertEquals("গোয়ালী", cert1.certificate.villageOrArea)
        assertEquals(8, cert1.inheritors.size)

        val rafiqul = cert1.inheritors.find { it.name == "রফিকুল ইসলাম" }
        assertNotNull(rafiqul)
        assertEquals("পুত্র", rafiqul?.relationship)
        assertEquals("৭৩০৬০১২৩০৮", rafiqul?.nidOrBirthCert)
        assertEquals("০৬/০৫/১৯৭৪", rafiqul?.dateOfBirth)
        assertEquals("জীবিত", rafiqul?.remarks)
        assertFalse(rafiqul!!.isDeceased)

        // Check Certificate 7: A. Alim (17 heirs)
        val cert7 = allWithInheritors.first { it.certificate.id == "major-warish-7" }
        assertEquals("কাই(দঃ)/ইউপি/২০২৩/০৬", cert7.certificate.certificateNo)
        assertEquals("আঃ আলীম", cert7.certificate.deceasedName)
        assertEquals(17, cert7.inheritors.size)

        // Check Certificate 8: Md. Titu (Cert #465406)
        val cert8 = allWithInheritors.first { it.certificate.id == "major-warish-8" }
        assertEquals("৪৬৫৪০৬", cert8.certificate.certificateNo)
        assertEquals(1, cert8.inheritors.size)
        assertEquals("মোঃ টিটু", cert8.inheritors.first().name)
    }

    @Test
    fun testUpdateInheritorAndCertificateInDatabase() = runBlocking {
        val masterList = SyntheticTestData.createSyntheticMajorWarishList()
        repository.persistMasterCertificatesList(masterList)

        // Query an inheritor from Certificate 1
        val inheritorsForCert1 = dao.getInheritorsForCertificateOnce("major-warish-1")
        val rafiqul = inheritorsForCert1.first { it.name == "রফিকুল ইসলাম" }

        // Update record (e.g. updating remarks or NID)
        val updatedRafiqul = rafiqul.copy(
            remarks = "নথি হালনাগাদকৃত",
            updatedAt = System.currentTimeMillis()
        )
        dao.updateInheritor(updatedRafiqul)

        // Verify persisted update in Room
        val refreshedHeirs = dao.getInheritorsForCertificateOnce("major-warish-1")
        val verifiedRafiqul = refreshedHeirs.first { it.id == rafiqul.id }
        assertEquals("নথি হালনাগাদকৃত", verifiedRafiqul.remarks)

        // Update certificate notes
        val cert = dao.getCertificateById("major-warish-1")!!
        val updatedCert = cert.copy(notes = "পরিমার্জিত ও হালনাগাদকৃত মাস্টার রেকর্ড")
        dao.updateCertificate(updatedCert)

        val verifiedCert = dao.getCertificateById("major-warish-1")
        assertEquals("পরিমার্জিত ও হালনাগাদকৃত মাস্টার রেকর্ড", verifiedCert?.notes)
    }

    @Test
    fun testSearchInheritorsByNameNidAndRelationship() = runBlocking {
        val masterList = SyntheticTestData.createSyntheticMajorWarishList()
        repository.persistMasterCertificatesList(masterList)

        // Search by name
        val zakirResults = dao.searchInheritors("জাকির").first()
        assertTrue(zakirResults.isNotEmpty())
        assertTrue(zakirResults.any { it.name.contains("জাকির") })

        // Search by NID
        val nidResults = dao.searchInheritors("৭৩০৬০১২৩০৮").first()
        assertEquals(1, nidResults.size)
        assertEquals("রফিকুল ইসলাম", nidResults.first().name)

        // Search by relationship
        val wifeResults = dao.searchInheritors("স্ত্রী").first()
        assertTrue(wifeResults.size >= 8) // Wives across the certificates
    }

    @Test
    fun testDeleteCascadeInheritorsWhenCertificateDeleted() = runBlocking {
        val certEntity = WarishCertificateEntity(
            id = "test-cert-temp",
            certificateNo = "৯৯৯৯৯",
            deceasedName = "পরীক্ষামূলক ব্যক্তি",
            villageOrArea = "গোয়ালী"
        )
        val heirEntity = InheritorEntity(
            id = "test-heir-1",
            certificateId = "test-cert-temp",
            certificateNo = "৯৯৯৯৯",
            serialNo = "১",
            name = "পরীক্ষামূলক উত্তরাধিকারী",
            relationship = "পুত্র",
            shareHissa = 1.0,
            shareFractionLabel = "১৬ আনা"
        )

        dao.upsertCertificateWithInheritors(certEntity, listOf(heirEntity))
        assertNotNull(dao.getCertificateById("test-cert-temp"))
        assertEquals(1, dao.getInheritorsForCertificateOnce("test-cert-temp").size)

        // Delete parent certificate
        dao.deleteCertificateById("test-cert-temp")
        assertNull(dao.getCertificateById("test-cert-temp"))
        // Inheritors should cascade delete
        assertEquals(0, dao.getInheritorsForCertificateOnce("test-cert-temp").size)
    }

    @Test
    fun testRepositoryReactiveFlowStreaming() = runBlocking {
        val masterList = SyntheticTestData.createSyntheticMajorWarishList()
        repository.persistMasterCertificatesList(masterList)

        val domainCerts = repository.masterCertificatesFlow.first()
        assertEquals(8, domainCerts.size)
        assertEquals("ফিরুজ মিয়া", domainCerts[0].deceasedName)
        assertEquals("সফর আলী", domainCerts[1].deceasedName)
        assertEquals("সিরাজ আলী", domainCerts[2].deceasedName)
        assertEquals("মোঃ জাহের মিয়া", domainCerts[3].deceasedName)
        assertEquals("আব্দুল কাদির", domainCerts[4].deceasedName)
        assertEquals("নোয়াব মিয়া", domainCerts[5].deceasedName)
        assertEquals("আঃ আলীম", domainCerts[6].deceasedName)
        assertEquals("মৃত মোঃ ফিরুজ মিয়া", domainCerts[7].deceasedName)

        val certByNo = repository.getCertificateByNo("২০২৬১২১৪৮০০০০২১")
        assertNotNull(certByNo)
        assertEquals("আব্দুল কাদির", certByNo?.deceasedName)
        assertEquals(11, certByNo?.heirs?.size)
    }
}
