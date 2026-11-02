package com.umbracam.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeSectionsNavigateToCameraOrHonestPlaceholdersAndBack() {
        Destination.studios.forEach { destination ->
            compose.onNodeWithTag("home-${destination.route}").performScrollTo().performClick()
            val expected = when (destination) { Destination.CAMERA -> R.string.camera_intro; Destination.SOS -> R.string.sos_preview; Destination.REACTION -> R.string.screen_intro; else -> R.string.library_privacy }
            compose.onNodeWithText(compose.activity.getString(expected)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(if (destination == Destination.VAULT) R.string.back else R.string.back_home)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.tagline)).assertExists()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        java.io.File(instrument.targetContext.getExternalFilesDir(null), "phase3-" + name + ".png").outputStream().use { instrument.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun sosScreenExplainsQuickRecordAndLimits() {
        capture("home")
        compose.onNodeWithText(compose.activity.getString(R.string.sos_title)).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.sos_preview)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.sos_quick_record)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.suite_sos_honest)).assertIsDisplayed()
        capture("sos")
    }

    @Test fun destinationSurvivesActivityRecreation() {
        compose.onNodeWithText("Screen recorder").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Screen recorder").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.screen_intro)).assertIsDisplayed()
    }

    @Test fun settingsExplainsSystemAppearance() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_details)).performScrollTo().assertIsDisplayed()
    }

    @Test fun proDiscoveryShowsHonestAccessAndNoCheckout() {
        compose.onNodeWithText(compose.activity.getString(R.string.pro_title)).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.pro_intro)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pro_no_checkout)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pro_layout_presets)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buy now").assertDoesNotExist()
        compose.onNodeWithText("Subscribe").assertDoesNotExist()
    }

    @Test fun supportCheckSurvivesRecreationAndBackReturnsToSettings() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_support_check)).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_support_check)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.camera_support_again)).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_support_again)).assertIsDisplayed()
        capture("support-navigation-result")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_details)).performScrollTo().assertIsDisplayed()
    }

    @Test fun systemBackReturnsFromSettingsToHome() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_details)).performScrollTo().assertIsDisplayed()
        // Semantics can be visible before WindowManager gives this activity input focus.
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        // OS Back delivery and the navigation transition can finish after the key call returns.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("home-camera").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("home-camera").assertIsDisplayed()
    }
}
