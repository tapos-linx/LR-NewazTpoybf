package com.example.domain.atlas

import com.example.data.local.entity.LandDocumentEntity

/**
 * Historical Property Atlas & Forensic Chain Reconstruction Engine.
 * Traces Bangladesh land records across historical survey eras:
 * CS (1888-1940) -> SA (1956-1962) -> RS (1965-1990) -> BS/BRS (1998-Present).
 */
data class AtlasChainNode(
    val era: SurveyEra,
    val khatianNo: String,
    val dagNo: String,
    val owners: List<String>,
    val areaDecimals: Double,
    val relationshipNotes: String,
    val eventType: ChainEventType = ChainEventType.ORIGIN,
    val childDagNumbers: List<String> = emptyList(), // In case of Dag split
    val isContinuityVerified: Boolean = true
)

enum class SurveyEra(val code: String, val nameBn: String, val period: String) {
    CS("CS", "সি এস খতিয়ান (ক্যাডাস্ট্রাল সার্ভে)", "১৮৮৮ - ১৯৪০"),
    SA("SA", "এস এ খতিয়ান (স্টেট একুইজিশন)", "১৯৫৬ - ১৯৬২"),
    RS("RS", "আর এস খতিয়ান (রিভিশনাল সার্ভে)", "১৯৬৫ - ১৯৯০"),
    BS("BS", "বি আর এস / বি এস জরিপ", "১৯৯৮ - বর্তমান")
}

enum class ChainEventType(val displayNameBn: String) {
    ORIGIN("মূল স্বত্ব (Original Base)"),
    INHERITANCE("উত্তরাধিকার সূত্রে বন্টন (Inheritance Partition)"),
    DAG_SPLIT("দাগ বিভাজন / শিকস্তি (Dag Split)"),
    DAG_MERGE("দাগ একত্রীকরণ (Dag Merge)"),
    SALE_DEED("সাফ-কবলা বিক্রয় (Deed Transfer)"),
    MUTATION("খারিজ / জমা পৃথকীকরণ (Mutation)")
}

data class DagSplitAnalysis(
    val parentDag: String,
    val parentEra: SurveyEra,
    val parentAreaDecimals: Double,
    val childDags: List<String>,
    val childEra: SurveyEra,
    val combinedChildAreaDecimals: Double,
    val isAreaBalanced: Boolean,
    val discrepancyDecimals: Double,
    val remarks: String
)

class AtlasTimelineEngine {

    /**
     * Builds a forensic chain-of-title timeline from genuine persisted land records.
     * Enforces anti-hallucination rules: returns empty list when no matching records exist.
     */
    fun buildHistoricalTimeline(
        mouza: String,
        baseCsDag: String,
        records: List<LandDocumentEntity> = emptyList()
    ): List<AtlasChainNode> {
        if (records.isEmpty()) return emptyList()

        val matching = records.filter { doc ->
            (mouza.isBlank() || doc.primaryMouza?.contains(mouza, ignoreCase = true) == true) &&
            (doc.primaryDagNo == baseCsDag || doc.extractedDataJson?.contains(baseCsDag) == true)
        }

        if (matching.isEmpty()) return emptyList()

        val nodes = mutableListOf<AtlasChainNode>()
        for (era in listOf(SurveyEra.CS, SurveyEra.SA, SurveyEra.RS, SurveyEra.BS)) {
            val eraDoc = matching.find {
                it.classifiedType.equals(era.code, ignoreCase = true) || it.sourceCategory.equals(era.code, ignoreCase = true)
            }
            if (eraDoc != null) {
                nodes.add(
                    AtlasChainNode(
                        era = era,
                        khatianNo = eraDoc.primaryKhatianNo ?: "অনির্দিষ্ট",
                        dagNo = eraDoc.primaryDagNo ?: baseCsDag,
                        owners = listOfNotNull(eraDoc.ownersSummary?.ifBlank { null } ?: "মালিক তথ্য অনুপস্থিত"),
                        areaDecimals = eraDoc.primaryAreaDecimals?.toDoubleOrNull() ?: 0.0,
                        relationshipNotes = "${era.nameBn} মূল নথিকৃত রেকর্ড",
                        eventType = if (nodes.isEmpty()) ChainEventType.ORIGIN else ChainEventType.MUTATION
                    )
                )
            }
        }
        return nodes
    }

    /**
     * Analyzes Dag split detection between historical surveys.
     */
    fun detectDagSplit(
        parentDag: String,
        parentArea: Double,
        childDags: List<Pair<String, Double>>
    ): DagSplitAnalysis {
        val totalChildArea = childDags.sumOf { it.second }
        val diff = Math.abs(parentArea - totalChildArea)
        val isBalanced = diff < 0.05

        val remarks = if (isBalanced) {
            "দাগ বিভাজন সম্পূর্ণ সমতাপ্রাপ্ত (Area Balance Verified)"
        } else if (totalChildArea < parentArea) {
            "দাগ বিভাজনে ${(parentArea - totalChildArea)} শতাংশ জমি ঘাটতি ও অমিল (Record Gap / Lost Area)"
        } else {
            "শাখা দাগের মোট জমি মূল দাগের চেয়ে বেশি (Contradictory Inflation)"
        }

        return DagSplitAnalysis(
            parentDag = parentDag,
            parentEra = SurveyEra.SA,
            parentAreaDecimals = parentArea,
            childDags = childDags.map { it.first },
            childEra = SurveyEra.RS,
            combinedChildAreaDecimals = totalChildArea,
            isAreaBalanced = isBalanced,
            discrepancyDecimals = diff,
            remarks = remarks
        )
    }
}
