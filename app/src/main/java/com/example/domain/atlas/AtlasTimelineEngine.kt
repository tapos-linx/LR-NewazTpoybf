package com.example.domain.atlas

import java.util.UUID

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
     * Builds a forensic chain-of-title timeline for a specific plot and lineage.
     */
    fun buildHistoricalTimeline(
        mouza: String,
        baseCsDag: String
    ): List<AtlasChainNode> {
        return listOf(
            AtlasChainNode(
                era = SurveyEra.CS,
                khatianNo = "১০৪",
                dagNo = baseCsDag,
                owners = listOf("মরহুম হাজী জমিরুদ্দিন শেখ"),
                areaDecimals = 50.0,
                relationshipNotes = "সি এস রেকর্ডীয় মূল ভূস্বামী (মুকররী স্বত্ব)",
                eventType = ChainEventType.ORIGIN,
                childDagNumbers = listOf("৪১২")
            ),
            AtlasChainNode(
                era = SurveyEra.SA,
                khatianNo = "৩১৮",
                dagNo = "৪১২",
                owners = listOf("আব্দুল বারিক", "আব্দুল করিম"),
                areaDecimals = 50.0,
                relationshipNotes = "পিতার মৃত্যুর পর দুই পুত্রের নামে ৫০% হারে স্টেট একুইজিশন রেকর্ড",
                eventType = ChainEventType.INHERITANCE,
                childDagNumbers = listOf("৮১৫/১", "৮১৫/২")
            ),
            AtlasChainNode(
                era = SurveyEra.RS,
                khatianNo = "৫১২",
                dagNo = "৮১৫/১",
                owners = listOf("আব্দুল বারিক"),
                areaDecimals = 25.0,
                relationshipNotes = "আপোষ বন্টননামা ও রিভিশনাল সার্ভেতে সাবেক ৪১২ দাগ বিভক্ত হইয়া ৮১৫/১ ও ৮১৫/২ দাগ সৃষ্টি",
                eventType = ChainEventType.DAG_SPLIT,
                childDagNumbers = listOf("১২০৪")
            ),
            AtlasChainNode(
                era = SurveyEra.BS,
                khatianNo = "৭২০",
                dagNo = "১২০৪",
                owners = listOf("মোঃ রফিকুল ইসলাম (বারিকের ওয়ারিশ)"),
                areaDecimals = 25.0,
                relationshipNotes = "সিটি জরিপে চূড়ান্ত প্রচার ও হাল দখল বহাল",
                eventType = ChainEventType.MUTATION,
                isContinuityVerified = true
            )
        )
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
