package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ============================================================================
// 1. SURVEY KHATIAN ENTITIES (CS, SA, RS, BRS / BS)
// ============================================================================

/**
 * Represents an extracted Bangladesh Land Survey Khatian (Record of Rights / Porcha).
 * Supports CS (Cadastral Survey), SA (State Acquisition), RS (Revisional Survey),
 * and BRS/BS (Bangladesh Survey / City Survey).
 */
@Entity(
    tableName = "extracted_khatians",
    indices = [
        Index(value = ["surveyType"]),
        Index(value = ["khatianNo"]),
        Index(value = ["mouza"]),
        Index(value = ["district", "upazila", "mouza"]),
        Index(value = ["surveyType", "mouza", "khatianNo"]),
        Index(value = ["documentId"])
    ]
)
data class KhatianEntity(
    @PrimaryKey
    val id: String, // e.g. "khatian-cs-403"
    val documentId: String? = null, // Foreign reference to scanned LandDocumentEntity
    val surveyType: String, // CS, SA, RS, BRS_BS
    val khatianNo: String, // e.g. "৪০৩"
    val formerKhatianNo: String? = null, // সাবেক খতিয়ান
    val halKhatianNo: String? = null, // হাল খতিয়ান
    val district: String, // জেলা (e.g. "ব্রাহ্মণবাড়িয়া", "গাজীপুর")
    val upazila: String, // উপজেলা/থানা (e.g. "নবীনগর", "জয়দেবপুর")
    val mouza: String, // মৌজা (e.g. "গোয়ালী", "কাশিমপুর")
    val jlNo: String, // জে. এল. নং (e.g. "০৮", "৬৫")
    val touziNo: String? = null, // তৌজি নং
    val sheetNo: String? = null, // চাদর নং (e.g. "১ নং চাদর")
    val annualRent: String? = null, // বার্ষিক খাজনা
    val cess: String? = null, // সেস
    val isVerified: Boolean = false,
    val confidenceScore: Float = 0.0f,
    val extractionSource: String = "OCR", // OCR, GEMINI, MANUAL, EPORCHA
    val rawText: String? = null,
    val remarks: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Individual owner/tenant (রায়ত ও মালিকগণের তালিকা) associated with a survey khatian.
 */
@Entity(
    tableName = "khatian_owners",
    foreignKeys = [
        ForeignKey(
            entity = KhatianEntity::class,
            parentColumns = ["id"],
            childColumns = ["khatianId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["khatianId"]),
        Index(value = ["ownerName"]),
        Index(value = ["fatherOrHusbandName"])
    ]
)
data class KhatianOwnerEntity(
    @PrimaryKey
    val id: String, // e.g. "k-owner-403-1"
    val khatianId: String,
    val serialNo: Int = 1,
    val ownerName: String, // মালিকের নাম
    val fatherOrHusbandName: String? = null, // পিতা বা স্বামীর নাম
    val relationshipType: String = "পিতা", // "পিতা", "স্বামী", "মাতা"
    val address: String? = null, // ঠিকানা
    val hissaDecimal: Double = 0.0, // হিস্যা/অংশ (0.0001 - 1.0000)
    val hissaFractionBn: String? = null, // e.g. "৪ আনা", "১৬ আনা"
    val nidOrBirthCert: String? = null,
    val remarks: String? = null
)

/**
 * Individual plot/dag (জমির দাগ সূচি) linked to a survey khatian.
 */
@Entity(
    tableName = "khatian_plots",
    foreignKeys = [
        ForeignKey(
            entity = KhatianEntity::class,
            parentColumns = ["id"],
            childColumns = ["khatianId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["khatianId"]),
        Index(value = ["dagNo"]),
        Index(value = ["formerDagNo"]),
        Index(value = ["halDagNo"])
    ]
)
data class KhatianPlotEntity(
    @PrimaryKey
    val id: String, // e.g. "k-plot-403-118"
    val khatianId: String,
    val dagNo: String, // দাগ নং (e.g. "১১৮")
    val formerDagNo: String? = null, // সাবেক দাগ
    val halDagNo: String? = null, // হাল দাগ
    val landClass: String = "নাল", // জমির শ্রেণি (নাল, ভিটি, বাড়ি, পুকুর, বাগান, ইত্যাদি)
    val totalPlotAreaDecimals: Double = 0.0, // দাগের মোট জমির পরিমাণ (শতক)
    val khatianShareAreaDecimals: Double = 0.0, // অত্র খতিয়ানের হিস্যানুযায়ী জমি (শতক)
    val areaAcres: Double = 0.0, // একর
    val boundaryNorth: String? = null, // উত্তর সীমানা
    val boundarySouth: String? = null, // দক্ষিণ সীমানা
    val boundaryEast: String? = null, // পূর্ব সীমানা
    val boundaryWest: String? = null, // পশ্চিম সীমানা
    val remarks: String? = null
)

// ============================================================================
// 2. REGISTERED DEEDS (দলিল / রেজিস্ট্রি দলিল / DALIL)
// ============================================================================

/**
 * Represents a Registered Deed (রেজিস্ট্রি দলিল) from a Sub-Registry Office.
 * Types include Saf-Kabala (সাফ-কবলা), Heba (হেবা), Heba-bil-Ewaz (হেবা বিল এওয়াজ),
 * Bantan-nama (বন্টননামা), Ewaz (এওয়াজ), Bayna (বায়নাপত্র), etc.
 */
@Entity(
    tableName = "extracted_deeds",
    indices = [
        Index(value = ["deedNo"]),
        Index(value = ["deedYear"]),
        Index(value = ["deedType"]),
        Index(value = ["subRegistryOffice"]),
        Index(value = ["mouza"]),
        Index(value = ["documentId"])
    ]
)
data class DeedRecordEntity(
    @PrimaryKey
    val id: String, // e.g. "deed-4521-2023"
    val documentId: String? = null, // Reference to scanned LandDocumentEntity
    val deedNo: String, // দলিল নং (e.g. "৪৫২১")
    val deedYear: Int, // সাল/বছর (e.g. 2023)
    val subRegistryOffice: String, // সাব-রেজিস্ট্রি অফিস (e.g. "নবীনগর সাব-রেজিস্ট্রি অফিস")
    val district: String,
    val upazila: String,
    val mouza: String,
    val jlNo: String? = null,
    val deedType: String, // SAF_KABALA, HEBA, HEBA_BIL_EWAZ, BANTANNAMA, EWAZ, BAYNA, WILL
    val registrationDate: String? = null, // রেজিস্ট্রির তারিখ
    val balamVolumeNo: String? = null, // বালাম খণ্ড নং
    val balamPageRange: String? = null, // পৃষ্ঠা নং
    val totalConsiderationTaka: Double = 0.0, // মোট পণমূল্য টাকা
    val stampDutyTaka: Double = 0.0, // স্ট্যাম্প শুল্ক
    val totalAreaDecimals: Double = 0.0, // হস্তান্তরিত মোট জমির পরিমাণ (শতক)
    val isVerified: Boolean = false,
    val confidenceScore: Float = 0.0f,
    val extractionSource: String = "OCR",
    val summaryNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Parties involved in a registered deed (দাতার বিবরণ ও গ্রহীতার বিবরণ).
 */
@Entity(
    tableName = "deed_parties",
    foreignKeys = [
        ForeignKey(
            entity = DeedRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["deedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deedId"]),
        Index(value = ["partyRole"]),
        Index(value = ["name"]),
        Index(value = ["nidNumber"])
    ]
)
data class DeedPartyEntity(
    @PrimaryKey
    val id: String, // e.g. "d-party-4521-1"
    val deedId: String,
    val partyRole: String, // SELLER (দাতা/বিক্রেতা), BUYER (গ্রহীতা/ক্রেতা), WITNESS (সাক্ষী), IDENTIFIER (শনাক্তকারী)
    val name: String,
    val fatherOrHusbandName: String? = null,
    val motherName: String? = null,
    val address: String? = null,
    val nidNumber: String? = null,
    val transferredShareDecimal: Double = 1.0, // হস্তান্তরিত অংশ
    val serialNo: Int = 1
)

/**
 * Land Schedule (জমির তফসিল) specified within a registered deed.
 */
@Entity(
    tableName = "deed_plot_schedules",
    foreignKeys = [
        ForeignKey(
            entity = DeedRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["deedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deedId"]),
        Index(value = ["dagNo"]),
        Index(value = ["rsKhatianNo"]),
        Index(value = ["csKhatianNo"])
    ]
)
data class DeedPlotScheduleEntity(
    @PrimaryKey
    val id: String, // e.g. "d-sched-4521-1"
    val deedId: String,
    val mouza: String,
    val jlNo: String? = null,
    val csKhatianNo: String? = null,
    val saKhatianNo: String? = null,
    val rsKhatianNo: String? = null,
    val brsKhatianNo: String? = null,
    val dagNo: String, // হস্তান্তরিত দাগ নং
    val formerDagNo: String? = null,
    val halDagNo: String? = null,
    val landClass: String = "নাল",
    val transferredAreaDecimals: Double = 0.0,
    val transferredAreaAcres: Double = 0.0,
    val boundaryNorth: String? = null,
    val boundarySouth: String? = null,
    val boundaryEast: String? = null,
    val boundaryWest: String? = null,
    val plotPriceTaka: Double = 0.0
)

// ============================================================================
// 3. MUTATIONS (নামজারি ও জমাভাগ / NAMJARI / MUTATION)
// ============================================================================

/**
 * Represents an Upazila Land Office Mutation Case & Porcha (নামজারি ও জমাভাগ খতিয়ান).
 */
@Entity(
    tableName = "extracted_mutations",
    indices = [
        Index(value = ["caseNumber"]),
        Index(value = ["proposedKhatianNo"]),
        Index(value = ["dischargedKhatianNo"]),
        Index(value = ["acLandOffice"]),
        Index(value = ["mouza"]),
        Index(value = ["mutationStatus"]),
        Index(value = ["documentId"])
    ]
)
data class MutationRecordEntity(
    @PrimaryKey
    val id: String, // e.g. "mut-1234-2024"
    val documentId: String? = null,
    val caseNumber: String, // নামজারি মোকদ্দমা নং (e.g. "১২৩৪/২০২৪-২৫")
    val caseYear: String, // সাল (e.g. "২০২৪-২৫")
    val acLandOffice: String, // উপজেলা ভূমি অফিস (e.g. "নবীনগর উপজেলা ভূমি অফিস")
    val district: String,
    val upazila: String,
    val mouza: String,
    val jlNo: String,
    val proposedKhatianNo: String, // প্রস্তাবিত হাল নামজারি খতিয়ান নং
    val dischargedKhatianNo: String? = null, // কর্তনকৃত সাবেক খতিয়ান নং
    val approvalDate: String? = null, // নামজারি মঞ্জুরের তারিখ
    val orderDate: String? = null,
    val mutatedAreaDecimals: Double = 0.0, // নামজারিকৃত মোট জমির পরিমাণ (শতক)
    val dcrNumber: String? = null, // ডুপ্লিকেট কার্বন রসিদ (DCR) নং
    val dcrAmountTaka: Double = 0.0, // ডিসিআর ফি
    val dakhilaReceiptNo: String? = null, // ভূমি উন্নয়ন কর দাখিলা নং
    val mutationStatus: String = "APPROVED", // APPROVED, PENDING, REJECTED, APPEALED
    val basisOfMutation: String = "DEED", // DEED (দলিলমূলে), INHERITANCE (ওয়ারিশসূত্রে), AUCTION, DECREE
    val referenceDeedNo: String? = null, // সূত্র দলিল নং
    val referenceWarishCertNo: String? = null, // সূত্র ওয়ারিশ সনদ নং
    val orderSheetSummary: String? = null, // আদেশনামা ও অনুমোদনের সারাংশ
    val isVerified: Boolean = false,
    val confidenceScore: Float = 0.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Applicant & Previous Owner parties for a mutation.
 */
@Entity(
    tableName = "mutation_parties",
    foreignKeys = [
        ForeignKey(
            entity = MutationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["mutationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mutationId"]),
        Index(value = ["partyRole"]),
        Index(value = ["name"])
    ]
)
data class MutationPartyEntity(
    @PrimaryKey
    val id: String, // e.g. "mut-party-1234-1"
    val mutationId: String,
    val partyRole: String, // APPLICANT (আবেদনকারী/রেকর্ডীয় মালিক), FORMER_OWNER (সাবেক রেকর্ডীয় মালিক), CO_SHARER (সহ-মালিক)
    val name: String,
    val fatherOrHusbandName: String? = null,
    val address: String? = null,
    val nidNumber: String? = null,
    val mutatedShareDecimal: Double = 1.0,
    val serialNo: Int = 1
)

/**
 * Plot detail associated with a mutation.
 */
@Entity(
    tableName = "mutation_plots",
    foreignKeys = [
        ForeignKey(
            entity = MutationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["mutationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mutationId"]),
        Index(value = ["dagNo"]),
        Index(value = ["formerDagNo"]),
        Index(value = ["halDagNo"])
    ]
)
data class MutationPlotEntity(
    @PrimaryKey
    val id: String, // e.g. "mut-plot-1234-1"
    val mutationId: String,
    val dagNo: String, // নামজারিকৃত দাগ নং
    val formerDagNo: String? = null, // সাবেক দাগ
    val halDagNo: String? = null, // হাল দাগ
    val landClass: String = "নাল",
    val totalPlotAreaDecimals: Double = 0.0,
    val mutatedAreaDecimals: Double = 0.0,
    val remainingAreaDecimals: Double = 0.0 // অবশিষ্টাংশ জমি
)

// ============================================================================
// 4. CROSS-SURVEY TITLE TRACE & HISTORICAL CHAIN OF TITLE (ধারাবাহিক স্বত্ব)
// ============================================================================

/**
 * Explicit links tracking genealogical and cadastral title chain
 * from CS ➔ SA ➔ RS ➔ BRS ➔ Deeds ➔ Mutation.
 */
@Entity(
    tableName = "land_chain_links",
    indices = [
        Index(value = ["mouza"]),
        Index(value = ["fromSurvey", "fromKhatianNo", "fromDagNo"]),
        Index(value = ["toSurvey", "toKhatianNo", "toDagNo"]),
        Index(value = ["transitionType"]),
        Index(value = ["status"])
    ]
)
data class LandChainLinkEntity(
    @PrimaryKey
    val id: String, // e.g. "link-cs118-sa118-rs254"
    val mouza: String,
    val jlNo: String? = null,
    val fromSurvey: String, // CS, SA, RS, DEED, INHERITANCE
    val fromKhatianNo: String,
    val fromDagNo: String,
    val fromOwnerName: String,
    val toSurvey: String, // SA, RS, BRS, MUTATION, DEED
    val toKhatianNo: String,
    val toDagNo: String,
    val toOwnerName: String,
    val transitionType: String, // SURVEY_REVISION, INHERITANCE, SALE_DEED, MUTATION, PARTITION
    val transferredAreaDecimals: Double = 0.0,
    val evidenceDocumentId: String? = null,
    val status: String = "VERIFIED", // VERIFIED, PROBABLE, DISPUTED, GAP
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ============================================================================
// 5. COMPOSITE RELATIONAL POJOS FOR CONVENIENT COMPOSABLE QUERYING
// ============================================================================

/**
 * Complete Khatian including all its parsed owners and plot records.
 */
data class KhatianWithDetails(
    @Embedded
    val khatian: KhatianEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "khatianId"
    )
    val owners: List<KhatianOwnerEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "khatianId"
    )
    val plots: List<KhatianPlotEntity>
)

/**
 * Complete Registered Deed including all parties and schedule plots.
 */
data class DeedWithDetails(
    @Embedded
    val deed: DeedRecordEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "deedId"
    )
    val parties: List<DeedPartyEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "deedId"
    )
    val plotSchedules: List<DeedPlotScheduleEntity>
)

/**
 * Complete Mutation Record including all applicants/former owners and plots.
 */
data class MutationWithDetails(
    @Embedded
    val mutation: MutationRecordEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "mutationId"
    )
    val parties: List<MutationPartyEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "mutationId"
    )
    val plots: List<MutationPlotEntity>
)
