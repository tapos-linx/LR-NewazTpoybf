package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.export.ExportFormat
import com.example.ui.components.ReportExportDialog
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
class ReportExportDialogComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testExportDialogDisplaysCsvOptionAndPreview() {
        var sharedFormat: ExportFormat? = null
        var dismissed = false

        val testCsvPreview = "Document_ID,Title,Survey_Type,Khatian_No\n\"doc-1\",\"সি এস ৪০৩\",\"CS\",\"৪০৩\""

        composeTestRule.setContent {
            NewazLandTheme {
                ReportExportDialog(
                    documentTitle = "সকল ভূমি রেকর্ড (৫ টি নথি)",
                    suggestedFileNameJson = "records.json",
                    suggestedFileNameText = "records.txt",
                    suggestedFileNameCsv = "records.csv",
                    jsonPreview = "{\"records\": []}",
                    textPreview = "বাংলাদেশ ভূমি রেকর্ড রিপোর্ট",
                    csvPreview = testCsvPreview,
                    initialFormat = ExportFormat.CSV,
                    onSaveToSafUri = { _, _ -> },
                    onShare = { format -> sharedFormat = format },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify title & note
        composeTestRule.onNodeWithText("রিপোর্ট সংরক্ষণ (SAF)").assertIsDisplayed()
        composeTestRule.onNodeWithText("নথি: সকল ভূমি রেকর্ড (৫ টি নথি)").assertIsDisplayed()

        // Verify CSV Chip exists and is displayed
        composeTestRule.onNodeWithTag("chip_export_csv").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_export_json").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_export_text").assertIsDisplayed()

        // Verify CSV filename is suggested
        composeTestRule.onNodeWithText("প্রস্তাবিত নাম: records.csv").assertIsDisplayed()

        // Verify preview content exists
        composeTestRule.onNodeWithTag("text_report_preview").assertExists()
        composeTestRule.onNodeWithText(testCsvPreview).assertExists()

        // Verify share button invokes onShare with CSV
        composeTestRule.onNodeWithTag("btn_share_report_dialog").performClick()
        assertEquals(ExportFormat.CSV, sharedFormat)
    }

    @Test
    fun testSwitchingBetweenFormatsInExportDialog() {
        var sharedFormat: ExportFormat? = null

        composeTestRule.setContent {
            NewazLandTheme {
                ReportExportDialog(
                    documentTitle = "খতিয়ান নং ৪০৩",
                    suggestedFileNameJson = "khatian_403.json",
                    suggestedFileNameText = "khatian_403.txt",
                    suggestedFileNameCsv = "khatian_403.csv",
                    jsonPreview = "{\"khatian\": \"403\"}",
                    textPreview = "টেক্সট রিপোর্ট ৪০৩",
                    csvPreview = "ID,Khatian\n1,403",
                    initialFormat = ExportFormat.CSV,
                    onSaveToSafUri = { _, _ -> },
                    onShare = { format -> sharedFormat = format },
                    onDismiss = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Switch to JSON
        composeTestRule.onNodeWithTag("chip_export_json").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("প্রস্তাবিত নাম: khatian_403.json").assertIsDisplayed()
        composeTestRule.onNodeWithText("{\"khatian\": \"403\"}").assertExists()

        // Switch to Text
        composeTestRule.onNodeWithTag("chip_export_text").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("প্রস্তাবিত নাম: khatian_403.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("টেক্সট রিপোর্ট ৪০৩").assertExists()

        // Switch back to CSV
        composeTestRule.onNodeWithTag("chip_export_csv").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("প্রস্তাবিত নাম: khatian_403.csv").assertIsDisplayed()
        composeTestRule.onNodeWithText("ID,Khatian\n1,403").assertExists()

        // Click Share
        composeTestRule.onNodeWithTag("btn_share_report_dialog").performClick()
        assertEquals(ExportFormat.CSV, sharedFormat)
    }
}
