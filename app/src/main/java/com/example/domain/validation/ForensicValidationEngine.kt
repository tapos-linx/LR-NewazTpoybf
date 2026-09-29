package com.example.domain.validation

import com.example.domain.matcher.EvidenceTier
import kotlin.math.abs

data class ValidationIssue(
    val category: ValidationIssueCategory,
    val severity: IssueSeverity,
    val descriptionBn: String,
    val descriptionEn: String,
    val fieldName: String
)

enum class ValidationIssueCategory {
    NAME_VARIANT,
    FATHER_MISMATCH,
    AREA_MISMATCH,
    CHAIN_DISCONTINUITY,
    DUPLICATE_IDENTITY,
    SHARE_IMBALANCE
}

enum class IssueSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class ForensicValidationReport(
    val overallTier: EvidenceTier,
    val confidenceScore: Float,
    val issues: List<ValidationIssue>,
    val verifiedCount: Int,
    val warningCount: Int,
    val criticalCount: Int,
    val summaryBn: String
)

class ForensicValidationEngine {

    /**
     * Normalizes Bengali names by stripping honorifics and standardized prefixes.
     */
    fun normalizeName(name: String): String {
        var n = name.trim()
        val prefixes = listOf("মোঃ", "মোছাঃ", "মোহাম্মদ", "মুহম্মদ", "মরহুম", "জনাব", "শেখ", "হাজী", "মৌলভী", "শ্রী", "শ্রীমতি")
        for (p in prefixes) {
            if (n.startsWith(p)) {
                n = n.removePrefix(p).trim()
            }
        }
        val enPrefixes = listOf("md.", "md", "mohammad", "mohammed", "late", "mrs.", "mr.")
        for (ep in enPrefixes) {
            if (n.lowercase().startsWith(ep)) {
                n = n.substring(ep.length).trim()
            }
        }
        return n.replace("[\\s\\-\\.,]+".toRegex(), " ")
    }

    /**
     * Converts various traditional land measurement units into standard Decimals (শতাংশ).
     */
    fun convertToDecimals(value: Double, unit: String): Double {
        return when (unit.lowercase()) {
            "acre", "একর" -> value * 100.0
            "bigha", "বিঘা" -> value * 33.0
            "katha", "কাঠা" -> value * 1.65
            "chhatak", "ছটাক" -> value * (1.65 / 16.0)
            "sqft", "বর্গফুট" -> value / 435.6
            else -> value // Default decimal/shatangsha
        }
    }

    /**
     * Evaluates cross-record forensic integrity.
     */
    fun validateRecords(
        csOwner: String?,
        csFather: String?,
        rsOwner: String?,
        rsFather: String?,
        csAreaDecimals: Double,
        rsAreaDecimals: Double,
        hasSaRecord: Boolean,
        totalHissa: Double
    ): ForensicValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        // 1. Name and Father Verification
        if (!csFather.isNullOrBlank() && !rsFather.isNullOrBlank()) {
            val normCsFather = normalizeName(csFather)
            val normRsFather = normalizeName(rsFather)
            if (normCsFather != normRsFather) {
                issues.add(
                    ValidationIssue(
                        category = ValidationIssueCategory.FATHER_MISMATCH,
                        severity = IssueSeverity.CRITICAL,
                        descriptionBn = "পিতার নামে অসঙ্গতি: সি এসে '$csFather' কিন্তু আর এসে '$rsFather'",
                        descriptionEn = "Father mismatch between CS ('$csFather') and RS ('$rsFather')",
                        fieldName = "father_name"
                    )
                )
            }
        }

        // 2. Area Discrepancy
        val areaDiff = csAreaDecimals - rsAreaDecimals
        if (rsAreaDecimals > csAreaDecimals * 1.05) {
            issues.add(
                ValidationIssue(
                    category = ValidationIssueCategory.AREA_MISMATCH,
                    severity = IssueSeverity.CRITICAL,
                    descriptionBn = "হাল দাগের জমি মূল দাগের চেয়ে বেশি (${rsAreaDecimals} > ${csAreaDecimals} শতাংশ)",
                    descriptionEn = "Child parcel area exceeds parent parcel area",
                    fieldName = "area"
                )
            )
        } else if (areaDiff > 0.05) {
            issues.add(
                ValidationIssue(
                    category = ValidationIssueCategory.AREA_MISMATCH,
                    severity = IssueSeverity.WARNING,
                    descriptionBn = "দাগ বিভাজনে $areaDiff শতাংশ জমির ঘাটতি বা রেকর্ড অসম্পূর্ণতা",
                    descriptionEn = "Area discrepancy of $areaDiff decimals in parcel transition",
                    fieldName = "area"
                )
            )
        }

        // 3. Chain Discontinuity (SA Gap)
        if (!hasSaRecord) {
            issues.add(
                ValidationIssue(
                    category = ValidationIssueCategory.CHAIN_DISCONTINUITY,
                    severity = IssueSeverity.WARNING,
                    descriptionBn = "এস এ (SA) মধ্যবর্তী রেকর্ড অনুপস্থিত - ধারাবাহিকতায় ব্যবধান (Record Gap)",
                    descriptionEn = "Intermediary SA record missing between CS and RS",
                    fieldName = "survey_chain"
                )
            )
        }

        // 4. Share balance
        if (abs(totalHissa - 1.0) > 0.005) {
            issues.add(
                ValidationIssue(
                    category = ValidationIssueCategory.SHARE_IMBALANCE,
                    severity = IssueSeverity.CRITICAL,
                    descriptionBn = "মোট হিস্যা ১.০০০০ (১৬ আনা) এর সমান নহে (বর্তমান: $totalHissa)",
                    descriptionEn = "Total inheritance share does not equal 1.000 / 16 anna",
                    fieldName = "hissa"
                )
            )
        }

        // Determine Final Evidence Tier
        val criticalCount = issues.count { it.severity == IssueSeverity.CRITICAL }
        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }

        val (tier, score) = when {
            criticalCount > 0 -> EvidenceTier.CONTRADICTORY to 0.35f
            issues.any { it.category == ValidationIssueCategory.CHAIN_DISCONTINUITY } -> EvidenceTier.RECORD_GAP to 0.58f
            warningCount > 0 -> EvidenceTier.PROBABLE to 0.76f
            else -> EvidenceTier.VERIFIED to 0.98f
        }

        val summaryBn = when (tier) {
            EvidenceTier.VERIFIED -> "নথির সকল তথ্য ও ধারাবাহিক স্বত্ব সম্পূর্ণরূপে যাচাইকৃত (VERIFIED)"
            EvidenceTier.CORROBORATED -> "সহায়ক নথিপত্র দ্বারা সমর্থিত স্বত্ব (CORROBORATED)"
            EvidenceTier.PROBABLE -> "স্বত্ব সম্ভাব্য তবে কিছু ক্ষেত্রে সতর্ক পর্যবেক্ষণ প্রয়োজন (PROBABLE)"
            EvidenceTier.POSSIBLE -> "আংশিক মিল রয়েছে কিন্তু পর্যাপ্ত প্রমাণের ঘাটতি (POSSIBLE)"
            EvidenceTier.RECORD_GAP -> "জরিপকালীন ধারাবাহিক রেকর্ডে ব্যবধান চিহ্নিত (RECORD GAP)"
            EvidenceTier.CONTRADICTORY -> "গুরুতর আইনি বা ক্ষেত্রীয় অসঙ্গতি বিদ্যমান (CONTRADICTORY)"
        }

        return ForensicValidationReport(
            overallTier = tier,
            confidenceScore = score,
            issues = issues,
            verifiedCount = if (criticalCount == 0 && warningCount == 0) 1 else 0,
            warningCount = warningCount,
            criticalCount = criticalCount,
            summaryBn = summaryBn
        )
    }
}
