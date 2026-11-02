package com.umbracam.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeonNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun welcomeGuideExplainsFreshConsentAndReturnsHome() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Welcome guide").performScrollTo().performClick()
        compose.onNodeWithText("Get Started").assertIsDisplayed().performClick()
        compose.onNodeWithText("Get Set Up").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.suite_consent_each_time)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-camera").assertIsDisplayed()
    }
    @Test fun sourceFiltersAndSearchUseCompletedCatalogData() {
        val root=compose.activity.noBackupFilesDir
        org.junit.Assume.assumeTrue("Requires existing camera and screen fixtures",
            java.io.File(root,"camera/recordings").listFiles().orEmpty().isNotEmpty() && java.io.File(root,"screen/recordings").listFiles().orEmpty().isNotEmpty())
        compose.onNodeWithTag("home-vault").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("recording-actions").fetchSemanticsNodes().isNotEmpty() }
        val field=compose.onNodeWithText("Search recordings")
        field.performTextInput("no-such-video-acceptance")
        field.performImeAction()
        compose.onNodeWithText(compose.activity.getString(R.string.suite_no_matches)).performScrollTo().assertIsDisplayed()
        field.performScrollTo().performTextClearance(); field.performImeAction()
        compose.onNodeWithText("Camera").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("recording-actions").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Screen recording").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Screen").performClick()
        assertTrue(compose.onAllNodesWithText("Camera recording").fetchSemanticsNodes().isEmpty())
    }
}
