package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.entity.LandDocumentEntity
import com.example.ui.screens.home.components.CapturedRecordCard
import com.example.ui.theme.NewazLandTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CapturedRecordListComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCapturedRecordCardDisplaysThumbnailAndMetadata() {
        var clicked = false
        var deleted = false

        val sampleDoc = LandDocumentEntity(
            id = "test-doc-99",
            title = "সি এস খতিয়ান ৪০৩",
            sourceFileName = "cs_porcha_403.pdf",
            sourceFilePath = "/storage/cs_porcha_403.pdf",
            sourceCategory = "CS",
            classifiedType = "CS",
            fileFormat = "PDF",
            fileSizeBytes = 2048000L,
            dateCaptured = 1774431000000L,
            primaryDistrict = "ঢাকা",
            primaryUpazila = "কেরানীগঞ্জ",
            primaryMouza = "শুভাঢ্যা",
            primaryKhatianNo = "৪০৩",
            primaryDagNo = "১২৫০",
            primaryAreaDecimals = "৫০",
            primaryLandClass = "নাল",
            ownersSummary = "মোঃ নুরুল ইসলাম",
            status = "VERIFIED",
            overallConfidence = 95.0f
        )

        composeTestRule.setContent {
            NewazLandTheme {
                CapturedRecordCard(
                    document = sampleDoc,
                    onClick = { clicked = true },
                    onDelete = { deleted = true }
                )
            }
        }

        // Verify test tags and metadata displays (useUnmergedTree for clickable parent)
        composeTestRule.onNodeWithTag("document_card_test-doc-99").assertIsDisplayed()
        composeTestRule.onNodeWithTag("thumbnail_test-doc-99", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("সি এস খতিয়ান ৪০৩", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মালিক: মোঃ নুরুল ইসলাম", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("খতিয়ান: ৪০৩", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("দাগ: ১২৫০", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মৌজা: শুভাঢ্যা", useUnmergedTree = true).assertIsDisplayed()

        // Verify click action
        composeTestRule.onNodeWithTag("document_card_test-doc-99").performClick()
        assertTrue("Card click callback triggered", clicked)

        // Verify delete action
        composeTestRule.onNodeWithTag("btn_delete_test-doc-99", useUnmergedTree = true).performClick()
        assertTrue("Delete callback triggered", deleted)
    }
}
