package com.example

import com.example.data.model.LandRecordType
import com.example.domain.parser.BangladeshLandRecordParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangladeshLandRecordParserTest {

    @Test
    fun testParseCsKhatianRecord() {
        val rawText = """
            গণপ্রজাতন্ত্রী বাংলাদেশ সরকার
            সি এস খতিয়ান
            খতিয়ান নং: ৪০৩
            জেলা: গাজীপুর | উপজেলা: জয়দেবপুর | মৌজা: কাশিমপুর | জে. এল. নং: ৬৫
            সাবেক দাগ নং: ১১৮ | হাল দাগ নং: ২৫৪
            জমির শ্রেণি: নাল | পরিমাণ: ১.২৫ একর (১২৫ শতক)
            মালিক: মোঃ সিরাজুল ইসলাম, পিতা: আব্দুল জব্বার, অংশ: ১.০০০
        """.trimIndent()

        val parsed = BangladeshLandRecordParser.parse(rawText, "403 নং সি এস খতিয়ান.pdf", 1)

        assertEquals(LandRecordType.CS, parsed.recordType)
        assertEquals("৪০৩", parsed.khatianNo.value)
        assertEquals("গাজীপুর", parsed.district.value)
        assertEquals("জয়দেবপুর", parsed.upazilaThana.value)
        assertEquals("কাশিমপুর", parsed.mouza.value)
        assertEquals("৬৫", parsed.jlNo.value)
        assertEquals("নাল", parsed.landClass.value)
        assertTrue(parsed.isHissaValid)
    }

    @Test
    fun testClassificationFromNestedFolder() {
        assertEquals(LandRecordType.CS, LandRecordType.fromFolderOrName("CS 403.pdf"))
        assertEquals(LandRecordType.BRS_BS, LandRecordType.fromFolderOrName("tituwhatsapp 247 নং বি আর এস খতিয়ান.pdf"))
        assertEquals(LandRecordType.RS, LandRecordType.fromFolderOrName("tituwhatsapp 521 নং আর এস খতিয়ান.pdf"))
        assertEquals(LandRecordType.NAMJARI_MUTATION, LandRecordType.fromFolderOrName("mutation কেস নং ১২.pdf"))
        assertEquals(LandRecordType.DEED_DALIL, LandRecordType.fromFolderOrName("deeds সাফ কবলা দলিল.pdf"))
    }
}
