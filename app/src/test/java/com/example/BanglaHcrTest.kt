package com.example

import com.example.domain.hcr.BanglaHcrEngine
import com.example.domain.hcr.HcrContextCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BanglaHcrTest {

    private val engine = BanglaHcrEngine()

    @Test
    fun testHcrOwnerNameTokenizationAndConfidence() {
        val result = engine.processHandwrittenCrop(
            cropBitmap = null,
            rawSampleText = "মৃত আব্দুল বারিক পিতা- হাজী জমিরুদ্দিন শেখ [Unclear: ঠিকানা অস্পষ্ট]",
            category = HcrContextCategory.OWNER_NAME
        )

        assertNotNull(result)
        assertTrue("Tokens should be extracted", result.tokens.isNotEmpty())
        assertTrue("Manual review must be required due to [Unclear tag", result.requiresManualReview)

        val unclearToken = result.tokens.find { it.text.contains("[Unclear") }
        assertNotNull("Unclear token must be present", unclearToken)
        assertTrue("Unclear token must be flagged", unclearToken!!.isFlagged)
        assertTrue("Unclear confidence must be low", unclearToken.confidence < 0.60f)
    }

    @Test
    fun testManualCorrectionPreservesZeroDataLoss() {
        val original = engine.processHandwrittenCrop(
            cropBitmap = null,
            rawSampleText = "সাবেক দাগ নং ৪১২, হাল দাগ নং ৮১৫ [Unclear: কাটা]",
            category = HcrContextCategory.DAG_NUMBER
        )

        val correctedText = "সাবেক দাগ নং ৪১২, হাল দাগ নং ৮১৫/ক (কাটা দাগ সংশোধন)"
        val corrected = engine.applyCorrection(
            original = original,
            correctedText = correctedText,
            editorNotes = "সার্ভেয়ার রিপোর্ট পৃষ্ঠা ৪ দ্বারা প্রমাণিত"
        )

        assertEquals("Original text must remain intact", original.originalText, corrected.originalText)
        assertEquals("Corrected text must match", correctedText, corrected.correctedText)
        assertTrue("Corrected record must be marked verified", corrected.isVerified)
        assertEquals(1.0f, corrected.confidence, 0.01f)
        assertEquals("সার্ভেয়ার রিপোর্ট পৃষ্ঠা ৪ দ্বারা প্রমাণিত", corrected.editorNotes)
    }

    @Test
    fun testHcrContextCategories() {
        for (category in HcrContextCategory.values()) {
            val result = engine.processHandwrittenCrop(null, null, category)
            assertNotNull(result.originalText)
            assertTrue("Sample text must not be blank", result.originalText.isNotBlank())
            assertEquals(category, result.contextCategory)
        }
    }
}
