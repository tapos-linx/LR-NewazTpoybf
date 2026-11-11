package com.example.fixtures

import com.example.data.local.entity.DeedPartyEntity
import com.example.data.local.entity.DeedPlotScheduleEntity
import com.example.data.local.entity.DeedRecordEntity
import com.example.data.local.entity.KhatianEntity
import com.example.data.local.entity.KhatianOwnerEntity
import com.example.data.local.entity.KhatianPlotEntity
import com.example.data.local.entity.LandChainLinkEntity
import com.example.data.local.entity.MutationPartyEntity
import com.example.data.local.entity.MutationPlotEntity
import com.example.data.local.entity.MutationRecordEntity
import com.example.data.repository.ExtractedLandRecordRepository
import com.example.domain.gis.CadastralParcel
import com.example.domain.gis.CadastralRoad
import com.example.domain.gis.CadastralWaterBody
import com.example.domain.matcher.HeirRecord
import com.example.domain.matcher.WarishCertificate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Isolated test fixtures for Robolectric and Unit Testing.
 * Explicitly labelled SYNTHETIC_TEST_DATA.
 */
object SyntheticTestData {

    fun createSyntheticCadastralParcels(): List<CadastralParcel> {
        return listOf(
            CadastralParcel("১০১", Rect(0.1f, 0.1f, 0.3f, 0.3f), 15.0),
            CadastralParcel("১০২", Rect(0.3f, 0.1f, 0.5f, 0.3f), 20.0),
            CadastralParcel("১০৩", Rect(0.5f, 0.1f, 0.7f, 0.3f), 12.0),
            CadastralParcel("১০৯", Rect(0.3f, 0.3f, 0.5f, 0.5f), 18.0)
        )
    }

    fun createSyntheticCadastralRoads(): List<CadastralRoad> {
        return listOf(
            CadastralRoad("road-1", "হালট", listOf(Offset(0.0f, 0.5f), Offset(1.0f, 0.5f)))
        )
    }

    fun createSyntheticCadastralWaterBodies(): List<CadastralWaterBody> {
        return listOf(
            CadastralWaterBody("water-1", "পুকুর", Rect(0.7f, 0.7f, 0.9f, 0.9f))
        )
    }

    fun createSyntheticDefaultWarish(): WarishCertificate {
        return createSyntheticMajorWarishList().first()
    }

    suspend fun seedTestKhatians(repository: ExtractedLandRecordRepository) {
        // 1. CS Khatian
        val csKhatian = KhatianEntity(
            id = "test-cs-403",
            surveyType = "CS",
            khatianNo = "৪০৩",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "৮৫",
            isVerified = true
        )
        val csOwners = listOf(
            KhatianOwnerEntity(id = "cso-1", khatianId = "test-cs-403", ownerName = "সফর আলী", serialNo = 1, hissaDecimal = 0.5),
            KhatianOwnerEntity(id = "cso-2", khatianId = "test-cs-403", ownerName = "কামদার আলী", serialNo = 2, hissaDecimal = 0.5)
        )
        val csPlots = listOf(
            KhatianPlotEntity(id = "csp-1", khatianId = "test-cs-403", dagNo = "১১৮", landClass = "নাল", totalPlotAreaDecimals = 60.0)
        )
        repository.saveFullKhatian(csKhatian, csOwners, csPlots)

        // 2. SA Khatian
        val saKhatian = KhatianEntity(
            id = "test-sa-403",
            surveyType = "SA",
            khatianNo = "৪০৩",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "৮৫",
            isVerified = true
        )
        val saOwners = listOf(
            KhatianOwnerEntity(id = "sao-1", khatianId = "test-sa-403", ownerName = "সিরাজ আলী", serialNo = 1, hissaDecimal = 1.0)
        )
        val saPlots = listOf(
            KhatianPlotEntity(id = "sap-1", khatianId = "test-sa-403", dagNo = "১১৮", landClass = "নাল", totalPlotAreaDecimals = 60.0)
        )
        repository.saveFullKhatian(saKhatian, saOwners, saPlots)

        // 3. RS Khatian
        val rsKhatian = KhatianEntity(
            id = "test-rs-521",
            surveyType = "RS",
            khatianNo = "৫২১",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "৮৫",
            isVerified = true
        )
        val rsOwners = listOf(
            KhatianOwnerEntity(id = "rso-1", khatianId = "test-rs-521", ownerName = "ফিরুজ মিয়া", serialNo = 1, hissaDecimal = 1.0)
        )
        val rsPlots = listOf(
            KhatianPlotEntity(id = "rsp-1", khatianId = "test-rs-521", dagNo = "২৫৪", landClass = "ভিটি", totalPlotAreaDecimals = 40.0)
        )
        repository.saveFullKhatian(rsKhatian, rsOwners, rsPlots)

        // 4. BRS/BS Khatian
        val brsKhatian = KhatianEntity(
            id = "test-brs-247",
            surveyType = "BRS_BS",
            khatianNo = "২৪৭",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "৮৫",
            isVerified = true
        )
        val brsOwners = listOf(
            KhatianOwnerEntity(id = "brso-1", khatianId = "test-brs-247", ownerName = "রফিকুল ইসলাম", serialNo = 1, hissaDecimal = 1.0)
        )
        val brsPlots = listOf(
            KhatianPlotEntity(id = "brsp-1", khatianId = "test-brs-247", dagNo = "৬৭২", landClass = "নাল", totalPlotAreaDecimals = 25.0)
        )
        repository.saveFullKhatian(brsKhatian, brsOwners, brsPlots)

        // 5. Registered Deed
        val deed = DeedRecordEntity(
            id = "test-deed-4521",
            deedNo = "৪৫২১",
            deedYear = 2023,
            subRegistryOffice = "নবীনগর সাব-রেজিস্ট্রি অফিস",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            deedType = "SAF_KABALA"
        )
        val deedParties = listOf(
            DeedPartyEntity(id = "test-dp-1", deedId = "test-deed-4521", name = "রফিকুল ইসলাম", partyRole = "SELLER", serialNo = 1),
            DeedPartyEntity(id = "test-dp-2", deedId = "test-deed-4521", name = "মোঃ টিটু", partyRole = "BUYER", serialNo = 2)
        )
        val deedPlots = listOf(
            DeedPlotScheduleEntity(id = "test-ds-1", deedId = "test-deed-4521", mouza = "গোয়ালী", dagNo = "৬৭২", transferredAreaDecimals = 25.0)
        )
        repository.saveFullDeed(deed, deedParties, deedPlots)

        // 6. Upazila Mutation
        val mutation = MutationRecordEntity(
            id = "test-mut-1234",
            caseNumber = "১২৩৪/২০২৪-২৫",
            caseYear = "২০২৪-২৫",
            acLandOffice = "নবীনগর উপজেলা ভূমি অফিস",
            district = "ব্রাহ্মণবাড়িয়া",
            upazila = "নবীনগর",
            mouza = "গোয়ালী",
            jlNo = "৮৫",
            proposedKhatianNo = "১০৮৯",
            mutationStatus = "APPROVED"
        )
        val mutParties = listOf(
            MutationPartyEntity(id = "test-mp-1", mutationId = "test-mut-1234", name = "মোঃ টিটু", partyRole = "APPLICANT", serialNo = 1),
            MutationPartyEntity(id = "test-mp-2", mutationId = "test-mut-1234", name = "রফিকুল ইসলাম", partyRole = "FORMER_OWNER", serialNo = 2)
        )
        val mutPlots = listOf(
            MutationPlotEntity(id = "test-mplot-1", mutationId = "test-mut-1234", dagNo = "৬৭২", mutatedAreaDecimals = 25.0)
        )
        repository.saveFullMutation(mutation, mutParties, mutPlots)

        // 7. Chain Links
        val links = listOf(
            LandChainLinkEntity(id = "link-1", mouza = "গোয়ালী", fromSurvey = "CS", toSurvey = "SA", fromKhatianNo = "৪০৩", fromDagNo = "১১৮", fromOwnerName = "সফর আলী", toKhatianNo = "৪০৩", toDagNo = "১১৮", toOwnerName = "সিরাজ আলী", transitionType = "INHERITANCE"),
            LandChainLinkEntity(id = "link-2", mouza = "গোয়ালী", fromSurvey = "SA", toSurvey = "RS", fromKhatianNo = "৪০৩", fromDagNo = "১১৮", fromOwnerName = "সিরাজ আলী", toKhatianNo = "৫২১", toDagNo = "২৫৪", toOwnerName = "ফিরুজ মিয়া", transitionType = "SURVEY_REVISION"),
            LandChainLinkEntity(id = "link-3", mouza = "গোয়ালী", fromSurvey = "RS", toSurvey = "BRS", fromKhatianNo = "৫২১", fromDagNo = "২৫৪", fromOwnerName = "ফিরুজ মিয়া", toKhatianNo = "২৪৭", toDagNo = "৬৭২", toOwnerName = "রফিকুল ইসলাম", transitionType = "SURVEY_REVISION"),
            LandChainLinkEntity(id = "link-4", mouza = "গোয়ালী", fromSurvey = "BRS", toSurvey = "DEED", fromKhatianNo = "২৪৭", fromDagNo = "৬৭২", fromOwnerName = "রফিকুল ইসলাম", toKhatianNo = "৪৫২১", toDagNo = "৬৭২", toOwnerName = "মোঃ টিটু", transitionType = "SALE_DEED"),
            LandChainLinkEntity(id = "link-5", mouza = "গোয়ালী", fromSurvey = "DEED", toSurvey = "MUTATION", fromKhatianNo = "৪৫২১", fromDagNo = "৬৭২", fromOwnerName = "মোঃ টিটু", toKhatianNo = "১০৮৯", toDagNo = "৬৭২", toOwnerName = "মোঃ টিটু", transitionType = "MUTATION")
        )
        for (link in links) {
            repository.saveChainLink(link)
        }
    }

    fun createSyntheticMajorWarishList(): List<WarishCertificate> {
        return listOf(
            WarishCertificate(
                id = "major-warish-1",
                certificateNo = "২০২৬৫৪১১৮১২০০০০০২",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "ফিরুজ মিয়া",
                fatherOrHusbandName = "মৃত সিরাজ আলী",
                motherName = "মৃত আইমনের নেছা",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "২২-০৫-২০০৯ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০২", "১০৫", "১০৯"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩", "২৪৭", "৫২১"),
                notes = "SYNTHETIC_TEST_DATA (ক্রমিক ৩): মৃত ফিরুজ মিয়া",
                heirs = listOf(
                    HeirRecord(id = "w1-h1", serialNo = "১", name = "তাসলিমা বেগম", relationship = "১ম স্ত্রী", shareHissa = 0.0625, shareFractionLabel = "১ আনা (১/১৬)", nidOrBirthCert = "-", dateOfBirth = "-", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w1-h2", serialNo = "২", name = "রফিকুল ইসলাম", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "৭৩০৬০১২৩০৮", dateOfBirth = "০৬/০৫/১৯৭৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h3", serialNo = "৩", name = "রানু কাইয়ুম", relationship = "কন্যা", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", nidOrBirthCert = "৩১৭৬০৮২২১৭", dateOfBirth = "০৯/০৪/১৯৭২", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h4", serialNo = "৪", name = "সুরাইয়া বেগম", relationship = "কন্যা", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", nidOrBirthCert = "-", dateOfBirth = "-", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h5", serialNo = "৫", name = "রোকেয়া খাতুন", relationship = "২য় স্ত্রী", shareHissa = 0.0625, shareFractionLabel = "১ আনা (১/১৬)", nidOrBirthCert = "১৯৪৬৫৩০৩০৮", dateOfBirth = "১৮/১০/১৯৫৭", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h6", serialNo = "৬", name = "খোকন মিয়া", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "৬৯২০১৭৭৮৩", dateOfBirth = "১০/০২/১৯৭৩", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h7", serialNo = "৭", name = "মোঃ টিটু", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "৬০২৫৮৪১৭৩", dateOfBirth = "০৯/০৬/১৯৮০", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w1-h8", serialNo = "৮", name = "জামাল", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "৬৯২৩৭৭৬৬৪", dateOfBirth = "০১/০১/১৯৮৩", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-2",
                certificateNo = "২০২৬৫৪১১৮১২০০০০৩",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "সফর আলী",
                fatherOrHusbandName = "মৃত নাজির ওরফে আলী নোয়াজ",
                motherName = "পরিচ্ছারের নেছা",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "১৮-১১-১৯০৫ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০১", "১০২", "১০৩"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩"),
                notes = "SYNTHETIC_TEST_DATA (ক্রমিক ১): মৃত সফর আলী",
                heirs = listOf(
                    HeirRecord(id = "w2-h1", serialNo = "১", name = "ফুলবানু", relationship = "স্ত্রী", shareHissa = 0.1250, shareFractionLabel = "২ আনা (১/৮)", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w2-h2", serialNo = "২", name = "কামদার আলী", relationship = "পুত্র", shareHissa = 0.4375, shareFractionLabel = "৭ আনা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w2-h3", serialNo = "৩", name = "সিরাজ আলী", relationship = "পুত্র", shareHissa = 0.4375, shareFractionLabel = "৭ আনা", remarks = "মৃত", isDeceased = true)
                )
            ),
            WarishCertificate(
                id = "major-warish-3",
                certificateNo = "২০২৬৫৪১১৮১২০০০০১",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "সিরাজ আলী",
                fatherOrHusbandName = "মৃত সফর আলী",
                motherName = "মৃত আলবরের মা",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "০১-০২-১৯৭১ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০২", "১০৫", "১০৯"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩", "৫২১"),
                notes = "SYNTHETIC_TEST_DATA (ক্রমিক ২): মৃত সিরাজ আলী",
                heirs = listOf(
                    HeirRecord(id = "w3-h1", serialNo = "১", name = "আইমনের নেছা", relationship = "স্ত্রী", shareHissa = 0.1250, shareFractionLabel = "২ আনা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w3-h2", serialNo = "২", name = "ফিরুজ মিয়া", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w3-h3", serialNo = "৩", name = "নোয়াব মিয়া", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "২৩৯৬৩০৮৯৪৮", dateOfBirth = "১৮/১০/১৯৩২", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w3-h4", serialNo = "৪", name = "মোঃ জাহের মিয়া", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w3-h5", serialNo = "৫", name = "মোঃ জাকির হোসেন", relationship = "পুত্র", shareHissa = 0.1750, shareFractionLabel = "২ আনা ১৬ গণ্ডা", nidOrBirthCert = "৫৫৪৭৬৭৯৯২০", dateOfBirth = "১২/০৮/১৯৬৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w3-h6", serialNo = "৬", name = "আনোয়ারা বেগম", relationship = "কন্যা", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", nidOrBirthCert = "৫৫৫৬৯৮২৬৫২", dateOfBirth = "১০/১০/১৯৫৯", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w3-h7", serialNo = "৭", name = "জাহানারা বেগম", relationship = "কন্যা", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", nidOrBirthCert = "৬৮৫৬০৮৬৬৬২", dateOfBirth = "০১/১১/১৯৬৭", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-4",
                certificateNo = "২০২৬৫৪১১৮১২০০০০০৮",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "মোঃ জাহের মিয়া",
                fatherOrHusbandName = "মৃত সিরাজ আলী",
                motherName = "মৃত আইমনের নেছা",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "১৩-০৬-২০০৬ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০২", "১০৩"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৫২১"),
                notes = "SYNTHETIC_TEST_DATA: জাহের মিয়া",
                heirs = listOf(
                    HeirRecord(id = "w4-h1", serialNo = "১", name = "রোশেনা বেগম", relationship = "১ম স্ত্রী", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "১০২৩২৪৯১৬০", dateOfBirth = "১০/১১/১৯৫২", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h2", serialNo = "২", name = "লাভলী আক্তার", relationship = "কন্যা", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "২৩৫২২৪৬৩০৩", dateOfBirth = "০৯/০৬/১৯৭৫", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h3", serialNo = "৩", name = "মোঃ ফোরকান মিয়া", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৭৩৩৭৫৭৮৭৩১", dateOfBirth = "০১/০১/১৯৮১", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h4", serialNo = "৪", name = "মোঃ ইমান হোসেন", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "১০১৮৭৮৪৭৪২", dateOfBirth = "০১/০১/১৯৮৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h5", serialNo = "৫", name = "তাছলিমা বেগম", relationship = "কন্যা", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "৩২৫৭১২২৬৪২", dateOfBirth = "১০/০৬/১৯৮৯", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h6", serialNo = "৬", name = "মোঃ জয়দুল হোসেন", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৮৬৮৩২০৭৯১৫", dateOfBirth = "০১/০১/১৯৯৩", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h7", serialNo = "৭", name = "মানছুরা বেগম", relationship = "কন্যা", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "১৯৫৭৯১১০৭৪", dateOfBirth = "২৬/১২/১৯৯৭", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h8", serialNo = "৮", name = "হনুফা বেগম", relationship = "২য় স্ত্রী", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "৭০২৩৯৫৫৪৬৩", dateOfBirth = "০৭/০৫/১৯৬২", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h9", serialNo = "৯", name = "মোসাঃ জোসনা বেগম", relationship = "কন্যা", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "৯১৩৭৫৩৭৭০১", dateOfBirth = "০৭/০৫/১৯৭৫", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h10", serialNo = "১০", name = "জালাল মিয়া", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৬৯২৩৫৭৯১৯৭", dateOfBirth = "২৪/১২/১৯৯৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h11", serialNo = "১১", name = "মোঃ বোরহান", relationship = "পুত্র", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "১২১৮৫২২৯২০৫", dateOfBirth = "১২/০৩/১৯৮৮", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w4-h12", serialNo = "১২", name = "মোঃ নিজাম উদ্দিন", relationship = "পুত্র", shareHissa = 0.0625, shareFractionLabel = "১ আনা", nidOrBirthCert = "১০৩২১৬৬২০৩", dateOfBirth = "১০/০৩/১৯৮৮", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-5",
                certificateNo = "২০২৬১২১৪৮০০০০২১",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "আব্দুল কাদির",
                fatherOrHusbandName = "কামদার আলী",
                motherName = "জাহেরা খাতুন",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "উল্লেখ নেই (সনদে খালি)",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১১১", "১১২", "১১৩"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩", "২৪৭"),
                notes = "SYNTHETIC_TEST_DATA: আব্দুল কাদির",
                heirs = listOf(
                    HeirRecord(id = "w5-h1", serialNo = "১", name = "শাহারা খাতুন", relationship = "স্ত্রী", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৫৫৪৬৩১৫৪৫৭", dateOfBirth = "১৮/১১/১৯৩৭", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w5-h2", serialNo = "২", name = "খলিল", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", nidOrBirthCert = "৮৭২৩৩৪৩২৩৫", dateOfBirth = "০১/০৩/১৯৭৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h3", serialNo = "৩", name = "আনু", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", nidOrBirthCert = "৩২৬৭২৭৭৪৭৯", dateOfBirth = "১৮/১০/১৯৭৯", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h4", serialNo = "৪", name = "তাজু মিয়া", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", nidOrBirthCert = "৪২২৩০৫৩৬৪৮", dateOfBirth = "০২/০২/১৯৭৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h5", serialNo = "৫", name = "মোহাম্মদ করিম", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", nidOrBirthCert = "১১৭৫০০০৭৮২২২০৩৬৪", dateOfBirth = "০১/০১/১৯৭৫", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h6", serialNo = "৬", name = "সুহেল মিয়া", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w5-h7", serialNo = "৭", name = "পারভীনা বেগম", relationship = "কন্যা", shareHissa = 0.0486, shareFractionLabel = "১৫ গণ্ডা ২ কড়া", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w5-h8", serialNo = "৮", name = "লুক্কুন মিয়া", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w5-h9", serialNo = "৯", name = "মোঃ সাক্কন", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", nidOrBirthCert = "৭৭৪৭৩২১৮৬৯", dateOfBirth = "০১/০১/১৯৯২", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h10", serialNo = "১০", name = "সারমিন আক্তার", relationship = "কন্যা", shareHissa = 0.0486, shareFractionLabel = "১৫ গণ্ডা ২ কড়া", nidOrBirthCert = "১৯৯০২১১৮৫৪৮০০০২৬৭", dateOfBirth = "০১/০১/১৯৯০", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w5-h11", serialNo = "১১", name = "মোঃ মনির", relationship = "পুত্র", shareHissa = 0.0972, shareFractionLabel = "১ আনা ১১ গণ্ডা", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-6",
                certificateNo = "২০২৬১২১৪৮০০০০০৫",
                certificateDate = "১৬-০৯-২০২৬",
                deceasedName = "নোয়াব মিয়া",
                fatherOrHusbandName = "মৃত সিরাজ আলী",
                motherName = "মৃত বানু বেগম",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "১৭-০৬-২০২৬ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০২", "১০৫"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৫২১"),
                notes = "SYNTHETIC_TEST_DATA: নোয়াব মিয়া",
                heirs = listOf(
                    HeirRecord(id = "w6-h1", serialNo = "১", name = "আমেনা খাতুন", relationship = "স্ত্রী", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৯৫৭৩১৪১৮৮৫", dateOfBirth = "১০/১০/১৯৪২", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w6-h2", serialNo = "২", name = "মোঃ রহিম মিয়া", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৫১০০১২৯২৫২", dateOfBirth = "১৩/০৪/১৯৬৭", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h3", serialNo = "৩", name = "মোঃ আব্দুল করিম", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৫৮২৮৩০৭৯১৬৬৩০", dateOfBirth = "২৩/০৮/১৯৮০", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h4", serialNo = "৪", name = "মোঃ বিল্লাল হোসেন", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৩০০০৩৪৫৩৫৬", dateOfBirth = "২৮/০৩/১৯৮১", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h5", serialNo = "৫", name = "জিলানী মিয়া", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "২৪৮৬২৬৮৪৮৫", dateOfBirth = "১০/১০/১৯৭৫", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h6", serialNo = "৬", name = "আব্দুল কাইয়ুম", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৬৮১৯০০৩৯২০", dateOfBirth = "০৪/০৪/১৯৮৪", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h7", serialNo = "৭", name = "জসিম উদ্দিন", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "৫১০২০১৮৬০৪", dateOfBirth = "০৩/০৫/১৯৯২", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w6-h8", serialNo = "৮", name = "মনির হোসেন", relationship = "পুত্র", shareHissa = 0.1250, shareFractionLabel = "২ আনা", nidOrBirthCert = "১৫৮০৪৮১৮৬০", dateOfBirth = "০১/০৭/১৯৮৮", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-7",
                certificateNo = "কাই(দঃ)/ইউপি/২০২৩/০৬",
                certificateDate = "০৬-০৯-২০২৩",
                deceasedName = "আঃ আলীম",
                fatherOrHusbandName = "কামদার আলী",
                motherName = "উল্লেখ নেই",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "উল্লেখ নেই",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০১", "১১৮"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩"),
                notes = "SYNTHETIC_TEST_DATA: আঃ আলীম",
                heirs = listOf(
                    HeirRecord(id = "w7-h1", serialNo = "০১", name = "মৃত আমেনা খাতুন", relationship = "স্ত্রী", shareHissa = 0.0417, shareFractionLabel = "১৩ গণ্ডা ১ কড়া", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w7-h2", serialNo = "০২", name = "সাহেদা খাতুন", relationship = "স্ত্রী", shareHissa = 0.0417, shareFractionLabel = "১৩ গণ্ডা ১ কড়া", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h3", serialNo = "০৩", name = "মোছাঃ হোসনে আরা বেগম", relationship = "স্ত্রী", shareHissa = 0.0416, shareFractionLabel = "১৩ গণ্ডা ১ কড়া", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h4", serialNo = "০৪", name = "মৃত আব্দুর রশিদ", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w7-h5", serialNo = "০৫", name = "মৃত আব্দুল লতিফ", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w7-h6", serialNo = "০৬", name = "আম্বিয়া বেগম", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h7", serialNo = "০৭", name = "মৃত জামেদা খাতুন", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "মৃত", isDeceased = true),
                    HeirRecord(id = "w7-h8", serialNo = "০৮", name = "মোছাঃ নাজমা বেগম", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h9", serialNo = "০৯", name = "মোছাঃ খুদেজা বেগম", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h10", serialNo = "১০", name = "মোঃ সেলিম মিয়া", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h11", serialNo = "১১", name = "মোঃ শামীম মিয়া", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h12", serialNo = "১২", name = "মোছাঃ মাহমুদা বেগম", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h13", serialNo = "১৩", name = "মোঃ মমিন মিয়া", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h14", serialNo = "১৪", name = "কহিনুর আক্তার", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h15", serialNo = "১৫", name = "মোছাঃ সালমা আক্তার", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h16", serialNo = "১৬", name = "মোছাঃ রহিমা আক্তার", relationship = "কন্যা", shareHissa = 0.04375, shareFractionLabel = "১৪ গণ্ডা", remarks = "জীবিত", isDeceased = false),
                    HeirRecord(id = "w7-h17", serialNo = "১৭", name = "মোঃ আমিনুল ইসলাম", relationship = "পুত্র", shareHissa = 0.0875, shareFractionLabel = "১ আনা ৮ গণ্ডা", remarks = "জীবিত", isDeceased = false)
                )
            ),
            WarishCertificate(
                id = "major-warish-8",
                certificateNo = "৪৬৫৪০৬",
                certificateDate = "০৯-০৮-২০২৩",
                deceasedName = "মৃত মোঃ ফিরুজ মিয়া",
                fatherOrHusbandName = "মৃত মোঃ সিরাজ আলী",
                motherName = "রোকেয়া খাতুন",
                villageOrArea = "গোয়ালী",
                unionOrWard = "কাইতলা (দক্ষিণ), ওয়ার্ড: ০৮",
                postOffice = "কাইতলা - ৩৪৭৭",
                upazila = "নবীনগর",
                district = "ব্রাহ্মণবাড়িয়া",
                dateOfDeath = "২২-০৫-২০০৯ ইং",
                issuerDesignation = "চেয়ারম্যান, কাইতলা (দক্ষিণ) ইউনিয়ন পরিষদ",
                targetDagNumbers = listOf("১০২", "১০৫", "১০৯"),
                targetMouza = "গোয়ালী",
                targetKhatianNumbers = listOf("৪০৩", "২৪৭", "৫২১"),
                notes = "SYNTHETIC_TEST_DATA: মোঃ টিটু",
                heirs = listOf(
                    HeirRecord(id = "w8-h1", serialNo = "১", name = "মোঃ টিটু", relationship = "পুত্র (পিতা ও মাতার ওয়ারিশ)", shareHissa = 1.0000, shareFractionLabel = "১৬ আনা", nidOrBirthCert = "৬০২৫৮৪১৭৩", dateOfBirth = "০৯/০৬/১৯৮০", remarks = "জীবিত", isDeceased = false)
                )
            )
        )
    }
}
