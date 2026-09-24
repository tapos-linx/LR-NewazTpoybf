package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.entity.LandDocumentEntity
import com.example.ui.screens.records.CapturedRecordsList
import com.example.ui.theme.NewazLandTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CapturedRecordsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleDoc1 = LandDocumentEntity(
        id = "doc-101",
        title = "সি এস খতিয়ান ২০৪",
        sourceFileName = "cs_porcha_204.pdf",
        sourceFilePath = "/records/cs_porcha_204.pdf",
        sourceCategory = "CS",
        classifiedType = "CS",
        fileFormat = "PDF",
        fileSizeBytes = 512000L,
        dateCaptured = 1774431000000L,
        primaryDistrict = "ঢাকা",
        primaryUpazila = "কেরানীগঞ্জ",
        primaryMouza = "শুভাঢ্যা",
        primaryKhatianNo = "২০৪",
        primaryDagNo = "৫১২",
        primaryAreaDecimals = "৩৩",
        primaryLandClass = "নাল",
        ownersSummary = "আহমেদ আলী ও রহমত উল্লাহ",
        status = "VERIFIED",
        overallConfidence = 96.0f
    )

    private val sampleDoc2 = LandDocumentEntity(
        id = "doc-102",
        title = "আর এস খতিয়ান ৮৮",
        sourceFileName = "rs_88.jpg",
        sourceFilePath = "/records/rs_88.jpg",
        sourceCategory = "RS",
        classifiedType = "RS",
        fileFormat = "JPG",
        fileSizeBytes = 1048576L,
        dateCaptured = 1774432000000L,
        primaryDistrict = "কুমিল্লা",
        primaryUpazila = "চান্দিনা",
        primaryMouza = "মাইজখার",
        primaryKhatianNo = "৮৮",
        primaryDagNo = "১০৪",
        primaryAreaDecimals = "২৫",
        primaryLandClass = "বাড়ি",
        ownersSummary = "ফাতেমা বেগম",
        status = "EXTRACTED",
        overallConfidence = 88.5f
    )

    @Test
    fun testCapturedRecordsListDisplaysLazyColumnAndMultipleCards() {
        var clickedId: String? = null
        var deletedDoc: LandDocumentEntity? = null

        composeTestRule.setContent {
            NewazLandTheme {
                CapturedRecordsList(
                    documents = listOf(sampleDoc1, sampleDoc2),
                    onDocumentClick = { clickedId = it },
                    onDeleteDocument = { deletedDoc = it }
                )
            }
        }

        // Verify LazyColumn is displayed
        composeTestRule.onNodeWithTag("captured_records_lazy_column").assertIsDisplayed()

        // Verify Doc 1 Card, Thumbnail, and Summary Metadata
        composeTestRule.onNodeWithTag("document_card_doc-101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("thumbnail_doc-101", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("সি এস খতিয়ান ২০৪", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মালিক: আহমেদ আলী ও রহমত উল্লাহ", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("খতিয়ান: ২০৪", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("দাগ: ৫১২", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মৌজা: শুভাঢ্যা", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("পরিমাণ: ৩৩ শতক", useUnmergedTree = true).assertIsDisplayed()

        // Verify Doc 2 Card, Thumbnail, and Summary Metadata
        composeTestRule.onNodeWithTag("document_card_doc-102").assertExists()
        composeTestRule.onNodeWithTag("thumbnail_doc-102", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("আর এস খতিয়ান ৮৮", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("মালিক: ফাতেমা বেগম", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("খতিয়ান: ৮৮", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("দাগ: ১০৪", useUnmergedTree = true).assertExists()

        // Click on second card
        composeTestRule.onNodeWithTag("document_card_doc-102").performClick()
        assertEquals("doc-102", clickedId)

        // Delete click on first card
        composeTestRule.onNodeWithTag("btn_delete_doc-101", useUnmergedTree = true).performClick()
        assertEquals("doc-101", deletedDoc?.id)
    }

    @Test
    fun testCapturedRecordsListEmptyState() {
        composeTestRule.setContent {
            NewazLandTheme {
                CapturedRecordsList(
                    documents = emptyList(),
                    onDocumentClick = {},
                    onDeleteDocument = {},
                    emptyMessage = "কোনো সংগৃহীত ভূমি রেকর্ড পাওয়া যায়নি"
                )
            }
        }

        composeTestRule.onNodeWithTag("empty_captured_records_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("কোনো সংগৃহীত ভূমি রেকর্ড পাওয়া যায়নি").assertIsDisplayed()
    }
}
