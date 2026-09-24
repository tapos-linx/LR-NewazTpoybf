package com.example

import com.example.data.model.BengaliNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class BengaliNumberUtilsTest {

    @Test
    fun testBengaliToEnglishConversion() {
        assertEquals("403", BengaliNumberUtils.toEnglishDigits("৪০৩"))
        assertEquals("247", BengaliNumberUtils.toEnglishDigits("২৪৭"))
        assertEquals("521", BengaliNumberUtils.toEnglishDigits("৫২১"))
        assertEquals("124", BengaliNumberUtils.toEnglishDigits("১২৪"))
    }

    @Test
    fun testEnglishToBengaliConversion() {
        assertEquals("৪০৩", BengaliNumberUtils.toBengaliDigits(403L))
        assertEquals("২৪৭", BengaliNumberUtils.toBengaliDigits("247"))
        assertEquals("১.২৫", BengaliNumberUtils.toBengaliDigits(1.25, 2))
    }

    @Test
    fun testParseShareHissa() {
        // Direct decimal
        val dec = BengaliNumberUtils.parseShareValue("০.৫০০")
        assertEquals(0.5, dec, 0.001)

        // 8 Anna = 0.5
        val anna = BengaliNumberUtils.parseShareValue("৮ আনা")
        assertEquals(0.5, anna, 0.01)

        // 16 Anna = 1.0 (Full share)
        val full = BengaliNumberUtils.parseShareValue("১৬ আনা")
        assertEquals(1.0, full, 0.01)
    }
}
