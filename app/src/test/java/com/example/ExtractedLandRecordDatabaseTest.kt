package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.ExtractedLandRecordDao
import com.example.data.local.entity.DeedPartyEntity
import com.example.data.local.entity.DeedPlotScheduleEntity
import com.example.data.local.entity.DeedRecordEntity
import com.example.data.local.entity.KhatianEntity
import com.example.data.local.entity.KhatianOwnerEntity
import com.example.data.local.entity.KhatianPlotEntity
import com.example.data.local.entity.MutationPartyEntity
import com.example.data.local.entity.MutationPlotEntity
import com.example.data.local.entity.MutationRecordEntity
import com.example.data.repository.ExtractedLandRecordRepository
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
class ExtractedLandRecordDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ExtractedLandRecordDao
    private lateinit var repository: ExtractedLandRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.extractedLandRecordDao()
        repository = ExtractedLandRecordRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSeedInitialExtractedRecordsMasterDataset() = runBlocking {
        repository.seedInitialExtractedRecordsIfEmpty()

        // 1. Verify Khatians: CS, SA, RS, BRS
        val khatians = repository.allKhatians.first()
        assertEquals(4, khatians.size)

        val csKhatian = repository.getKhatiansBySurveyType("CS").first()
        assertEquals(1, csKhatian.size)
        assertEquals("৪০৩", csKhatian[0].khatian.khatianNo)
        assertEquals("গোয়ালী", csKhatian[0].khatian.mouza)
        assertEquals(2, csKhatian[0].owners.size)
        assertEquals(1, csKhatian[0].plots.size)
        assertEquals("১১৮", csKhatian[0].plots[0].dagNo)

        val saKhatian = repository.getKhatiansBySurveyType("SA").first()
        assertEquals(1, saKhatian.size)
        assertEquals("সিরাজ আলী", saKhatian[0].owners[0].ownerName)

        val rsKhatian = repository.getKhatiansBySurveyType("RS").first()
        assertEquals(1, rsKhatian.size)
        assertEquals("৫২১", rsKhatian[0].khatian.khatianNo)
        assertEquals("২৫৪", rsKhatian[0].plots[0].dagNo)

        val brsKhatian = repository.getKhatiansBySurveyType("BRS_BS").first()
        assertEquals(1, brsKhatian.size)
        assertEquals("২৪৭", brsKhatian[0].khatian.khatianNo)
        assertEquals("৬৭২", brsKhatian[0].plots[0].dagNo)

        // 2. Verify Registered Deed (দলিল)
        val deeds = repository.allDeeds.first()
        assertEquals(1, deeds.size)
        val deed = deeds[0]
        assertEquals("৪৫২১", deed.deed.deedNo)
        assertEquals(2023, deed.deed.deedYear)
        assertEquals("SAF_KABALA", deed.deed.deedType)
        assertEquals(2, deed.parties.size)
        assertEquals("SELLER", deed.parties[0].partyRole)
        assertEquals("BUYER", deed.parties[1].partyRole)
        assertEquals(1, deed.plotSchedules.size)
        assertEquals("৬৭২", deed.plotSchedules[0].dagNo)
        assertEquals(25.0, deed.plotSchedules[0].transferredAreaDecimals, 0.001)

        // 3. Verify Upazila Mutation (নামজারি)
        val mutations = repository.allMutations.first()
        assertEquals(1, mutations.size)
        val mutation = mutations[0]
        assertEquals("১২৩৪/২০২৪-২৫", mutation.mutation.caseNumber)
        assertEquals("১০৮৯", mutation.mutation.proposedKhatianNo)
        assertEquals("APPROVED", mutation.mutation.mutationStatus)
        assertEquals(2, mutation.parties.size)
        assertEquals("APPLICANT", mutation.parties[0].partyRole)
        assertEquals(1, mutation.plots.size)
        assertEquals(25.0, mutation.plots[0].mutatedAreaDecimals, 0.001)

        // 4. Verify Title Chain Links (CS ➔ SA ➔ RS ➔ BRS ➔ Deed ➔ Mutation)
        val chainLinks = repository.getChainLinksByMouza("গোয়ালী").first()
        assertEquals(5, chainLinks.size)
        assertTrue(chainLinks.any { it.fromSurvey == "CS" && it.toSurvey == "SA" })
        assertTrue(chainLinks.any { it.fromSurvey == "SA" && it.toSurvey == "RS" })
        assertTrue(chainLinks.any { it.fromSurvey == "RS" && it.toSurvey == "BRS" })
        assertTrue(chainLinks.any { it.fromSurvey == "BRS" && it.toSurvey == "DEED" })
        assertTrue(chainLinks.any { it.fromSurvey == "DEED" && it.toSurvey == "MUTATION" })
    }

    @Test
    fun testInsertAndSearchCustomKhatian() = runBlocking {
        val customKhatian = KhatianEntity(
            id = "custom-rs-999",
            surveyType = "RS",
            khatianNo = "৯৯৯",
            district = "কুমিল্লা",
            upazila = "মুরাদনগর",
            mouza = "শ্রীকাইল",
            jlNo = "৪৫",
            isVerified = true
        )
        val owners = listOf(
            KhatianOwnerEntity(
                id = "custom-o1",
                khatianId = customKhatian.id,
                ownerName = "মোঃ মোস্তফা কামাল",
                fatherOrHusbandName = "মৃত রমিজ উদ্দিন",
                hissaDecimal = 1.0
            )
        )
        val plots = listOf(
            KhatianPlotEntity(
                id = "custom-p1",
                khatianId = customKhatian.id,
                dagNo = "৫৫৫",
                landClass = "ভিটি",
                totalPlotAreaDecimals = 50.0,
                khatianShareAreaDecimals = 50.0
            )
        )

        repository.saveFullKhatian(customKhatian, owners, plots)

        val searchResult = repository.searchKhatians("মোস্তফা").first()
        assertEquals(1, searchResult.size)
        assertEquals("৯৯৯", searchResult[0].khatian.khatianNo)
        assertEquals("শ্রীকাইল", searchResult[0].khatian.mouza)
        assertEquals(1, searchResult[0].owners.size)
        assertEquals("মোঃ মোস্তফা কামাল", searchResult[0].owners[0].ownerName)
    }

    @Test
    fun testCascadingDeleteKhatian() = runBlocking {
        val khatian = KhatianEntity(
            id = "test-del-khatian",
            surveyType = "CS",
            khatianNo = "১০০",
            district = "ঢাকা",
            upazila = "সাভার",
            mouza = "আশুলিয়া",
            jlNo = "১০"
        )
        val owners = listOf(
            KhatianOwnerEntity(
                id = "test-del-owner",
                khatianId = khatian.id,
                ownerName = "পরীক্ষামূলক মালিক"
            )
        )
        val plots = listOf(
            KhatianPlotEntity(
                id = "test-del-plot",
                khatianId = khatian.id,
                dagNo = "২০০"
            )
        )

        repository.saveFullKhatian(khatian, owners, plots)
        assertNotNull(repository.getKhatianDetailsByIdOnce(khatian.id))

        // Delete khatian
        repository.deleteKhatian(khatian.id)

        // Verify null and cascaded removal
        val afterDel = repository.getKhatianDetailsByIdOnce(khatian.id)
        assertEquals(null, afterDel)
    }
}
