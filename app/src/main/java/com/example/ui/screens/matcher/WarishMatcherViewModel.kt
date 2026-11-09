package com.example.ui.screens.matcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LandDocumentEntity
import com.example.data.repository.InheritorRepository
import com.example.data.repository.LandRecordRepository
import com.example.domain.gis.CadastralMapDetector
import com.example.domain.gis.CadastralParcel
import com.example.domain.gis.CadastralSheetData
import com.example.domain.matcher.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WarishMatcherUiState(
    val majorWarishList: List<WarishCertificate> = createPermanentMajorWarishList(),
    val selectedCertificateIndex: Int = 0,
    val currentWarish: WarishCertificate = createDefaultWarish(),
    val matchReport: WarishMatchReport? = null,
    val cadastralSheet: CadastralSheetData? = null,
    val selectedParcel: CadastralParcel? = null,
    val selectedTab: Int = 0, // 0: Matcher & Certificate, 1: Cadastral Map, 2: Forensic Audit
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val surveyRecordsCount: Int = 0
)

/**
 * স্থায়ী মেজর ওয়ারিশ সনদসমূহের তালিকা (Permanent Major Warish Certificates)
 * গ্রাম: গোয়ালী, ওয়ার্ড: ০৮, ডাকঘর: কাইতলা - ৩৪৭৭, ইউনিয়ন: কাইতলা (দক্ষিণ), উপজেলা: নবীনগর, জেলা: ব্রাহ্মণবাড়িয়া।
 */
fun createPermanentMajorWarishList(): List<WarishCertificate> {
    return listOf(
        // ১. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬৫৪১১৮১২০০০০০২) - মৃত: ফিরুজ মিয়া
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
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৩): মৃত ফিরুজ মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত আইমনের নেছা।",
            heirs = listOf(
                HeirRecord(
                    id = "w1-h1",
                    serialNo = "১",
                    name = "তাসলিমা বেগম",
                    relationship = "১ম স্ত্রী",
                    shareHissa = 0.0625,
                    shareFractionLabel = "১ আনা (১/১৬)",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w1-h2",
                    serialNo = "২",
                    name = "রফিকুল ইসলাম",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "৭৩০৬০১২৩০৮",
                    dateOfBirth = "০৬/০৫/১৯৭৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h3",
                    serialNo = "৩",
                    name = "রানু কাইয়ুম",
                    relationship = "কন্যা",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "৩১৭৬০৮২২১৭",
                    dateOfBirth = "০৯/০৪/১৯৭২",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h4",
                    serialNo = "৪",
                    name = "সুরাইয়া বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h5",
                    serialNo = "৫",
                    name = "রোকেয়া খাতুন",
                    relationship = "২য় স্ত্রী",
                    shareHissa = 0.0625,
                    shareFractionLabel = "১ আনা (১/১৬)",
                    nidOrBirthCert = "১৯৪৬৫৩০৩০৮",
                    dateOfBirth = "১৮/১০/১৯৫৭",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h6",
                    serialNo = "৬",
                    name = "খোকন মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "৬৯২০১৭৭৮৩",
                    dateOfBirth = "১০/০২/১৯৭৩",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h7",
                    serialNo = "৭",
                    name = "মোঃ টিটু",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "৬০২৫৮৪১৭৩",
                    dateOfBirth = "০৯/০৬/১৯৮০",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w1-h8",
                    serialNo = "৮",
                    name = "জামাল",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "৬৯২৩৭৭৬৬৪",
                    dateOfBirth = "০১/০১/১৯৮৩",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ২. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬৫৪১১৮১২০০০০৩) - মৃত: সফর আলী
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
            targetDagNumbers = listOf("১০১", "১০২", "১০৪"),
            targetMouza = "গোয়ালী",
            targetKhatianNumbers = listOf("৪০৩"),
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ১ - মূল পূর্বপুরুষ সিএস রেকর্ড): মৃত সফর আলী, পিতা: মৃত নাজির ওরফে আলী নোয়াজ, মাতা: পরিচ্ছারের নেছা।",
            heirs = listOf(
                HeirRecord(
                    id = "w2-h1",
                    serialNo = "১",
                    name = "ফুলবানু",
                    relationship = "স্ত্রী",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w2-h2",
                    serialNo = "২",
                    name = "কামদার আলী",
                    relationship = "পুত্র",
                    shareHissa = 0.4375,
                    shareFractionLabel = "৭ আনা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w2-h3",
                    serialNo = "৩",
                    name = "সিরাজ আলী",
                    relationship = "পুত্র",
                    shareHissa = 0.4375,
                    shareFractionLabel = "৭ আনা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                )
            )
        ),

        // ৩. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬৫৪১১৮১২০০০০১) - মৃত: সিরাজ আলী
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
            targetDagNumbers = listOf("১০২", "১০৫", "১০৮"),
            targetMouza = "গোয়ালী",
            targetKhatianNumbers = listOf("৪০৩", "২৪৭"),
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ২): মৃত সিরাজ আলী, পিতা: মৃত সফর আলী, মাতা: মৃত আলবরের মা।",
            heirs = listOf(
                HeirRecord(
                    id = "w3-h1",
                    serialNo = "১",
                    name = "আইমনের নেছা",
                    relationship = "স্ত্রী",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w3-h2",
                    serialNo = "২",
                    name = "ফিরুজ মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w3-h3",
                    serialNo = "৩",
                    name = "নোয়াব মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "২৩৯৬৩০৮৯৪৮",
                    dateOfBirth = "১৮/১০/১৯৩২",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w3-h4",
                    serialNo = "৪",
                    name = "মোঃ জাহের মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w3-h5",
                    serialNo = "৫",
                    name = "মোঃ জাকির হোসেন",
                    relationship = "পুত্র",
                    shareHissa = 0.1750,
                    shareFractionLabel = "২ আনা ১৬ গণ্ডা",
                    nidOrBirthCert = "৫৫৪৭৬৭৯৯২০",
                    dateOfBirth = "১২/০৮/১৯৬৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w3-h6",
                    serialNo = "৬",
                    name = "আনোয়ারা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "৫৫৫৬৯৮২৬৫২",
                    dateOfBirth = "১০/১০/১৯৫৯",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w3-h7",
                    serialNo = "৭",
                    name = "জাহানারা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "৬৮৫৬০৮৬৬৬২",
                    dateOfBirth = "০১/১১/১৯৬৭",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ৪. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬৫৪১১৮১২০০০০০৮) - মৃত: মোঃ জাহের মিয়া
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
            targetDagNumbers = listOf("১০৩", "১০৭", "১১০"),
            targetMouza = "গোয়ালী",
            targetKhatianNumbers = listOf("৫২১"),
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৪): মৃত মোঃ জাহের মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত আইমনের নেছা।",
            heirs = listOf(
                HeirRecord(
                    id = "w4-h1",
                    serialNo = "১",
                    name = "রোশেনা বেগম",
                    relationship = "১ম স্ত্রী",
                    shareHissa = 0.0625,
                    shareFractionLabel = "১ আনা (১/১৬)",
                    nidOrBirthCert = "১০২৩২৪৯১৬০",
                    dateOfBirth = "১০/১১/১৯৫২",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h2",
                    serialNo = "২",
                    name = "লাভলী আক্তার",
                    relationship = "কন্যা",
                    shareHissa = 0.0583,
                    shareFractionLabel = "১৮ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "২৩৫২২৪৬৩০৩",
                    dateOfBirth = "০৯/০৬/১৯৭৫",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h3",
                    serialNo = "৩",
                    name = "মোঃ ফোরকান মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1167,
                    shareFractionLabel = "১ আনা ১৭ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "৭৩৩৭৫৭৮৭৩১",
                    dateOfBirth = "০১/০১/১৯৮১",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h4",
                    serialNo = "৪",
                    name = "মোঃ ইমান হোসেন",
                    relationship = "পুত্র",
                    shareHissa = 0.1167,
                    shareFractionLabel = "১ আনা ১৭ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "১০১৮৭৮৪৭৪২",
                    dateOfBirth = "০১/০১/১৯৮৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h5",
                    serialNo = "৫",
                    name = "তাছলিমা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0583,
                    shareFractionLabel = "১৮ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "৩২৫৭১২২৬৪২",
                    dateOfBirth = "১০/০৬/১৯৮৯",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h6",
                    serialNo = "৬",
                    name = "মোঃ জয়দুল হোসেন",
                    relationship = "পুত্র",
                    shareHissa = 0.1167,
                    shareFractionLabel = "১ আনা ১৭ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "৮৬৮৩২০৭৯১৫",
                    dateOfBirth = "০১/০১/১৯৯৩",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h7",
                    serialNo = "৭",
                    name = "মানছুরা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0583,
                    shareFractionLabel = "১৮ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "১৯৫৭৯১১০৭৪",
                    dateOfBirth = "২৬/১২/১৯৯৭",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h8",
                    serialNo = "৮",
                    name = "হনুফা বেগম",
                    relationship = "২য় স্ত্রী",
                    shareHissa = 0.0625,
                    shareFractionLabel = "১ আনা (১/১৬)",
                    nidOrBirthCert = "৭০২৩৯৫৫৪৬৩",
                    dateOfBirth = "০৭/০৫/১৯৬২",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h9",
                    serialNo = "৯",
                    name = "মোসাঃ জোসনা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0583,
                    shareFractionLabel = "১৮ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "৯১৩৭৫৩৭৭০১",
                    dateOfBirth = "০৭/০৫/১৯৭৫",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h10",
                    serialNo = "১০",
                    name = "জালাল মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1167,
                    shareFractionLabel = "১ আনা ১৭ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "৬৯২৩৫৭৯১৯৭",
                    dateOfBirth = "২৪/১২/১৯৯৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h11",
                    serialNo = "১১",
                    name = "মোঃ বোরহান",
                    relationship = "পুত্র",
                    shareHissa = 0.1165,
                    shareFractionLabel = "১ আনা ১৭ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "১২১৮৫২২৯২০৫",
                    dateOfBirth = "১২/০৩/১৯৮৮",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w4-h12",
                    serialNo = "১২",
                    name = "মোঃ নিজাম উদ্দিন",
                    relationship = "পুত্র",
                    shareHissa = 0.0585,
                    shareFractionLabel = "১৮ গণ্ডা ৩ কড়া",
                    nidOrBirthCert = "১০৩২১৬৬২০৩",
                    dateOfBirth = "১০/০৩/১৯৮৮",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ৫. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬১২১৪৮০০০০২১) - মৃত: আব্দুল কাদির
        WarishCertificate(
            id = "major-warish-5",
            certificateNo = "২০২৬১২১৪৮০০০০২১",
            certificateDate = "১৫-১০-২০২৬",
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
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৭): মৃত আব্দুল কাদির, পিতা: কামদার আলী, মাতা: জাহেরা খাতুন।",
            heirs = listOf(
                HeirRecord(
                    id = "w5-h1",
                    serialNo = "১",
                    name = "শাহারা খাতুন",
                    relationship = "স্ত্রী",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৫৫৪৬৩১৫৪৫৭",
                    dateOfBirth = "১৮/১১/১৯৩৭",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w5-h2",
                    serialNo = "২",
                    name = "খলিল",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "৮৭২৩৩৪৩২৩৫",
                    dateOfBirth = "০১/০৩/১৯৭৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h3",
                    serialNo = "৩",
                    name = "আনু",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "৩২৬৭২৭৭৪৭৯",
                    dateOfBirth = "১৮/১০/১৯৭৯",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h4",
                    serialNo = "৪",
                    name = "তাজু মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "৪২২৩০৫৩৬৪৮",
                    dateOfBirth = "০২/০২/১৯৭৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h5",
                    serialNo = "৫",
                    name = "মোহাম্মদ করিম",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "১১৭৫০০০৭৮২২২০৩৬৪",
                    dateOfBirth = "০১/০১/১৯৭৫",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h6",
                    serialNo = "৬",
                    name = "সুহেল মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w5-h7",
                    serialNo = "৭",
                    name = "পারভীনা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.0486,
                    shareFractionLabel = "১৫ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w5-h8",
                    serialNo = "৮",
                    name = "লুক্কুন মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w5-h9",
                    serialNo = "৯",
                    name = "মোঃ সাক্কন",
                    relationship = "পুত্র",
                    shareHissa = 0.0972,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "৭৭৪৭৩২১৮৬৯",
                    dateOfBirth = "০১/০১/১৯৯২",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h10",
                    serialNo = "১০",
                    name = "সারমিন আক্তার",
                    relationship = "কন্যা",
                    shareHissa = 0.0486,
                    shareFractionLabel = "১৫ গণ্ডা ২ কড়া",
                    nidOrBirthCert = "১৯৯০২১১৮৫৪৮০০০২৬৭",
                    dateOfBirth = "০১/০১/১৯৯০",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w5-h11",
                    serialNo = "১১",
                    name = "মোঃ মনির",
                    relationship = "পুত্র",
                    shareHissa = 0.0974,
                    shareFractionLabel = "১ আনা ১১ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ৬. ওয়ারিশ সনদ বিবরণ (সনদ নং: ২০২৬১২১৪৮০০০০০৫) - মৃত: নোয়াব মিয়া
        WarishCertificate(
            id = "major-warish-6",
            certificateNo = "২০২৬১২১৪৮০০০০০৫",
            certificateDate = "১৭-০৯-২০২৬",
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
            targetDagNumbers = listOf("১০২", "১০৬", "১০৯"),
            targetMouza = "গোয়ালী",
            targetKhatianNumbers = listOf("৪০৩", "৫২১"),
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৫): মৃত নোয়াব মিয়া, পিতা: মৃত সিরাজ আলী, মাতা: মৃত বানু বেগম।",
            heirs = listOf(
                HeirRecord(
                    id = "w6-h1",
                    serialNo = "১",
                    name = "আমেনা খাতুন",
                    relationship = "স্ত্রী",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৯৫৭৩১৪১৮৮৫",
                    dateOfBirth = "১০/১০/১৯৪২",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w6-h2",
                    serialNo = "২",
                    name = "মোঃ রহিম মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৫১০০১২৯২৫২",
                    dateOfBirth = "১৩/০৪/১৯৬৭",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h3",
                    serialNo = "৩",
                    name = "মোঃ আব্দুল করিম",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৫৮২৮৩০৭৯১৬৬৩০",
                    dateOfBirth = "২৩/০৮/১৯৮০",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h4",
                    serialNo = "৪",
                    name = "মোঃ বিল্লাল হোসেন",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৩০০০৩৪৫৩৫৬",
                    dateOfBirth = "২৮/০৩/১৯৮১",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h5",
                    serialNo = "৫",
                    name = "জিলানী মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "২৪৮৬২৬৮৪৮৫",
                    dateOfBirth = "১০/১০/১৯৭৫",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h6",
                    serialNo = "৬",
                    name = "আব্দুল কাইয়ুম",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৬৮১৯০০৩৯২০",
                    dateOfBirth = "০৪/০৪/১৯৮৪",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h7",
                    serialNo = "৭",
                    name = "জসিম উদ্দিন",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "৫১০২০১৮৬০৪",
                    dateOfBirth = "০৩/০৫/১৯৯২",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w6-h8",
                    serialNo = "৮",
                    name = "মনির হোসেন",
                    relationship = "পুত্র",
                    shareHissa = 0.1250,
                    shareFractionLabel = "২ আনা (১/৮)",
                    nidOrBirthCert = "১৫৮০৪৮১৮৬০",
                    dateOfBirth = "০১/০৭/১৯৮৮",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ৭. ওয়ারিশ সনদ বিবরণ (সনদ নং: কাই(দঃ)/ইউপি/২০২৩/০৬) - মৃত: আঃ আলীম
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
            targetDagNumbers = listOf("১০১", "১১১", "১১৮"),
            targetMouza = "গোয়ালী",
            targetKhatianNumbers = listOf("৪০৩", "২৪৭"),
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৬): মৃত আঃ আলীম, পিতা: কামদার আলী, মাতা: উল্লেখ নেই। ১৭ জন ওয়ারিশ।",
            heirs = listOf(
                HeirRecord(
                    id = "w7-h1",
                    serialNo = "০১",
                    name = "মৃত আমেনা খাতুন",
                    relationship = "স্ত্রী",
                    shareHissa = 0.0417,
                    shareFractionLabel = "১৩ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w7-h2",
                    serialNo = "০২",
                    name = "সাহেদা খাতুন",
                    relationship = "স্ত্রী",
                    shareHissa = 0.0417,
                    shareFractionLabel = "১৩ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h3",
                    serialNo = "০৩",
                    name = "মোছাঃ হোসনে আরা বেগম",
                    relationship = "স্ত্রী",
                    shareHissa = 0.0416,
                    shareFractionLabel = "১৩ গণ্ডা ১ কড়া",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h4",
                    serialNo = "০৪",
                    name = "মৃত আব্দুর রশিদ",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w7-h5",
                    serialNo = "০৫",
                    name = "মৃত আব্দুল লতিফ",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w7-h6",
                    serialNo = "০৬",
                    name = "আম্বিয়া বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h7",
                    serialNo = "০৭",
                    name = "মৃত জামেদা খাতুন",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "মৃত",
                    isDeceased = true
                ),
                HeirRecord(
                    id = "w7-h8",
                    serialNo = "০৮",
                    name = "মোছাঃ নাজমা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h9",
                    serialNo = "০৯",
                    name = "মোছাঃ খুদেজা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h10",
                    serialNo = "১০",
                    name = "মোঃ সেলিম মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h11",
                    serialNo = "১১",
                    name = "মোঃ শামীম মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h12",
                    serialNo = "১২",
                    name = "মোছাঃ মাহমুদা বেগম",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h13",
                    serialNo = "১৩",
                    name = "মোঃ মমিন মিয়া",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h14",
                    serialNo = "১৪",
                    name = "কহিনুর আক্তার",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h15",
                    serialNo = "১৫",
                    name = "মোছাঃ সালমা আক্তার",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h16",
                    serialNo = "১৬",
                    name = "মোছাঃ রহিমা আক্তার",
                    relationship = "কন্যা",
                    shareHissa = 0.04375,
                    shareFractionLabel = "১৪ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                ),
                HeirRecord(
                    id = "w7-h17",
                    serialNo = "১৭",
                    name = "মোঃ আমিনুল ইসলাম",
                    relationship = "পুত্র",
                    shareHissa = 0.0875,
                    shareFractionLabel = "১ আনা ৮ গণ্ডা",
                    nidOrBirthCert = "-",
                    dateOfBirth = "-",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        ),

        // ৮. ওয়ারিশ সনদ বিবরণ (সনদ নং: ৪৬৫৪০৬) - মৃত: মৃত মোঃ ফিরুজ মিয়া ও মাতা রোকেয়া খাতুন
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
            notes = "স্থায়ী মেজর ওয়ারিশ সনদ (ক্রমিক ৮ - সনদ নং ৪৬৫৪০৬): মৃত মোঃ ফিরুজ মিয়া, পিতা: মৃত মোঃ সিরাজ আলী, মাতা: রোকেয়া খাতুন।",
            heirs = listOf(
                HeirRecord(
                    id = "w8-h1",
                    serialNo = "১",
                    name = "মোঃ টিটু",
                    relationship = "পুত্র (পিতা ও মাতার ওয়ারিশ)",
                    shareHissa = 1.0000,
                    shareFractionLabel = "১৬ আনা (একক প্রত্যয়ন)",
                    nidOrBirthCert = "৬০২৫৮৪১৭৩",
                    dateOfBirth = "০৯/০৬/১৯৮০",
                    remarks = "জীবিত",
                    isDeceased = false
                )
            )
        )
    )
}

fun createDefaultWarish(): WarishCertificate {
    return createPermanentMajorWarishList().first()
}

class WarishMatcherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LandRecordRepository(application)
    val inheritorRepository = InheritorRepository(application)
    private val inheritanceMatcher = InheritanceMatcher()
    private val mapDetector = CadastralMapDetector(surveyType = "CS")

    private val _uiState = MutableStateFlow(WarishMatcherUiState())
    val uiState: StateFlow<WarishMatcherUiState> = _uiState.asStateFlow()

    init {
        // Seed Room Database with all 8 Master Warish Certificates if empty or outdated
        viewModelScope.launch {
            inheritorRepository.seedMasterDatasetIfEmpty(createPermanentMajorWarishList())
        }

        // Reactively observe Master Dataset changes from Room DB
        viewModelScope.launch {
            inheritorRepository.masterCertificatesFlow.collect { certs ->
                if (certs.isNotEmpty()) {
                    val currentIndex = _uiState.value.selectedCertificateIndex.coerceIn(certs.indices)
                    _uiState.value = _uiState.value.copy(
                        majorWarishList = certs,
                        selectedCertificateIndex = currentIndex,
                        currentWarish = certs[currentIndex]
                    )
                    runMatching()
                }
            }
        }

        runMatching()
    }

    fun selectCertificateIndex(index: Int) {
        val list = _uiState.value.majorWarishList
        if (index in list.indices) {
            _uiState.value = _uiState.value.copy(
                selectedCertificateIndex = index,
                currentWarish = list[index]
            )
            runMatching()
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun selectParcel(parcel: CadastralParcel?) {
        _uiState.value = _uiState.value.copy(selectedParcel = parcel)
    }

    fun updateDeceasedName(name: String) {
        val updated = _uiState.value.currentWarish.copy(deceasedName = name)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetDags(dagsText: String) {
        val dags = dagsText.split(",", " ", "।")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val updated = _uiState.value.currentWarish.copy(targetDagNumbers = dags)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun updateTargetMouza(mouza: String) {
        val updated = _uiState.value.currentWarish.copy(targetMouza = mouza)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
    }

    fun addHeir(name: String, relation: String, share: Double, shareLabel: String) {
        val nextSerial = com.example.data.model.BengaliNumberUtils.toBengaliDigits((_uiState.value.currentWarish.heirs.size + 1).toLong())
        val newHeir = HeirRecord(
            serialNo = nextSerial,
            name = name,
            relationship = relation,
            shareHissa = share,
            shareFractionLabel = shareLabel,
            remarks = "জীবিত"
        )
        val currentHeirs = _uiState.value.currentWarish.heirs.toMutableList()
        currentHeirs.add(newHeir)
        val updated = _uiState.value.currentWarish.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun removeHeir(heirId: String) {
        val currentHeirs = _uiState.value.currentWarish.heirs.filter { it.id != heirId }
        val updated = _uiState.value.currentWarish.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun updateHeirRecord(updatedHeir: HeirRecord) {
        val currentHeirs = _uiState.value.currentWarish.heirs.map {
            if (it.id == updatedHeir.id) updatedHeir else it
        }
        val updated = _uiState.value.currentWarish.copy(heirs = currentHeirs)
        _uiState.value = _uiState.value.copy(currentWarish = updated)
        viewModelScope.launch {
            inheritorRepository.saveOrUpdateCertificate(updated)
        }
        runMatching()
    }

    fun resetToSample() {
        val permanentList = createPermanentMajorWarishList()
        val idx = _uiState.value.selectedCertificateIndex.coerceIn(permanentList.indices)
        _uiState.value = _uiState.value.copy(
            majorWarishList = permanentList,
            selectedCertificateIndex = idx,
            currentWarish = permanentList[idx]
        )
        viewModelScope.launch {
            inheritorRepository.seedMasterDatasetIfEmpty(permanentList, forceReset = true)
        }
        runMatching()
    }

    fun runMatching() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val surveyDocs: List<LandDocumentEntity> = try {
                repository.allDocuments.first()
            } catch (_: Exception) {
                emptyList()
            }

            val currentWarish = _uiState.value.currentWarish

            // 1. Run Inheritance Matcher
            val report = inheritanceMatcher.matchWarishToSurveys(currentWarish, surveyDocs)

            // 2. Generate Cadastral Sheet with Highlights on matched target Dags
            val sheetData = mapDetector.detectAndHighlightParcels(
                targetDags = currentWarish.targetDagNumbers,
                mouzaHint = currentWarish.targetMouza.ifBlank { "গোয়ালী" }
            )

            _uiState.value = _uiState.value.copy(
                matchReport = report,
                cadastralSheet = sheetData,
                selectedParcel = sheetData.parcels.firstOrNull { it.isTargetMatched },
                isLoading = false,
                surveyRecordsCount = surveyDocs.size,
                statusMessage = "স্থায়ী মেজর ওয়ারিশনামা (${currentWarish.certificateNo}) ও জরিপ খতিয়ান মিলকরণ সম্পন্ন (${report.matchedPlots.size} টি দাগ বিশ্লেষিত)"
            )
        }
    }
}
