package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.dao.ExtractedLandRecordDao
import com.example.data.local.entity.DeedPartyEntity
import com.example.data.local.entity.DeedPlotScheduleEntity
import com.example.data.local.entity.DeedRecordEntity
import com.example.data.local.entity.DeedWithDetails
import com.example.data.local.entity.KhatianEntity
import com.example.data.local.entity.KhatianOwnerEntity
import com.example.data.local.entity.KhatianPlotEntity
import com.example.data.local.entity.KhatianWithDetails
import com.example.data.local.entity.LandChainLinkEntity
import com.example.data.local.entity.MutationPartyEntity
import com.example.data.local.entity.MutationPlotEntity
import com.example.data.local.entity.MutationRecordEntity
import com.example.data.local.entity.MutationWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository abstracting offline Room persistence for extracted land records:
 * CS, SA, RS, BRS Khatians, Registered Deeds, and Mutation Cases.
 */
class ExtractedLandRecordRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val dao: ExtractedLandRecordDao = database.extractedLandRecordDao()

    // Observables
    val allKhatians: Flow<List<KhatianWithDetails>> = dao.getAllKhatiansWithDetails()
    val allDeeds: Flow<List<DeedWithDetails>> = dao.getAllDeedsWithDetails()
    val allMutations: Flow<List<MutationWithDetails>> = dao.getAllMutationsWithDetails()

    val khatianCount: Flow<Int> = dao.getKhatianCount()
    val deedCount: Flow<Int> = dao.getDeedCount()
    val mutationCount: Flow<Int> = dao.getMutationCount()

    // Filtered Khatian Queries
    fun getKhatiansBySurveyType(surveyType: String): Flow<List<KhatianWithDetails>> =
        dao.getKhatiansBySurveyType(surveyType)

    fun getKhatianDetailsById(id: String): Flow<KhatianWithDetails?> =
        dao.getKhatianWithDetailsById(id)

    suspend fun getKhatianDetailsByIdOnce(id: String): KhatianWithDetails? =
        dao.getKhatianWithDetailsByIdOnce(id)

    fun searchKhatians(query: String): Flow<List<KhatianWithDetails>> =
        dao.searchKhatians(query)

    // Filtered Deed Queries
    fun getDeedDetailsById(id: String): Flow<DeedWithDetails?> =
        dao.getDeedWithDetailsById(id)

    suspend fun getDeedDetailsByIdOnce(id: String): DeedWithDetails? =
        dao.getDeedWithDetailsByIdOnce(id)

    fun getDeedsByType(deedType: String): Flow<List<DeedWithDetails>> =
        dao.getDeedsByType(deedType)

    fun searchDeeds(query: String): Flow<List<DeedWithDetails>> =
        dao.searchDeeds(query)

    // Filtered Mutation Queries
    fun getMutationDetailsById(id: String): Flow<MutationWithDetails?> =
        dao.getMutationWithDetailsById(id)

    suspend fun getMutationDetailsByIdOnce(id: String): MutationWithDetails? =
        dao.getMutationWithDetailsByIdOnce(id)

    fun getMutationsByStatus(status: String): Flow<List<MutationWithDetails>> =
        dao.getMutationsByStatus(status)

    fun searchMutations(query: String): Flow<List<MutationWithDetails>> =
        dao.searchMutations(query)

    // Title Chain Links
    fun getChainLinksByMouza(mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksByMouza(mouza)

    fun getChainLinksForDag(dagNo: String, mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksForDag(dagNo, mouza)

    fun getChainLinksForKhatian(khatianNo: String, mouza: String): Flow<List<LandChainLinkEntity>> =
        dao.getChainLinksForKhatian(khatianNo, mouza)

    // Persistence Actions
    suspend fun saveFullKhatian(
        khatian: KhatianEntity,
        owners: List<KhatianOwnerEntity>,
        plots: List<KhatianPlotEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullKhatian(khatian, owners, plots)
    }

    suspend fun saveFullDeed(
        deed: DeedRecordEntity,
        parties: List<DeedPartyEntity>,
        schedules: List<DeedPlotScheduleEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullDeed(deed, parties, schedules)
    }

    suspend fun saveFullMutation(
        mutation: MutationRecordEntity,
        parties: List<MutationPartyEntity>,
        plots: List<MutationPlotEntity>
    ) = withContext(Dispatchers.IO) {
        dao.insertFullMutation(mutation, parties, plots)
    }

    suspend fun saveChainLink(link: LandChainLinkEntity) = withContext(Dispatchers.IO) {
        dao.insertChainLink(link)
    }

    suspend fun deleteKhatian(id: String) = withContext(Dispatchers.IO) {
        dao.deleteKhatianById(id)
    }

    suspend fun deleteDeed(id: String) = withContext(Dispatchers.IO) {
        dao.deleteDeedById(id)
    }

    suspend fun deleteMutation(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMutationById(id)
    }

    /**
     * Seeds initial authentic Bangladesh land records for offline testing and immediate inspection.
     */
    suspend fun seedInitialExtractedRecordsIfEmpty() = withContext(Dispatchers.IO) {
        val currentCount = dao.getKhatianCount().first()
        if (currentCount > 0) return@withContext

        // 1. CS Khatian #403 (মৌজা: গোয়ালী, জে.এল: ০৮)
        val csKhatian = KhatianEntity(
            id = "cs-khatian-403",
            surveyType = "CS",
            khatianNo = "৪০৩",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            touziNo = "৫৪৬২",
            sheetNo = "১ নং চাদর",
            annualRent = "১২.৫০ টাকা",
            cess = "১.৫০ টাকা",
            isVerified = true,
            confidenceScore = 96.0f,
            remarks = "ক্যাডাস্ট্রাল সার্ভে মূল রেকর্ড (১৯০৫-১৯১২)"
        )
        val csOwners = listOf(
            KhatianOwnerEntity(
                id = "cs-403-o1",
                khatianId = csKhatian.id,
                serialNo = 1,
                ownerName = "সফর আলী",
                fatherOrHusbandName = "নাজির ওরফে আলী নোয়াজ",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            ),
            KhatianOwnerEntity(
                id = "cs-403-o2",
                khatianId = csKhatian.id,
                serialNo = 2,
                ownerName = "কামদার আলী",
                fatherOrHusbandName = "নাজির ওরফে আলী নোয়াজ",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            )
        )
        val csPlots = listOf(
            KhatianPlotEntity(
                id = "cs-403-p1",
                khatianId = csKhatian.id,
                dagNo = "১১৮",
                formerDagNo = "১১৮",
                halDagNo = "১১৮",
                landClass = "নাল",
                totalPlotAreaDecimals = 150.0,
                khatianShareAreaDecimals = 150.0,
                areaAcres = 1.50,
                boundaryNorth = "গোপাট",
                boundarySouth = "আব্দুল মজিদ",
                boundaryEast = "নদী",
                boundaryWest = "নিজ খতিয়ান"
            )
        )
        dao.insertFullKhatian(csKhatian, csOwners, csPlots)

        // 2. SA Khatian #403 (স্টেট একুইজিশন ১৯৫৬-১৯৬২)
        val saKhatian = KhatianEntity(
            id = "sa-khatian-403",
            surveyType = "SA",
            khatianNo = "৪০৩",
            formerKhatianNo = "৪০৩ (CS)",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            annualRent = "২৫.০০ টাকা",
            isVerified = true,
            confidenceScore = 94.0f,
            remarks = "জমিদারী উচ্ছেদ ও স্টেট একুইজিশন খতিয়ান"
        )
        val saOwners = listOf(
            KhatianOwnerEntity(
                id = "sa-403-o1",
                khatianId = saKhatian.id,
                serialNo = 1,
                ownerName = "সিরাজ আলী",
                fatherOrHusbandName = "মৃত সফর আলী",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            ),
            KhatianOwnerEntity(
                id = "sa-403-o2",
                khatianId = saKhatian.id,
                serialNo = 2,
                ownerName = "আব্দুল কাদির",
                fatherOrHusbandName = "মৃত কান্দর আলী",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            )
        )
        val saPlots = listOf(
            KhatianPlotEntity(
                id = "sa-403-p1",
                khatianId = saKhatian.id,
                dagNo = "১১৮",
                formerDagNo = "১১৮ (CS)",
                halDagNo = "১১৮",
                landClass = "নাল",
                totalPlotAreaDecimals = 150.0,
                khatianShareAreaDecimals = 150.0,
                areaAcres = 1.50
            )
        )
        dao.insertFullKhatian(saKhatian, saOwners, saPlots)

        // 3. RS Khatian #521 (রিভিশনাল সার্ভে)
        val rsKhatian = KhatianEntity(
            id = "rs-khatian-521",
            surveyType = "RS",
            khatianNo = "৫২১",
            formerKhatianNo = "৪০৩ (SA)",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            annualRent = "৪৫.০০ টাকা",
            isVerified = true,
            confidenceScore = 98.0f,
            remarks = "আর এস রিভিশনাল চূড়ান্ত প্রকাশিত খতিয়ান"
        )
        val rsOwners = listOf(
            KhatianOwnerEntity(
                id = "rs-521-o1",
                khatianId = rsKhatian.id,
                serialNo = 1,
                ownerName = "ফিরুজ মিয়া",
                fatherOrHusbandName = "মৃত সিরাজ আলী",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            ),
            KhatianOwnerEntity(
                id = "rs-521-o2",
                khatianId = rsKhatian.id,
                serialNo = 2,
                ownerName = "নোয়াব মিয়া",
                fatherOrHusbandName = "মৃত সিরাজ আলী",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.5000,
                hissaFractionBn = "৮ আনা"
            )
        )
        val rsPlots = listOf(
            KhatianPlotEntity(
                id = "rs-521-p1",
                khatianId = rsKhatian.id,
                dagNo = "২৫৪",
                formerDagNo = "১১৮ (SA)",
                halDagNo = "২৫৪",
                landClass = "নাল",
                totalPlotAreaDecimals = 120.0,
                khatianShareAreaDecimals = 120.0,
                areaAcres = 1.20
            )
        )
        dao.insertFullKhatian(rsKhatian, rsOwners, rsPlots)

        // 4. BRS / City Survey Khatian #247
        val brsKhatian = KhatianEntity(
            id = "brs-khatian-247",
            surveyType = "BRS_BS",
            khatianNo = "২৪৭",
            formerKhatianNo = "৫২১ (RS)",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            annualRent = "৮০.০০ টাকা",
            isVerified = true,
            confidenceScore = 95.0f,
            remarks = "বি আর এস কম্পিউটারাইজড জরিপ খতিয়ান"
        )
        val brsOwners = listOf(
            KhatianOwnerEntity(
                id = "brs-247-o1",
                khatianId = brsKhatian.id,
                serialNo = 1,
                ownerName = "রফিকুল ইসলাম",
                fatherOrHusbandName = "মৃত ফিরুজ মিয়া",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.6000,
                hissaFractionBn = "৯ আনা ১২ গণ্ডা"
            ),
            KhatianOwnerEntity(
                id = "brs-247-o2",
                khatianId = brsKhatian.id,
                serialNo = 2,
                ownerName = "খোকন মিয়া",
                fatherOrHusbandName = "মৃত ফিরুজ মিয়া",
                relationshipType = "পিতা",
                address = "গোয়ালী",
                hissaDecimal = 0.4000,
                hissaFractionBn = "৬ আনা ৮ গণ্ডা"
            )
        )
        val brsPlots = listOf(
            KhatianPlotEntity(
                id = "brs-247-p1",
                khatianId = brsKhatian.id,
                dagNo = "৬৭২",
                formerDagNo = "২৫৪ (RS)",
                halDagNo = "৬৭২",
                landClass = "বাড়ি ও নাল",
                totalPlotAreaDecimals = 95.0,
                khatianShareAreaDecimals = 95.0,
                areaAcres = 0.95
            )
        )
        dao.insertFullKhatian(brsKhatian, brsOwners, brsPlots)

        // 5. Registered Deed (সাফ-কবলা দলিল নং ৪৫২১/২০২৩)
        val deed = DeedRecordEntity(
            id = "deed-4521-2023",
            deedNo = "৪৫২১",
            deedYear = 2023,
            subRegistryOffice = "নবীনগর সাব-রেজিস্ট্রি অফিস",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            deedType = "SAF_KABALA",
            registrationDate = "১৪-০৮-২০২৩",
            balamVolumeNo = "১২",
            balamPageRange = "৪৫-৫০",
            totalConsiderationTaka = 850000.0,
            stampDutyTaka = 25500.0,
            totalAreaDecimals = 25.0,
            isVerified = true,
            confidenceScore = 97.5f,
            summaryNotes = "সাফ-কবলা রেজিস্ট্রি দলিল: গোয়ালী মৌজার ২৫ শতক নাল জমি হস্তান্তর"
        )
        val deedParties = listOf(
            DeedPartyEntity(
                id = "deed-4521-p1",
                deedId = deed.id,
                partyRole = "SELLER",
                name = "রফিকুল ইসলাম",
                fatherOrHusbandName = "মৃত ফিরুজ মিয়া",
                address = "গোয়ালী, কাইতলা দক্ষিণ",
                nidNumber = "৭৩০৬০১২৩০৮",
                transferredShareDecimal = 1.0,
                serialNo = 1
            ),
            DeedPartyEntity(
                id = "deed-4521-p2",
                deedId = deed.id,
                partyRole = "BUYER",
                name = "মোঃ আমিনুল ইসলাম",
                fatherOrHusbandName = "মৃত আবুল কাশেম",
                address = "গোয়ালী, কাইতলা দক্ষিণ",
                nidNumber = "১৯৮৫০৮২৫৪৬০০০০১২",
                transferredShareDecimal = 1.0,
                serialNo = 1
            )
        )
        val deedPlots = listOf(
            DeedPlotScheduleEntity(
                id = "deed-4521-s1",
                deedId = deed.id,
                mouza = "গোয়ালী",
                jlNo = "০৮",
                csKhatianNo = "৪০৩",
                saKhatianNo = "৪০৩",
                rsKhatianNo = "৫২১",
                brsKhatianNo = "২৪৭",
                dagNo = "৬৭২",
                formerDagNo = "২৫৪",
                halDagNo = "৬৭২",
                landClass = "নাল",
                transferredAreaDecimals = 25.0,
                transferredAreaAcres = 0.25,
                boundaryNorth = "রফিকুল ইসলামের অবশিষ্ট জমি",
                boundarySouth = "রাস্তা",
                boundaryEast = "খোকন মিয়া",
                boundaryWest = "আব্দুর রশিদ",
                plotPriceTaka = 850000.0
            )
        )
        dao.insertFullDeed(deed, deedParties, deedPlots)

        // 6. Upazila Mutation Record (নামজারি মোকদ্দমা নং ১২৩৪/২০২৪-২৫)
        val mutation = MutationRecordEntity(
            id = "mut-1234-2024",
            caseNumber = "১২৩৪/২০২৪-২৫",
            caseYear = "২০২৪-২৫",
            acLandOffice = "নবীনগর উপজেলা ভূমি অফিস",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "০৮",
            proposedKhatianNo = "১০৮৯",
            dischargedKhatianNo = "২৪৭ (BRS)",
            approvalDate = "১২-০২-২০২৪",
            orderDate = "১০-০২-২০২৪",
            mutatedAreaDecimals = 25.0,
            dcrNumber = "৫৬৭৮৯",
            dcrAmountTaka = 1150.0,
            dakhilaReceiptNo = "৯৮৭৬৫৪",
            mutationStatus = "APPROVED",
            basisOfMutation = "DEED",
            referenceDeedNo = "৪৫২১/২০২৩",
            orderSheetSummary = "সাফ-কবলা দলিল নং ৪৫২১/২০২৩ মূলে ২৪৭ নং বিআরএস খতিয়ান কর্তনপূর্বক নতুন ১০৮৯ নং নামজারি খতিয়ান সৃজন মঞ্জুর করা হলো।",
            isVerified = true,
            confidenceScore = 99.0f
        )
        val mutParties = listOf(
            MutationPartyEntity(
                id = "mut-1234-p1",
                mutationId = mutation.id,
                partyRole = "APPLICANT",
                name = "মোঃ আমিনুল ইসলাম",
                fatherOrHusbandName = "মৃত আবুল কাশেম",
                address = "গোয়ালী",
                nidNumber = "১৯৮৫০৮২৫৪৬০০০০১২",
                mutatedShareDecimal = 1.0,
                serialNo = 1
            ),
            MutationPartyEntity(
                id = "mut-1234-p2",
                mutationId = mutation.id,
                partyRole = "FORMER_OWNER",
                name = "রফিকুল ইসলাম",
                fatherOrHusbandName = "মৃত ফিরুজ মিয়া",
                address = "গোয়ালী",
                nidNumber = "৭৩০৬০১২৩০৮",
                mutatedShareDecimal = 1.0,
                serialNo = 2
            )
        )
        val mutPlots = listOf(
            MutationPlotEntity(
                id = "mut-1234-pl1",
                mutationId = mutation.id,
                dagNo = "৬৭২",
                formerDagNo = "২৫৪",
                halDagNo = "৬৭২",
                landClass = "নাল",
                totalPlotAreaDecimals = 95.0,
                mutatedAreaDecimals = 25.0,
                remainingAreaDecimals = 70.0
            )
        )
        dao.insertFullMutation(mutation, mutParties, mutPlots)

        // 7. Title Chain Links (CS ➔ SA ➔ RS ➔ BRS ➔ Deed ➔ Mutation)
        val chainLinks = listOf(
            LandChainLinkEntity(
                id = "chain-cs-to-sa",
                mouza = "গোয়ালী",
                jlNo = "০৮",
                fromSurvey = "CS",
                fromKhatianNo = "৪০৩",
                fromDagNo = "১১৮",
                fromOwnerName = "সফর আলী",
                toSurvey = "SA",
                toKhatianNo = "৪০৩",
                toDagNo = "১১৮",
                toOwnerName = "সিরাজ আলী",
                transitionType = "INHERITANCE",
                transferredAreaDecimals = 150.0,
                status = "VERIFIED",
                notes = "সফর আলীর মৃত্যুর পর ওয়ারিশসূত্রে পুত্র সিরাজ আলী ও আব্দুল কাদিরের নামীয় এসএ রেকর্ড"
            ),
            LandChainLinkEntity(
                id = "chain-sa-to-rs",
                mouza = "গোয়ালী",
                jlNo = "০৮",
                fromSurvey = "SA",
                fromKhatianNo = "৪০৩",
                fromDagNo = "১১৮",
                fromOwnerName = "সিরাজ আলী",
                toSurvey = "RS",
                toKhatianNo = "৫২১",
                toDagNo = "২৫৪",
                toOwnerName = "ফিরুজ মিয়া ও নোয়াব মিয়া",
                transitionType = "SURVEY_REVISION",
                transferredAreaDecimals = 120.0,
                status = "VERIFIED",
                notes = "এসএ ১১৮ দাগ বিভাজিত হয়ে আরএস ২৫৪ দাগ ও ৫২১ নং খতিয়ানে সিরাজ আলীর পুত্রগণের নামে রেকর্ড"
            ),
            LandChainLinkEntity(
                id = "chain-rs-to-brs",
                mouza = "গোয়ালী",
                jlNo = "০৮",
                fromSurvey = "RS",
                fromKhatianNo = "৫২১",
                fromDagNo = "২৫৪",
                fromOwnerName = "ফিরুজ মিয়া",
                toSurvey = "BRS",
                toKhatianNo = "২৪৭",
                toDagNo = "৬৭২",
                toOwnerName = "রফিকুল ইসলাম ও খোকন মিয়া",
                transitionType = "SURVEY_REVISION",
                transferredAreaDecimals = 95.0,
                status = "VERIFIED",
                notes = "আরএস ২৫৪ দাগ থেকে বিআরএস ৬৭২ দাগ এবং ফিরুজ মিয়ার পুত্রগণের নামে ২৪৭ নং খতিয়ান"
            ),
            LandChainLinkEntity(
                id = "chain-brs-to-deed",
                mouza = "গোয়ালী",
                jlNo = "০৮",
                fromSurvey = "BRS",
                fromKhatianNo = "২৪৭",
                fromDagNo = "৬৭২",
                fromOwnerName = "রফিকুল ইসলাম",
                toSurvey = "DEED",
                toKhatianNo = "২৪৭",
                toDagNo = "৬৭২",
                toOwnerName = "মোঃ আমিনুল ইসলাম",
                transitionType = "SALE_DEED",
                transferredAreaDecimals = 25.0,
                status = "VERIFIED",
                notes = "সাফ-কবলা রেজিস্ট্রি দলিল নং ৪৫২১/২০২৩ মূলে রফিকুল ইসলাম কর্তৃক ২৫ শতক বিক্রি"
            ),
            LandChainLinkEntity(
                id = "chain-deed-to-mut",
                mouza = "গোয়ালী",
                jlNo = "০৮",
                fromSurvey = "DEED",
                fromKhatianNo = "২৪৭",
                fromDagNo = "৬৭২",
                fromOwnerName = "রফিকুল ইসলাম",
                toSurvey = "MUTATION",
                toKhatianNo = "১০৮৯",
                toDagNo = "৬৭২",
                toOwnerName = "মোঃ আমিনুল ইসলাম",
                transitionType = "MUTATION",
                transferredAreaDecimals = 25.0,
                status = "VERIFIED",
                notes = "দলিলমূলে নবীনগর উপজেলা ভূমি অফিসে ১২৩৪/২০২৪-২৫ নং নামজারি কেসে ১০৮৯ নং খতিয়ান সৃজন"
            )
        )
        dao.insertChainLinks(chainLinks)
    }
}
