package com.example

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.example.data.model.ConfidenceLevel
import com.example.data.model.LandRecordType
import com.example.ui.screens.ocr.LandRecordGeminiUiState
import com.example.ui.screens.ocr.LandRecordGeminiViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LandRecordGeminiViewModelTest {

    private lateinit var app: Application
    private lateinit var viewModel: LandRecordGeminiViewModel

    @Before
    fun setup() {
        app = Application()
        viewModel = ViewModelProvider.AndroidViewModelFactory(app)
            .create(LandRecordGeminiViewModel::class.java)
    }

    @Test
    fun testAndroidViewModelFactoryReflectiveInstantiation() {
        val singleArgConstructor = LandRecordGeminiViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(singleArgConstructor)
        val createdVm = ViewModelProvider.AndroidViewModelFactory(app)
            .create(LandRecordGeminiViewModel::class.java)
        assertNotNull(createdVm)
        assertEquals(LandRecordGeminiUiState.Idle, createdVm.uiState.value)
    }

    @Test
    fun testInitialUiStateIsIdle() {
        assertEquals(LandRecordGeminiUiState.Idle, viewModel.uiState.value)
        assertNull(viewModel.selectedBitmap.value)
    }

    @Test
    fun testParseGeminiResponseWithStructuredBilingualJson() {
        val sampleResponse = """
            Here is the extracted information from the Bangladesh land document:
            
            <<<JSON>>>
            {
              "recordType": "CS",
              "surveyName": "ক্যাডাস্ট্রাল সার্ভে (CS Survey)",
              "districtBengali": "ব্রাহ্মণবাড়িয়া",
              "districtEnglish": "Brahmanbaria",
              "upazilaBengali": "নবীনগর",
              "upazilaEnglish": "Nabinagar",
              "mouzaBengali": "গোয়ালী",
              "mouzaEnglish": "Goalai",
              "jlNo": "১২৪",
              "khatianNo": "৪০৩",
              "formerKhatianNo": "২৩",
              "halKhatianNo": "৪০৩",
              "dagNo": "১০২, ১০৫",
              "formerDagNo": "৫৬",
              "halDagNo": "১০২",
              "landClass": "ধানী ও নাল",
              "totalAreaAcres": "১.২০",
              "totalAreaDecimals": "১২০",
              "owners": [
                {
                  "serial": 1,
                  "nameBengali": "ফিরুজ মিয়া",
                  "nameEnglish": "Firuz Miah",
                  "fatherOrHusbandName": "মৃত সিরাজ আলী",
                  "shareHissa": "/৪ আনা (০.২৫০০)",
                  "address": "গ্রাম: গোয়ালী, ডাকঘর: কাইতলা"
                },
                {
                  "serial": 2,
                  "nameBengali": "তাসলিমা বেগম",
                  "nameEnglish": "Taslima Begum",
                  "fatherOrHusbandName": "ফিরুজ মিয়া (স্বামী)",
                  "shareHissa": "/২ আনা (০.১২৫০)",
                  "address": "গ্রাম: গোয়ালী"
                }
              ],
              "stampsAndSeals": [
                "Sub-Registry Office Nabinagar - Verified Seal",
                "সহকারী কমিশনার (ভূমি) এর রাজস্ব স্বাক্ষর"
              ],
              "marginalNotes": [
                "নামজারি কেস নং ২৪/২০০৯ অনুযায়ী জমা খারিজ"
              ],
              "rawBengaliText": "সি এস খতিয়ান। জেলা: ব্রাহ্মণবাড়িয়া, থানা: নবীনগর, মৌজা: গোয়ালী, জেএল নং: ১২৪। খতিয়ান নং: ৪০৩। দাগ নং: ১০২, ১০৫। রায়ত: ফিরুজ মিয়া, পিতা: সিরাজ আলী।",
              "rawEnglishText": "CS Khatian 403, Plots 102, 105. Sub-Registry Office Seal: Nabinagar 1998.",
              "confidenceNotes": "Clear vintage parchment, high optical legibility"
            }
            <<</JSON>>>
            
            === BENGALI TEXT (বাংলা পাঠ্যাংশ) ===
            সি এস খতিয়ান। জেলা: ব্রাহ্মণবাড়িয়া, থানা: নবীনগর, মৌজা: গোয়ালী, জেএল নং: ১২৪। খতিয়ান নং: ৪০৩। দাগ নং: ১০২, ১০৫।
            
            === ENGLISH TEXT (ইংরেজি পাঠ্যাংশ) ===
            CS Khatian 403, Plots 102, 105. Sub-Registry Office Seal: Nabinagar 1998.
        """.trimIndent()

        val parsed = viewModel.parseGeminiResponse(sampleResponse)

        assertEquals(LandRecordType.CS, parsed.recordType)
        assertEquals("ব্রাহ্মণবাড়িয়া", parsed.districtBengali)
        assertEquals("Brahmanbaria", parsed.districtEnglish)
        assertEquals("নবীনগর", parsed.upazilaBengali)
        assertEquals("Nabinagar", parsed.upazilaEnglish)
        assertEquals("গোয়ালী", parsed.mouzaBengali)
        assertEquals("Goalai", parsed.mouzaEnglish)
        assertEquals("১২৪", parsed.jlNo)
        assertEquals("৪০৩", parsed.khatianNo)
        assertEquals("১০২, ১০৫", parsed.dagNo)
        assertEquals("ধানী ও নাল", parsed.landClass)
        assertEquals("১.২০", parsed.totalAreaAcres)
        assertEquals(2, parsed.owners.size)
        assertEquals("ফিরুজ মিয়া", parsed.owners[0].nameBengali)
        assertEquals("Taslima Begum", parsed.owners[1].nameEnglish)
        assertEquals(2, parsed.stampsAndSeals.size)
        assertEquals(1, parsed.marginalNotes.size)

        assertTrue(parsed.rawBengaliText.contains("ব্রাহ্মণবাড়িয়া"))
        assertTrue(parsed.rawEnglishText.contains("Sub-Registry Office Seal"))
    }

    @Test
    fun testDomainConversionToLandRecordData() {
        val sampleResponse = """
            <<<JSON>>>
            {
              "recordType": "RS",
              "districtBengali": "ব্রাহ্মণবাড়িয়া",
              "upazilaBengali": "নবীনগর",
              "mouzaBengali": "গোয়ালী",
              "jlNo": "১২৪",
              "khatianNo": "৫২১",
              "dagNo": "১০৯",
              "landClass": "ভিটি",
              "totalAreaAcres": "০.৫০",
              "totalAreaDecimals": "৫০",
              "owners": [
                {
                  "serial": 1,
                  "nameBengali": "সফর আলী",
                  "fatherOrHusbandName": "নাজির ওরফে আলী নোয়াজ",
                  "shareHissa": "১৬ আনা (১.০০০০)"
                }
              ]
            }
            <<</JSON>>>
        """.trimIndent()

        val parsed = viewModel.parseGeminiResponse(sampleResponse)
        val landRecordData = viewModel.toLandRecordData(parsed)

        assertEquals(LandRecordType.RS, landRecordData.recordType)
        assertEquals("ব্রাহ্মণবাড়িয়া", landRecordData.district.value)
        assertEquals(ConfidenceLevel.VERIFIED, landRecordData.district.confidence)
        assertEquals("নবীনগর", landRecordData.upazilaThana.value)
        assertEquals("গোয়ালী", landRecordData.mouza.value)
        assertEquals("৫২১", landRecordData.khatianNo.value)
        assertEquals("১০৯", landRecordData.dagNo.value)
        assertEquals("ভিটি", landRecordData.landClass.value)
        assertEquals(1, landRecordData.owners.size)
        assertEquals("সফর আলী", landRecordData.owners[0].name)
        assertEquals("নাজির ওরফে আলী নোয়াজ", landRecordData.owners[0].fatherOrHusbandName)
        assertEquals("১৬ আনা (১.০০০০)", landRecordData.owners[0].shareHissa)
    }

    @Test
    fun testParseFallbackForUnstructuredResponse() {
        val plainText = """
            মৌজা: গোয়ালী, থানা: নবীনগর, জেলা: ব্রাহ্মণবাড়িয়া।
            খতিয়ান নম্বর: ৪০৩, দাগ নম্বর: ১০২।
            মালিক: রফিকুল ইসলাম পিতা ফিরুজ মিয়া।
            English Seal: Union Parishad Kaitala South 2026.
        """.trimIndent()

        val parsed = viewModel.parseGeminiResponse(plainText)

        assertNotNull(parsed)
        assertTrue(parsed.rawBengaliText.contains("গোয়ালী") || parsed.fullBilingualTranscript.contains("গোয়ালী"))
        assertTrue(parsed.rawEnglishText.contains("English Seal") || parsed.fullBilingualTranscript.contains("English Seal"))
    }

    @Test
    fun testResetStateRestoresIdle() {
        viewModel.resetState()
        assertEquals(LandRecordGeminiUiState.Idle, viewModel.uiState.value)
        assertNull(viewModel.selectedBitmap.value)
    }
}
