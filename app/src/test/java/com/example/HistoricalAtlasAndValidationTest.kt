package com.example

import com.example.domain.atlas.AtlasTimelineEngine
import com.example.domain.atlas.SurveyEra
import com.example.domain.matcher.EvidenceTier
import com.example.domain.validation.ForensicValidationEngine
import com.example.domain.validation.IssueSeverity
import com.example.domain.validation.ValidationIssueCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.example.data.local.entity.LandDocumentEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoricalAtlasAndValidationTest {

    private val atlasEngine = AtlasTimelineEngine()
    private val validationEngine = ForensicValidationEngine()

    @Test
    fun testAtlasTimelineReconstruction() {
        val testRecords = listOf(
            LandDocumentEntity(
                id = "doc-cs",
                title = "CS Khatian 101",
                sourceFileName = "cs.pdf",
                sourceFilePath = "/docs/cs.pdf",
                sourceCategory = "CS",
                classifiedType = "CS",
                fileFormat = "PDF",
                primaryMouza = "দিলকুশা",
                primaryDagNo = "৫০১",
                primaryKhatianNo = "১০১",
                primaryAreaDecimals = "50.0"
            ),
            LandDocumentEntity(
                id = "doc-sa",
                title = "SA Khatian 102",
                sourceFileName = "sa.pdf",
                sourceFilePath = "/docs/sa.pdf",
                sourceCategory = "SA",
                classifiedType = "SA",
                fileFormat = "PDF",
                primaryMouza = "দিলকুশা",
                primaryDagNo = "৫০১",
                primaryKhatianNo = "১০২",
                primaryAreaDecimals = "50.0"
            ),
            LandDocumentEntity(
                id = "doc-rs",
                title = "RS Khatian 103",
                sourceFileName = "rs.pdf",
                sourceFilePath = "/docs/rs.pdf",
                sourceCategory = "RS",
                classifiedType = "RS",
                fileFormat = "PDF",
                primaryMouza = "দিলকুশা",
                primaryDagNo = "৫০১",
                primaryKhatianNo = "১০৩",
                primaryAreaDecimals = "50.0"
            ),
            LandDocumentEntity(
                id = "doc-bs",
                title = "BS Khatian 104",
                sourceFileName = "bs.pdf",
                sourceFilePath = "/docs/bs.pdf",
                sourceCategory = "BS",
                classifiedType = "BS",
                fileFormat = "PDF",
                primaryMouza = "দিলকুশা",
                primaryDagNo = "৫০১",
                primaryKhatianNo = "১০৪",
                primaryAreaDecimals = "50.0"
            )
        )
        val timeline = atlasEngine.buildHistoricalTimeline("দিলকুশা", "৫০১", testRecords)
        assertNotNull(timeline)
        assertEquals(4, timeline.size)
        assertEquals(SurveyEra.CS, timeline[0].era)
        assertEquals(SurveyEra.SA, timeline[1].era)
        assertEquals(SurveyEra.RS, timeline[2].era)
        assertEquals(SurveyEra.BS, timeline[3].era)

        // Verify anti-hallucination rule: empty records = empty timeline
        val emptyTimeline = atlasEngine.buildHistoricalTimeline("দিলকুশা", "৫০১", emptyList())
        assertTrue(emptyTimeline.isEmpty())
    }

    @Test
    fun testDagSplitAreaDetectionBalanced() {
        val split = atlasEngine.detectDagSplit(
            parentDag = "৪১২",
            parentArea = 50.0,
            childDags = listOf("৮১৫/১" to 25.0, "৮১৫/২" to 25.0)
        )
        assertTrue("Equal split must be balanced", split.isAreaBalanced)
        assertEquals(0.0, split.discrepancyDecimals, 0.01)
        assertEquals(2, split.childDags.size)
    }

    @Test
    fun testDagSplitAreaDetectionDiscrepancy() {
        val split = atlasEngine.detectDagSplit(
            parentDag = "৪১২",
            parentArea = 50.0,
            childDags = listOf("৮১৫/১" to 20.0, "৮১৫/২" to 15.0)
        )
        assertFalse("Lost area must be flagged as unbalanced", split.isAreaBalanced)
        assertEquals(15.0, split.discrepancyDecimals, 0.01)
        assertTrue(split.remarks.contains("ঘাটতি"))
    }

    @Test
    fun testBengaliNameNormalization() {
        val norm1 = validationEngine.normalizeName("মোঃ রফিকুল ইসলাম")
        val norm2 = validationEngine.normalizeName("মোহাম্মদ রফিকুল ইসলাম")
        val norm3 = validationEngine.normalizeName("মুহম্মদ রফিকুল ইসলাম")

        assertEquals("রফিকুল ইসলাম", norm1)
        assertEquals("রফিকুল ইসলাম", norm2)
        assertEquals("রফিকুল ইসলাম", norm3)
    }

    @Test
    fun testAreaUnitConversion() {
        val acreToDec = validationEngine.convertToDecimals(1.5, "acre")
        assertEquals(150.0, acreToDec, 0.01)

        val bighaToDec = validationEngine.convertToDecimals(2.0, "bigha")
        assertEquals(66.0, bighaToDec, 0.01)
    }

    @Test
    fun testForensicValidationVerified() {
        val report = validationEngine.validateRecords(
            csOwner = "হাজী আব্দুল করিম",
            csFather = "রহমত আলী",
            rsOwner = "হাজী আব্দুল করিম",
            rsFather = "রহমত আলী",
            csAreaDecimals = 32.0,
            rsAreaDecimals = 32.0,
            hasSaRecord = true,
            totalHissa = 1.000
        )

        assertEquals(EvidenceTier.VERIFIED, report.overallTier)
        assertEquals(0.98f, report.confidenceScore, 0.05f)
        assertEquals(0, report.criticalCount)
    }

    @Test
    fun testForensicValidationFatherMismatchAndGap() {
        val report = validationEngine.validateRecords(
            csOwner = "হাজী আব্দুল করিম",
            csFather = "রহমত আলী",
            rsOwner = "হাজী আব্দুল করিম",
            rsFather = "কেরামত আলী শেখ", // Mismatch
            csAreaDecimals = 32.0,
            rsAreaDecimals = 32.0,
            hasSaRecord = false, // Record Gap
            totalHissa = 1.000
        )

        assertEquals(EvidenceTier.CONTRADICTORY, report.overallTier)
        assertTrue(report.issues.any { it.category == ValidationIssueCategory.FATHER_MISMATCH })
        assertTrue(report.issues.any { it.category == ValidationIssueCategory.CHAIN_DISCONTINUITY })
    }
}
