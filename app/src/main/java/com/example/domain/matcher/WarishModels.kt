package com.example.domain.matcher

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.LandDocumentEntity
import com.example.ui.theme.*
import java.util.UUID

/**
 * Six Forensic Evidence Tiers as mandated by LR-Newaz forensic standard.
 */
enum class EvidenceTier(
    val code: String,
    val bengaliLabel: String,
    val description: String,
    val badgeColor: Color
) {
    VERIFIED(
        code = "VERIFIED",
        bengaliLabel = "যাচাইকৃত (Verified)",
        description = "সরাসরি মূল নথির সাথে সম্পূর্ণ মিলে গেছে এবং জরিপ শৃঙ্খল অক্ষুণ্ন।",
        badgeColor = StatusVerified
    ),
    CORROBORATED(
        code = "CORROBORATED",
        bengaliLabel = "সমর্থিত (Corroborated)",
        description = "একাধিক স্বতন্ত্র নথি বা দলিলের মাধ্যমে সমর্থনপ্রাপ্ত।",
        badgeColor = StatusCorroborated
    ),
    PROBABLE(
        code = "PROBABLE",
        bengaliLabel = "সম্ভাব্য (Probable)",
        description = "নাম, দাগ ও মৌজা অনুসারে জোরালো সম্ভাবনা বিদ্যমান।",
        badgeColor = StatusProbable
    ),
    POSSIBLE(
        code = "POSSIBLE",
        bengaliLabel = "সম্ভাবনাময় (Possible)",
        description = "প্রাথমিক মিল রয়েছে কিন্তু পর্যাপ্ত সংযোগকারী প্রমাণ মেলেনি।",
        badgeColor = StatusPossible
    ),
    RECORD_GAP(
        code = "RECORD_GAP",
        bengaliLabel = "রেকর্ড ফাঁক (Record Gap)",
        description = "ধারাবাহিক জরিপের মধ্যবর্তী নথি (যেমন: এসএ বা আরএস) অনুপস্থিত।",
        badgeColor = StatusRecordGap
    ),
    CONTRADICTORY(
        code = "CONTRADICTORY",
        bengaliLabel = "পরস্পরবিরোধী (Contradictory)",
        description = "হিস্যা অতিরিক্ত, নাম অমিল বা দাগ নম্বরে সরাসরি সাংঘর্ষিক দাবি।",
        badgeColor = StatusContradictory
    )
}

/**
 * Individual heir listed in the Warishnama / Inheritance certificate.
 */
data class HeirRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationship: String, // পুত্র, কন্যা, স্ত্রী, স্বামী, পিতা, মাতা, ভাই, বোন
    val shareHissa: Double, // Decimal share, e.g. 0.500 or 0.125
    val shareFractionLabel: String = "", // e.g. "৮ আনা" বা "১/২ অংশ"
    val nidOrBirthCert: String = "",
    val isDeceased: Boolean = false,
    val subHeirs: List<HeirRecord> = emptyList()
)

/**
 * Warishnama (Inheritance Certificate) document representation.
 */
data class WarishCertificate(
    val id: String = UUID.randomUUID().toString(),
    val certificateNo: String,
    val deceasedName: String,
    val fatherOrHusbandName: String = "",
    val villageOrArea: String = "",
    val unionOrWard: String = "",
    val upazila: String = "",
    val district: String = "",
    val dateOfDeath: String = "",
    val issuerDesignation: String = "চেয়ারম্যান / কাউন্সিলর",
    val heirs: List<HeirRecord> = emptyList(),
    val targetDagNumbers: List<String> = emptyList(),
    val targetMouza: String = "",
    val targetKhatianNumbers: List<String> = emptyList(),
    val notes: String = ""
) {
    val totalShareSum: Double
        get() = heirs.sumOf { it.shareHissa }

    val isHissaBalanced: Boolean
        get() = kotlin.math.abs(totalShareSum - 1.0) < 0.001
}

/**
 * Allocated share of a specific Dag parcel for an heir.
 */
data class HeirShareAllocation(
    val heir: HeirRecord,
    val allocatedDecimals: Double,
    val percentageOfPlot: Double,
    val note: String = ""
)

/**
 * Survey record match for a specific Dag parcel.
 */
data class MatchedPlotResult(
    val dagNo: String,
    val mouza: String,
    val upazila: String,
    val district: String,
    val totalAreaDecimals: Double,
    val landClass: String,
    val surveyedChains: List<String>, // e.g. ["CS খতিয়ান: ১০২", "SA খতিয়ান: ১৫৪", "RS খতিয়ান: ২১০"]
    val matchedSurveyRecords: List<LandDocumentEntity>,
    val heirShares: List<HeirShareAllocation>,
    val evidenceTier: EvidenceTier,
    val confidenceScore: Float, // 0.0 to 100.0%
    val discrepancies: List<String>,
    val isContradictory: Boolean = false,
    val notes: String = ""
)

/**
 * Complete audit report produced by InheritanceMatcher.
 */
data class WarishMatchReport(
    val warishCertificate: WarishCertificate,
    val matchedPlots: List<MatchedPlotResult>,
    val totalDeclaredShare: Double,
    val isHissaMathematicallyBalanced: Boolean,
    val totalInheritedDecimals: Double,
    val overallEvidenceTier: EvidenceTier,
    val overallConfidenceScore: Float,
    val globalDiscrepancies: List<String>,
    val chainCompleteness: Map<String, Boolean>, // CS, SA, RS, BRS
    val generatedTimestamp: Long = System.currentTimeMillis()
)
