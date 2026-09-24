package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Bangladesh Land Record Survey / Document Categories.
 */
enum class LandRecordType(
    val code: String,
    val bengaliName: String,
    val englishName: String,
    val description: String
) {
    CS(
        code = "CS",
        bengaliName = "সি এস খতিয়ান (ক্যাডাস্ট্রাল সার্ভে)",
        englishName = "Cadastral Survey (CS)",
        description = "First survey of Bengal (1888-1940) prepared under Bengal Tenancy Act 1885."
    ),
    SA(
        code = "SA",
        bengaliName = "এস এ খতিয়ান (স্টেট একুইজিশন)",
        englishName = "State Acquisition (SA)",
        description = "Prepared after East Bengal State Acquisition and Tenancy Act 1950 (1956-1962)."
    ),
    RS(
        code = "RS",
        bengaliName = "আর এস খতিয়ান (রিভিশনাল সার্ভে)",
        englishName = "Revisional Survey (RS)",
        description = "Revisional survey correcting errors of SA survey."
    ),
    BRS_BS(
        code = "BRS",
        bengaliName = "বি আর এস / বি এস / সিটি জরিপ",
        englishName = "Bangladesh / City Survey (BRS/BS)",
        description = "Modern computerized survey conducted across Bangladesh."
    ),
    NAMJARI_MUTATION(
        code = "MUTATION",
        bengaliName = "নামজারি ও জমাভাগ খতিয়ান",
        englishName = "Namjari / Mutation Porcha",
        description = "Upazila land office mutation khatian updating ownership after transfer or inheritance."
    ),
    DEED_DALIL(
        code = "DEEDS",
        bengaliName = "দলিল (সাফ-কবলা/হেবা/বন্টননামা)",
        englishName = "Registered Deed (Dalil)",
        description = "Sub-registry office deed transferring title or partition."
    ),
    DAKHILA_TAX(
        code = "TAX",
        bengaliName = "দাখিলা (ভূমি উন্নয়ন কর রশিদ)",
        englishName = "Tax Receipt (Dakhila)",
        description = "Land development tax payment receipt issued by Union Land Office."
    ),
    MOUZA_MAP(
        code = "MAPS",
        bengaliName = "মৌজা নকশা / ম্যাপ",
        englishName = "Mouza Map / Sheet",
        description = "Survey map showing Dag/Plot geographic boundaries."
    ),
    WARIS_INHERITANCE(
        code = "INHERITANCE",
        bengaliName = "ওয়ারিশনামা / উত্তরাধিকার সনদ",
        englishName = "Inheritance Certificate (Warisnama)",
        description = "Heirship certificate issued by Union Parishad or City Ward."
    ),
    OTHER(
        code = "OTHER",
        bengaliName = "অন্যান্য ভূমি সংক্রান্ত নথি",
        englishName = "Other Land Document",
        description = "Miscellaneous land record document."
    );

    companion object {
        fun fromFolderOrName(hint: String): LandRecordType {
            val upper = hint.uppercase()
            fun hasWord(word: String): Boolean =
                upper.contains(Regex("(^|[^A-Z])$word([^A-Z]|$)"))

            return when {
                hasWord("BRS") || upper.contains("বি আর এস") || upper.contains("বি.আর.এস") ||
                        hasWord("BS") || upper.contains("বি এস") || upper.contains("সিটি জরিপ") -> BRS_BS
                hasWord("CS") || upper.contains("সি এস") || upper.contains("সি.এস") -> CS
                hasWord("SA") || upper.contains("এস এ") || upper.contains("এস.এ") -> SA
                hasWord("RS") || upper.contains("আর এস") || upper.contains("আর.এস") -> RS
                upper.contains("MUTATION") || upper.contains("নামজারি") || upper.contains("খারিজ") || upper.contains("জমাভাগ") -> NAMJARI_MUTATION
                upper.contains("DEED") || upper.contains("DALIL") || upper.contains("দলিল") || upper.contains("কবলা") || upper.contains("হেবা") -> DEED_DALIL
                upper.contains("TAX") || upper.contains("দাখিলা") || upper.contains("খাজনা") || upper.contains("কর") -> DAKHILA_TAX
                upper.contains("MAP") || upper.contains("নকশা") || upper.contains("ম্যাপ") -> MOUZA_MAP
                upper.contains("INHERITANCE") || upper.contains("WARIS") || upper.contains("ওয়ারিশ") -> WARIS_INHERITANCE
                else -> OTHER
            }
        }
    }
}

/**
 * Verification Confidence Level for extracted fields.
 */
enum class ConfidenceLevel {
    VERIFIED,   // Confirmed by user or high OCR confidence
    PROBABLE,   // High likelihood match from OCR
    UNCERTAIN   // Needs human inspection against visual evidence
}

/**
 * Field with confidence tracking and evidence reference.
 */
@JsonClass(generateAdapter = true)
data class FieldWithConfidence(
    val value: String = "",
    val confidence: ConfidenceLevel = ConfidenceLevel.UNCERTAIN,
    val rawOcrMatch: String = "",
    val evidencePage: Int = 1,
    val userEdited: Boolean = false,
    val note: String = ""
)

/**
 * Land Owner / Khatianidar record.
 */
@JsonClass(generateAdapter = true)
data class OwnerRecord(
    val serial: Int = 1,
    val name: String = "",
    val fatherOrHusbandName: String = "",
    val shareHissa: String = "",
    val address: String = "",
    val confidence: ConfidenceLevel = ConfidenceLevel.UNCERTAIN
)

/**
 * Complete structured Bangladesh Land Record data model.
 */
@JsonClass(generateAdapter = true)
data class LandRecordData(
    val recordType: LandRecordType = LandRecordType.OTHER,
    val district: FieldWithConfidence = FieldWithConfidence(),
    val upazilaThana: FieldWithConfidence = FieldWithConfidence(),
    val mouza: FieldWithConfidence = FieldWithConfidence(),
    val jlNo: FieldWithConfidence = FieldWithConfidence(),
    val touziNo: FieldWithConfidence = FieldWithConfidence(),
    val sheetNo: FieldWithConfidence = FieldWithConfidence(),
    val khatianNo: FieldWithConfidence = FieldWithConfidence(),
    val formerKhatianNo: FieldWithConfidence = FieldWithConfidence(),
    val halKhatianNo: FieldWithConfidence = FieldWithConfidence(),
    val dagNo: FieldWithConfidence = FieldWithConfidence(),
    val formerDagNo: FieldWithConfidence = FieldWithConfidence(),
    val halDagNo: FieldWithConfidence = FieldWithConfidence(),
    val landClass: FieldWithConfidence = FieldWithConfidence(),
    val landShareHissa: FieldWithConfidence = FieldWithConfidence(),
    val areaAcres: FieldWithConfidence = FieldWithConfidence(),
    val areaDecimals: FieldWithConfidence = FieldWithConfidence(),
    val annualRent: FieldWithConfidence = FieldWithConfidence(),
    val deedNo: FieldWithConfidence = FieldWithConfidence(),
    val deedDate: FieldWithConfidence = FieldWithConfidence(),
    val mutationCaseNo: FieldWithConfidence = FieldWithConfidence(),
    val owners: List<OwnerRecord> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val isHissaValid: Boolean = true,
    val hissaTotalCalculated: Double = 0.0,
    val legalDisclaimerAcknowledged: Boolean = false
)
