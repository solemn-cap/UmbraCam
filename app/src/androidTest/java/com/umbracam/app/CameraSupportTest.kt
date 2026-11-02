package com.umbracam.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraSupportTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun show(state: CameraSupportState, onCheck: () -> Unit = {}, onOpen: () -> Unit = {}) {
        compose.setContent { UmbraCamTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { CameraSupportScreen(state, onCheck, onOpen) } } } }
    }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "phase3-" + name + ".png")
        // Capture the native Compose view buffer, independent of window launch fades.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun positiveMetadataShowsWholeGroupAndNoRecordingControl() {
        val cameras = listOf(CameraDescriptor("0", CameraFacing.REAR), CameraDescriptor("1", CameraFacing.FRONT), CameraDescriptor("2", CameraFacing.REAR))
        show(CameraSupportState(result = ConcurrentCameraAnalyzer.analyze(cameras, setOf(setOf("0", "1", "2")))))
        compose.onNodeWithText(text(R.string.camera_support_front_rear)).performScrollTo().assertIsDisplayed()
        screenshot("positive-fixture-top")
        compose.onNodeWithText(text(R.string.camera_support_limit)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_record)).assertDoesNotExist()
        screenshot("positive-fixture")
    }
    @Test fun emptyReportDoesNotPromiseAnAvailableCamera() {
        show(CameraSupportState(result = ConcurrentCameraCapabilities.Report(emptyList(), emptyList())))
        compose.onNodeWithText(text(R.string.camera_support_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_support_fallback)).assertDoesNotExist()
        screenshot("empty-fixture")
    }
    @Test fun accessErrorOffersRetryAndCameraNavigation() {
        var checks = 0; var opens = 0
        show(CameraSupportState(result = ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.ACCESS_DENIED)), { checks++ }, { opens++ })
        compose.onNodeWithText(text(R.string.camera_support_access)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_support_again)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.camera_support_open)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks); assertEquals(1, opens) }
        screenshot("error-fixture")
    }
    @Test fun twoCamerasWithoutAdvertisedGroupsUseSingleCameraFallback() {
        show(CameraSupportState(result = ConcurrentCameraAnalyzer.analyze(listOf(CameraDescriptor("0", CameraFacing.REAR), CameraDescriptor("1", CameraFacing.FRONT)), emptySet())))
        compose.onNodeWithText(text(R.string.camera_support_none)).performScrollTo().assertIsDisplayed()
        screenshot("no-groups-fixture-top")
        compose.onNodeWithText(text(R.string.camera_support_fallback)).performScrollTo().assertIsDisplayed()
        screenshot("no-groups-fixture")
    }
    @Test fun checkingDisablesDuplicateRequests() {
        show(CameraSupportState(checking = true))
        compose.onNodeWithText(text(R.string.camera_support_checking)).assertIsNotEnabled()
        screenshot("checking-fixture")
    }
    @Test fun unsupportedVersionDoesNotClaimUnsupportedHardware() {
        show(CameraSupportState(result = ConcurrentCameraCapabilities.UnsupportedPlatform))
        compose.onNodeWithText(text(R.string.camera_support_old)).performScrollTo().assertIsDisplayed()
        screenshot("version-fallback-fixture-top")
        compose.onNodeWithText(text(R.string.camera_support_old_detail)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_support_none)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.camera_support_front_rear)).assertDoesNotExist()
        screenshot("version-fallback-fixture")
    }
    @Test fun actualEmulatorMetadataQueryNeedsNoCameraPermissionPrompt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = ConcurrentCameraDetector(AndroidCameraInventorySource(context)).read()
        if (android.os.Build.VERSION.SDK_INT < 30) assertEquals(ConcurrentCameraCapabilities.UnsupportedPlatform, result)
        else assertTrue(result is ConcurrentCameraCapabilities.Report)
        show(CameraSupportState(result = result))
        compose.onNodeWithText(text(R.string.camera_support_again)).assertIsDisplayed()
        screenshot("actual-platform-result")
    }
}
