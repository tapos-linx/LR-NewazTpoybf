package com.example

import com.example.data.local.entity.LandDocumentEntity
import com.example.domain.matcher.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WarishMatcherTest {

    private val matcher = InheritanceMatcher()

    @Test
    fun testHissaMathematicalBalanceVerification() {
        val balancedWarish = WarishCertificate(
            certificateNo = "WAR-01",
            deceasedName = "আব্দুল করিম",
            heirs = listOf(
                HeirRecord(name = "স্ত্রী", relationship = "স্ত্রী", shareHissa = 0.125),
                HeirRecord(name = "পুত্র ১", relationship = "পুত্র", shareHissa = 0.350),
                HeirRecord(name = "পুত্র ২", relationship = "পুত্র", shareHissa = 0.350),
                HeirRecord(name = "কন্যা", relationship = "কন্যা", shareHissa = 0.175)
            )
        )

        assertTrue(balancedWarish.isHissaBalanced)
        assertEquals(1.0, balancedWarish.totalShareSum, 0.001)

        val report = matcher.matchWarishToSurveys(balancedWarish, emptyList())
        assertTrue(report.isHissaMathematicallyBalanced)
        assertTrue(report.globalDiscrepancies.isEmpty())
    }

    @Test
    fun testHissaDiscrepancyAndContradictoryDetection() {
        // Exceeding 1.0 (e.g. 1.200)
        val unbalancedWarish = WarishCertificate(
            certificateNo = "WAR-02",
            deceasedName = "রফিক উল্লাহ",
            heirs = listOf(
                HeirRecord(name = "ওয়ারিশ ১", relationship = "পুত্র", shareHissa = 0.600),
                HeirRecord(name = "ওয়ারিশ ২", relationship = "পুত্র", shareHissa = 0.600)
            )
        )

        assertFalse(unbalancedWarish.isHissaBalanced)
        assertEquals(1.200, unbalancedWarish.totalShareSum, 0.001)

        val report = matcher.matchWarishToSurveys(unbalancedWarish, emptyList())
        assertFalse(report.isHissaMathematicallyBalanced)
        assertEquals(EvidenceTier.CONTRADICTORY, report.overallEvidenceTier)
        assertTrue(report.globalDiscrepancies.any { it.contains("গুরুতর গরমিল") })
    }

    @Test
    fun testRecordGapDetectionWhenIntermediateSurveyMissing() {
        val warish = WarishCertificate(
            certificateNo = "WAR-03",
            deceasedName = "কাসেম আলী",
            targetDagNumbers = listOf("১০৫"),
            heirs = listOf(
                HeirRecord(name = "পুত্র", relationship = "পুত্র", shareHissa = 1.0)
            )
        )

        // CS exists, RS exists, but SA is missing for Dag 105
        val records = listOf(
            LandDocumentEntity(
                id = "doc-cs",
                title = "CS Porcha",
                sourceFileName = "cs_105.pdf",
                sourceFilePath = "/storage/cs_105.pdf",
                sourceCategory = "CS",
                classifiedType = "CS",
                fileFormat = "PDF",
                primaryDagNo = "১০৫",
                primaryKhatianNo = "৫০",
                primaryAreaDecimals = "৪৫.০",
                ownersSummary = "কাসেম আলী"
            ),
            LandDocumentEntity(
                id = "doc-rs",
                title = "RS Porcha",
                sourceFileName = "rs_105.pdf",
                sourceFilePath = "/storage/rs_105.pdf",
                sourceCategory = "RS",
                classifiedType = "RS",
                fileFormat = "PDF",
                primaryDagNo = "১০৫",
                primaryKhatianNo = "১৮০",
                primaryAreaDecimals = "৪৫.০",
                ownersSummary = "কাসেম আলী"
            )
        )

        val report = matcher.matchWarishToSurveys(warish, records)
        val plot = report.matchedPlots.first()
        assertEquals(EvidenceTier.RECORD_GAP, plot.evidenceTier)
        assertTrue(plot.discrepancies.any { it.contains("Record Gap") })
    }

    @Test
    fun testVerifiedTierWithCompleteChainAndOwnerMatch() {
        val warish = WarishCertificate(
            certificateNo = "WAR-04",
            deceasedName = "নুরুল ইসলাম",
            targetDagNumbers = listOf("২০১"),
            heirs = listOf(
                HeirRecord(name = "পুত্র", relationship = "পুত্র", shareHissa = 0.5),
                HeirRecord(name = "কন্যা", relationship = "কন্যা", shareHissa = 0.5)
            )
        )

        // Continuous chain: CS, SA, and RS
        val records = listOf(
            LandDocumentEntity(
                id = "cs-1",
                title = "CS",
                sourceFileName = "cs_201.pdf",
                sourceFilePath = "/storage/cs_201.pdf",
                sourceCategory = "CS",
                classifiedType = "CS",
                fileFormat = "PDF",
                primaryDagNo = "২০১",
                primaryAreaDecimals = "৫০.০",
                ownersSummary = "নুরুল ইসলাম"
            ),
            LandDocumentEntity(
                id = "sa-1",
                title = "SA",
                sourceFileName = "sa_201.pdf",
                sourceFilePath = "/storage/sa_201.pdf",
                sourceCategory = "SA",
                classifiedType = "SA",
                fileFormat = "PDF",
                primaryDagNo = "২০১",
                primaryAreaDecimals = "৫০.০",
                ownersSummary = "নুরুল ইসলাম"
            ),
            LandDocumentEntity(
                id = "rs-1",
                title = "RS",
                sourceFileName = "rs_201.pdf",
                sourceFilePath = "/storage/rs_201.pdf",
                sourceCategory = "RS",
                classifiedType = "RS",
                fileFormat = "PDF",
                primaryDagNo = "২০১",
                primaryAreaDecimals = "৫০.০",
                ownersSummary = "নুরুল ইসলাম"
            )
        )

        val report = matcher.matchWarishToSurveys(warish, records)
        val plot = report.matchedPlots.first()
        assertEquals(EvidenceTier.VERIFIED, plot.evidenceTier)
        assertTrue(plot.confidenceScore >= 90.0f)
        assertEquals(2, plot.heirShares.size)
        assertEquals(25.0, plot.heirShares[0].allocatedDecimals, 0.01)
        assertEquals(25.0, plot.heirShares[1].allocatedDecimals, 0.01)
    }
}
