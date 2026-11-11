package com.example.domain.matcher

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.LandDocumentEntity
import com.example.ui.theme.*
import java.util.UUID

/**
 * Four Canonical Forensic Evidence Statuses with Ladder Grades.
 *
 * CONFIRMED (Ladder A–B): matches original document image and was human-verified, or supported by 2+ independent documents.
 * PROBABLE (Ladder C): strong match on name + dag + mouza, but a linking document is missing.
 * CONFLICTED (Ladder D): contradictory names, father-name mismatch, shares > 1.000, area mismatch.
 * UNVERIFIED (Ladder E–F): machine-extracted or single unverified source. Default for every newly extracted document.
 * RECORD GAP: represented as a warning badge on a chain, not a tier.
 */
enum class EvidenceTier(
    val code: String,
    val bengaliLabel: String,
    val description: String,
    val ladderGrade: String,
    val badgeColor: Color
) {
    CONFIRMED(
        code = "CONFIRMED",
        bengaliLabel = "নিশ্চিত (Confirmed)",
        description = "মূল নথির ছবির সাথে সম্পূর্ণ মিলে গেছে এবং মানুষ দ্বারা যাচাইকৃত, অথবা ২+ স্বতন্ত্র নথি দ্বারা সমর্থিত।",
        ladderGrade = "লেডার A–B",
        badgeColor = StatusVerified
    ),
    PROBABLE(
        code = "PROBABLE",
        bengaliLabel = "সম্ভাব্য (Probable)",
        description = "নাম, দাগ ও মৌজা অনুসারে জোরালো সম্ভাবনা বিদ্যমান, কিন্তু সংযোগকারী নথি অনুপস্থিত।",
        ladderGrade = "লেডার C",
        badgeColor = StatusProbable
    ),
    CONFLICTED(
        code = "CONFLICTED",
        bengaliLabel = "সাংঘর্ষিক (Conflicted)",
        description = "পরস্পরবিরোধী নাম, পিতার নাম অমিল, হিস্যা > ১.০০০ বা জমির পরিমাণে সরাসরি অমিল।",
        ladderGrade = "লেডার D",
        badgeColor = StatusContradictory
    ),
    UNVERIFIED(
        code = "UNVERIFIED",
        bengaliLabel = "অযাচাইকৃত (Unverified)",
        description = "স্বয়ংক্রিয় মেশিন দ্বারা এক্সট্র্যাক্টকৃত বা একক অযাচাইকৃত উৎস। সকল নতুন নথির ডিফল্ট অবস্থা।",
        ladderGrade = "লেডার E–F",
        badgeColor = StatusRecordGap
    );

    companion object {
        // Legacy compatibility aliases
        val VERIFIED get() = CONFIRMED
        val CORROBORATED get() = CONFIRMED
        val CONTRADICTORY get() = CONFLICTED
        val POSSIBLE get() = UNVERIFIED
        val RECORD_GAP get() = UNVERIFIED
    }
}

/**
 * Individual heir listed in the Warishnama / Inheritance certificate.
 */
data class HeirRecord(
    val id: String = UUID.randomUUID().toString(),
    val serialNo: String = "",
    val name: String,
    val relationship: String, // পুত্র, কন্যা, স্ত্রী, স্বামী, পিতা, মাতা, ভাই, বোন
    val shareHissa: Double, // Decimal share, e.g. 0.500 or 0.125
    val shareFractionLabel: String = "", // e.g. "৮ আনা" বা "১/২ অংশ"
    val nidOrBirthCert: String = "",
    val dateOfBirth: String = "-",
    val remarks: String = "জীবিত",
    val isDeceased: Boolean = false,
    val subHeirs: List<HeirRecord> = emptyList()
)

/**
 * Warishnama (Inheritance Certificate) document representation.
 */
data class WarishCertificate(
    val id: String = UUID.randomUUID().toString(),
    val certificateNo: String,
    val certificateDate: String = "১৬-০৯-২০২৬",
    val deceasedName: String,
    val fatherOrHusbandName: String = "",
    val motherName: String = "",
    val villageOrArea: String = "গোয়ালী",
    val unionOrWard: String = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
    val postOffice: String = "কাইতলা - ৩৪৭৭",
    val upazila: String = "নবীনগর",
    val district: String = "ব্রাহ্মণবাড়িয়া",
    val dateOfDeath: String = "",
    val issuerDesignation: String = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
    val heirs: List<HeirRecord> = emptyList(),
    val targetDagNumbers: List<String> = emptyList(),
    val targetMouza: String = "গোয়ালী",
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
    val hasRecordGap: Boolean = false,
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
    val hasRecordGap: Boolean = false,
    val generatedTimestamp: Long = System.currentTimeMillis()
)
