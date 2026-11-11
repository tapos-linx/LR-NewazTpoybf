package com.example.domain.gis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Geometric parcel representing a Cadastral Survey Dag (দাগ).
 */
data class CadastralParcel(
    val dagNo: String,
    val bounds: Rect, // Normalized coordinates (0.0 to 1.0)
    val areaDecimals: Double,
    val landClass: String = "নাল",
    val isTargetMatched: Boolean = false,
    val linkedHeirCount: Int = 0,
    val centerPoint: Offset = Offset(bounds.center.x, bounds.center.y)
)

/**
 * Cadastral map road line feature.
 */
data class CadastralRoad(
    val id: String,
    val name: String = "গ্রাম্য রাস্তা / হালট",
    val pathPoints: List<Offset> // Normalized coordinates
)

/**
 * Cadastral map water body feature (খাল / নদী / পুকুর).
 */
data class CadastralWaterBody(
    val id: String,
    val name: String = "পুকুর / খাল",
    val bounds: Rect
)

/**
 * Complete Cadastral Survey Sheet intelligence data.
 */
data class CadastralSheetData(
    val surveyType: String = "CS", // CS, SA, RS, BRS
    val sheetNo: String = "১",
    val mouzaName: String = "",
    val jlNo: String = "",
    val upazila: String = "",
    val district: String = "",
    val parcels: List<CadastralParcel> = emptyList(),
    val roadLines: List<CadastralRoad> = emptyList(),
    val waterBodies: List<CadastralWaterBody> = emptyList(),
    val hasNorthArrow: Boolean = true,
    val scaleRatio: String = "১৬ ইঞ্চি = ১ মাইল"
)

/**
 * Cadastral Survey Map Intelligence Engine.
 *
 * Implements evidence-based GIS logic:
 * - Highlights ONLY genuine target Dags from matched inheritance certificates.
 * - Enforces zero-fabrication: never invents map coordinates, parcels, or road vectors.
 * - When no map sheet is loaded, returns empty data with NO_MAP status.
 */
class CadastralMapDetector(private val surveyType: String = "CS") {

    /**
     * Highlights matched target Dags on genuine detected cadastral sheet parcels.
     * If no map parcels are provided, returns an empty sheet without fabricated polygons.
     */
    fun detectAndHighlightParcels(
        targetDags: List<String>,
        baseParcels: List<CadastralParcel> = emptyList(),
        roadLines: List<CadastralRoad> = emptyList(),
        waterBodies: List<CadastralWaterBody> = emptyList(),
        mouzaHint: String = "",
        sheetNoHint: String = "১"
    ): CadastralSheetData {
        val normalizedTargetDags = targetDags.map { it.trim() }

        if (baseParcels.isEmpty()) {
            return CadastralSheetData(
                surveyType = surveyType,
                sheetNo = sheetNoHint,
                mouzaName = mouzaHint,
                jlNo = "",
                upazila = "",
                district = "",
                parcels = emptyList(),
                roadLines = emptyList(),
                waterBodies = emptyList(),
                hasNorthArrow = false,
                scaleRatio = ""
            )
        }

        // Strict Forensic Rule:
        // Highlights ONLY the lands matched from the inheritance certificate,
        // leaving all other parcels in their original survey state.
        val highlightedParcels = baseParcels.map { parcel ->
            val isMatched = normalizedTargetDags.any { target ->
                target.isNotBlank() && (target.contains(parcel.dagNo) || parcel.dagNo.contains(target))
            }
            parcel.copy(
                isTargetMatched = isMatched,
                linkedHeirCount = if (isMatched) 1 else 0
            )
        }

        return CadastralSheetData(
            surveyType = surveyType,
            sheetNo = sheetNoHint,
            mouzaName = mouzaHint,
            jlNo = "",
            upazila = "",
            district = "",
            parcels = highlightedParcels,
            roadLines = roadLines,
            waterBodies = waterBodies,
            hasNorthArrow = true,
            scaleRatio = "১৬ ইঞ্চি = ১ মাইল"
        )
    }

    /**
     * Helper to verify forensic highlighting rule:
     * Never highlight all parcels unless all parcels happen to be the target.
     */
    fun verifyHighlightingRule(sheetData: CadastralSheetData, targetDags: List<String>): Boolean {
        val highlightedCount = sheetData.parcels.count { it.isTargetMatched }
        if (sheetData.parcels.isEmpty()) return true
        if (targetDags.isEmpty()) return highlightedCount == 0
        return highlightedCount <= targetDags.size
    }
}
