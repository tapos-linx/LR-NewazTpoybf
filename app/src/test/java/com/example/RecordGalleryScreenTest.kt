package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.entity.LandDocumentEntity
import com.example.ui.screens.gallery.RecordGalleryCard
import com.example.ui.screens.gallery.RecordGalleryGrid
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
class RecordGalleryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleDoc1 = LandDocumentEntity(
        id = "gallery-doc-1",
        title = "সি এস পরচা ৫০১",
        sourceFileName = "cs_501.jpg",
        sourceFilePath = "/records/cs_501.jpg",
        sourceCategory = "CS",
        classifiedType = "CS",
        fileFormat = "JPG",
        fileSizeBytes = 512000L,
        dateCaptured = 1774431000000L,
        primaryDistrict = "ঢাকা",
        primaryUpazila = "কেরানীগঞ্জ",
        primaryMouza = "শুভাঢ্যা",
        primaryKhatianNo = "৫০১",
        primaryDagNo = "১৩৪০",
        primaryAreaDecimals = "৪৫",
        primaryLandClass = "নাল",
        ownersSummary = "মোঃ নুরুল ইসলাম",
        status = "VERIFIED",
        overallConfidence = 95.0f
    )

    private val sampleDoc2 = LandDocumentEntity(
        id = "gallery-doc-2",
        title = "বি আর এস খতিয়ান ৬০২",
        sourceFileName = "brs_602.png",
        sourceFilePath = "/records/brs_602.png",
        sourceCategory = "BRS",
        classifiedType = "BRS",
        fileFormat = "PNG",
        fileSizeBytes = 820000L,
        dateCaptured = 1774432000000L,
        primaryDistrict = "ঢাকা",
        primaryUpazila = "গুলশান",
        primaryMouza = "বাড্ডা",
        primaryKhatianNo = "৬০২",
        primaryDagNo = "২১২",
        primaryAreaDecimals = "১২",
        primaryLandClass = "বাণিজ্যিক",
        ownersSummary = "আনিসুর রহমান",
        status = "EXTRACTED",
        overallConfidence = 91.0f
    )

    @Test
    fun testRecordGalleryGridDisplaysLazyVerticalGridAndItems() {
        var clickedDoc: LandDocumentEntity? = null
        var deletedDoc: LandDocumentEntity? = null

        composeTestRule.setContent {
            NewazLandTheme {
                RecordGalleryGrid(
                    documents = listOf(sampleDoc1, sampleDoc2),
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onToggleSelect = {},
                    onDocumentClick = { clickedDoc = it },
                    onDeleteDocument = { deletedDoc = it }
                )
            }
        }

        // Verify LazyVerticalGrid is displayed
        composeTestRule.onNodeWithTag("record_gallery_grid").assertIsDisplayed()

        // Verify Doc 1 card and metadata details
        composeTestRule.onNodeWithTag("record_gallery_item_gallery-doc-1").assertIsDisplayed()
        composeTestRule.onNodeWithText("সি এস পরচা ৫০১", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("খতিয়ান: ৫০১", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("দাগ: ১৩৪০", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মৌজা: শুভাঢ্যা, ঢাকা", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("মালিক: মোঃ নুরুল ইসলাম", useUnmergedTree = true).assertIsDisplayed()

        // Verify Delete button for Doc 1 is present in normal mode
        composeTestRule.onNodeWithTag("btn_delete_gallery_gallery-doc-1", useUnmergedTree = true).assertIsDisplayed()

        // Click on delete button
        composeTestRule.onNodeWithTag("btn_delete_gallery_gallery-doc-1", useUnmergedTree = true).performClick()
        assertEquals("gallery-doc-1", deletedDoc?.id)

        // Click on Doc 1 card
        composeTestRule.onNodeWithTag("record_gallery_item_gallery-doc-1").performClick()
        assertEquals("gallery-doc-1", clickedDoc?.id)
    }

    @Test
    fun testRecordGalleryCardSelectionMode() {
        var toggledId: String? = null

        composeTestRule.setContent {
            NewazLandTheme {
                RecordGalleryCard(
                    document = sampleDoc1,
                    isSelected = true,
                    isSelectionMode = true,
                    onToggleSelect = { toggledId = sampleDoc1.id },
                    onClick = {},
                    onLongClick = {},
                    onDelete = {}
                )
            }
        }

        // Selection checkbox should be displayed in selection mode
        composeTestRule.onNodeWithTag("select_checkbox_gallery-doc-1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_checkbox_gallery-doc-1", useUnmergedTree = true).performClick()
        assertEquals("gallery-doc-1", toggledId)
    }

    @Test
    fun testRecordGalleryGridEmptyState() {
        composeTestRule.setContent {
            NewazLandTheme {
                RecordGalleryGrid(
                    documents = emptyList(),
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onToggleSelect = {},
                    onDocumentClick = {},
                    onDeleteDocument = {}
                )
            }
        }

        // Empty state placeholder
        composeTestRule.onNodeWithTag("empty_gallery_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("গ্যালারিতে কোনো ইমেজ রেকর্ড পাওয়া যায়নি").assertIsDisplayed()
    }
}
