package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.matcher.WarishMatcherScreen
import com.example.ui.screens.matcher.WarishMatcherTabContent
import com.example.ui.screens.matcher.CadastralMapTabContent
import com.example.ui.screens.matcher.ForensicAuditTabContent
import com.example.ui.screens.matcher.WarishMatcherUiState
import com.example.ui.screens.matcher.createDefaultWarish
import com.example.ui.theme.NewazLandTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WarishMatcherScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testWarishMatcherTabContentRendersCards() {
        val warish = createDefaultWarish()
        val uiState = WarishMatcherUiState(
            currentWarish = warish,
            selectedTab = 0
        )

        composeTestRule.setContent {
            NewazLandTheme {
                WarishMatcherTabContent(
                    uiState = uiState,
                    onAddHeirClick = {},
                    onRemoveHeir = {},
                    onReRunMatch = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("major_warish_selector_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("warish_certificate_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("ওয়ারিশনামা সনদ তথ্য").assertIsDisplayed()
        composeTestRule.onNodeWithText("মরহুম / মৃত: ফিরুজ মিয়া").assertIsDisplayed()
        composeTestRule.onNodeWithTag("warish_matcher_tab_content")
            .performScrollToNode(hasTestTag("hissa_balance_card"))
        composeTestRule.onNodeWithTag("hissa_balance_card").assertExists()
        composeTestRule.onNodeWithText("হিস্যা সমতা যাচাই (১৬ আনা / ১.০০০)").assertExists()
        composeTestRule.onNodeWithText("রফিকুল ইসলাম").assertExists()
    }

    @Test
    fun testCadastralMapTabRendersForensicBannerAndCanvas() {
        val warish = createDefaultWarish()
        val detector = com.example.domain.gis.CadastralMapDetector()
        val sheetData = detector.detectAndHighlightParcels(warish.targetDagNumbers)

        composeTestRule.setContent {
            NewazLandTheme {
                CadastralMapTabContent(
                    sheetData = sheetData,
                    selectedParcel = sheetData.parcels.first { it.isTargetMatched },
                    onParcelClick = {},
                    report = null
                )
            }
        }

        composeTestRule.onNodeWithTag("cadastral_map_tab_content").assertIsDisplayed()
        composeTestRule.onNodeWithText("ফরেনসিক নিয়ম: শুধুমাত্র ওয়ারিশ সনদের মিলকৃত দাগ হাইলাইট করা হয়েছে।").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cadastral_map_canvas_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("parcel_inspector_card").assertIsDisplayed()
    }

    @Test
    fun testForensicAuditTabRendersSummaryAndShareButton() {
        val warish = createDefaultWarish()
        val matcher = com.example.domain.matcher.InheritanceMatcher()
        val report = matcher.matchWarishToSurveys(warish, emptyList())

        composeTestRule.setContent {
            NewazLandTheme {
                ForensicAuditTabContent(
                    report = report,
                    onShareReport = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("forensic_audit_tab_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("ফরেনসিক ওয়ারিশ ও জরিপ অডিট সার্টিফিকেট").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_share_audit_report").assertIsDisplayed()
    }
}
