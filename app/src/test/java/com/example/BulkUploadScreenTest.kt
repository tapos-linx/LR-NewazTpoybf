package com.example

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.LandRecordType
import com.example.domain.saf.DiscoveredFile
import com.example.domain.saf.FolderScanSummary
import com.example.ui.screens.upload.UploadQueueItemCard
import com.example.ui.theme.NewazLandTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BulkUploadScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testQueueItemCardRendersStandardFile() {
        val file = DiscoveredFile(
            uri = Uri.parse("content://saf/test/CS_Khatian_501.pdf"),
            name = "CS_Khatian_501.pdf",
            parentFolder = "CS",
            mimeType = "application/pdf",
            sizeBytes = 245000L,
            isZeroByte = false,
            format = "PDF",
            classifiedType = LandRecordType.CS,
            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            isDuplicate = false
        )

        composeTestRule.setContent {
            NewazLandTheme {
                UploadQueueItemCard(file = file)
            }
        }

        composeTestRule.onNodeWithTag("queue_item_CS_Khatian_501.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("CS_Khatian_501.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("CS").assertIsDisplayed()
        composeTestRule.onNodeWithText("কিউতে যুক্ত").assertIsDisplayed()
        composeTestRule.onNodeWithText("SHA-256: e3b0c44298fc...").assertIsDisplayed()
    }

    @Test
    fun testQueueItemCardRendersDuplicateWarningBadge() {
        val duplicateFile = DiscoveredFile(
            uri = Uri.parse("content://saf/test/copy_CS_Khatian_501.pdf"),
            name = "copy_CS_Khatian_501.pdf",
            parentFolder = "Duplicates",
            mimeType = "application/pdf",
            sizeBytes = 245000L,
            isZeroByte = false,
            format = "PDF",
            classifiedType = LandRecordType.CS,
            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            isDuplicate = true,
            duplicateOriginalSource = "CS/CS_Khatian_501.pdf"
        )

        composeTestRule.setContent {
            NewazLandTheme {
                UploadQueueItemCard(file = duplicateFile)
            }
        }

        composeTestRule.onNodeWithTag("queue_item_copy_CS_Khatian_501.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("ডুপ্লিকেট").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "সতর্কবার্তা: পূর্ববর্তী নথির একই হ্যাশ শনাক্ত (CS/CS_Khatian_501.pdf)। মূল ফাইল সংরক্ষিত থাকবে।"
        ).assertIsDisplayed()
    }

    @Test
    fun testQueueItemCardRendersZeroByteWarningBadge() {
        val zeroByteFile = DiscoveredFile(
            uri = Uri.parse("content://saf/test/corrupted.pdf"),
            name = "corrupted.pdf",
            parentFolder = "Broken",
            mimeType = "application/pdf",
            sizeBytes = 0L,
            isZeroByte = true,
            format = "PDF",
            classifiedType = LandRecordType.OTHER,
            sha256Hash = "",
            isDuplicate = false
        )

        composeTestRule.setContent {
            NewazLandTheme {
                UploadQueueItemCard(file = zeroByteFile)
            }
        }

        composeTestRule.onNodeWithTag("queue_item_corrupted.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("০-বাইট").assertIsDisplayed()
    }

    @Test
    fun testFolderScanSummaryCountsDuplicatesCorrectly() {
        val f1 = DiscoveredFile(
            uri = Uri.parse("content://f1"),
            name = "file1.pdf",
            parentFolder = "folder",
            mimeType = "application/pdf",
            sizeBytes = 1000L,
            isZeroByte = false,
            format = "PDF",
            classifiedType = LandRecordType.CS,
            sha256Hash = "hash1",
            isDuplicate = false
        )
        val f2 = DiscoveredFile(
            uri = Uri.parse("content://f2"),
            name = "file1_copy.pdf",
            parentFolder = "folder",
            mimeType = "application/pdf",
            sizeBytes = 1000L,
            isZeroByte = false,
            format = "PDF",
            classifiedType = LandRecordType.CS,
            sha256Hash = "hash1",
            isDuplicate = true,
            duplicateOriginalSource = "folder/file1.pdf"
        )

        val summary = FolderScanSummary(
            rootUri = Uri.parse("content://root"),
            totalFilesFound = 2,
            supportedFiles = listOf(f1, f2),
            zeroByteFiles = emptyList(),
            duplicateFiles = listOf(f2),
            ignoredFilesCount = 0,
            categoriesFound = mapOf("CS" to 2)
        )

        assertEquals(2, summary.totalFilesFound)
        assertEquals(2, summary.supportedFiles.size)
        assertEquals(1, summary.duplicateFiles.size)
        assertTrue(summary.duplicateFiles.first().isDuplicate)
        assertEquals("folder/file1.pdf", summary.duplicateFiles.first().duplicateOriginalSource)
    }
}
