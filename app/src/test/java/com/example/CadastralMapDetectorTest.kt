package com.example

import com.example.domain.gis.CadastralMapDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CadastralMapDetectorTest {

    private val detector = CadastralMapDetector(surveyType = "CS")

    @Test
    fun testSheetMetadataAndFeaturesDetection() {
        val sheetData = detector.detectAndHighlightParcels(
            targetDags = listOf("১০২"),
            mouzaHint = "শুভাঢ্যা",
            sheetNoHint = "১"
        )

        assertEquals("CS", sheetData.surveyType)
        assertEquals("শুভাঢ্যা", sheetData.mouzaName)
        assertEquals("১", sheetData.sheetNo)
        assertTrue(sheetData.hasNorthArrow)
        assertEquals("১৬ ইঞ্চি = ১ মাইল", sheetData.scaleRatio)
        assertTrue(sheetData.parcels.isNotEmpty())
        assertTrue(sheetData.roadLines.isNotEmpty())
        assertTrue(sheetData.waterBodies.isNotEmpty())
    }

    @Test
    fun testHighlightingRuleNeverHighlightsAllParcels() {
        val targetDags = listOf("১০২", "১০৯")
        val sheetData = detector.detectAndHighlightParcels(targetDags)

        // Strict rule: never highlight all parcels
        val totalParcels = sheetData.parcels.size
        val highlightedCount = sheetData.parcels.count { it.isTargetMatched }

        assertTrue(totalParcels > highlightedCount)
        assertEquals(2, highlightedCount)

        // Verify that target Dags are highlighted
        val p102 = sheetData.parcels.first { it.dagNo == "১০২" }
        val p109 = sheetData.parcels.first { it.dagNo == "১০৯" }
        assertTrue(p102.isTargetMatched)
        assertTrue(p109.isTargetMatched)

        // Verify that un-matched parcels are NOT highlighted
        val p101 = sheetData.parcels.first { it.dagNo == "১০১" }
        val p103 = sheetData.parcels.first { it.dagNo == "১০৩" }
        assertFalse(p101.isTargetMatched)
        assertFalse(p103.isTargetMatched)

        // Rule validator should return true
        assertTrue(detector.verifyHighlightingRule(sheetData, targetDags))
    }

    @Test
    fun testEmptyTargetDagsLeavesAllParcelsUnaltered() {
        val sheetData = detector.detectAndHighlightParcels(emptyList())
        val highlightedCount = sheetData.parcels.count { it.isTargetMatched }
        assertEquals(0, highlightedCount)
        assertTrue(detector.verifyHighlightingRule(sheetData, emptyList()))
    }
}
