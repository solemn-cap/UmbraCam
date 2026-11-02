package com.umbracam.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic metadata states rendered with the same production native controls; no cameras opened. */
@RunWith(AndroidJUnit4::class)
class DualCameraControlStatesTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val front = CameraDescriptor("1", CameraFacing.FRONT)
    private val rear = CameraDescriptor("0", CameraFacing.REAR)
    private fun show(options: DualOptionsState, spec: DualRecordingSpec? = null,
        check: () -> Unit = {}, pair: (DualCameraPair) -> Unit = {}) {
        compose.setContent { UmbraCamTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Camera Studio", style = MaterialTheme.typography.headlineMedium)
            CameraModeControls(spec, options, true, {}, {}, check)
            if (spec != null) DualSettings(spec, options, true, pair, {}, check)
        } } } }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "phase4-controls-fixture-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun checkingDisablesDual() {
        show(DualOptionsState(checking = true))
        compose.onNodeWithText("Dual camera").assertIsNotEnabled()
        compose.onNodeWithText("Checking dual-camera availability…").assertIsDisplayed()
        shot("checking")
    }
    @Test fun oldPlatformRetainsSingleWithoutClaimingHardwareFailure() {
        show(DualOptionsState(result = ConcurrentCameraCapabilities.UnsupportedPlatform))
        compose.onNodeWithText("Single camera").assertIsEnabled()
        compose.onNodeWithText("Dual camera").assertIsNotEnabled()
        compose.onNodeWithText("Dual mode needs Android 11 or later. You can use Single camera.").assertIsDisplayed()
        shot("old-platform")
    }
    @Test fun unavailableQueryOffersRetry() {
        var checks = 0
        show(DualOptionsState(result = ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.CAMERA_SERVICE)), check = { checks++ })
        compose.onNodeWithText("Dual camera").assertIsNotEnabled()
        compose.onNodeWithText("Check again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks) }
        shot("error")
    }
    @Test fun noAdvertisedPairDisablesDualAndExplainsFallback() {
        show(DualOptionsState(result = ConcurrentCameraCapabilities.Report(listOf(front, rear), emptyList())))
        compose.onNodeWithText("Dual camera").assertIsNotEnabled()
        compose.onNodeWithText("No front-and-rear pair is advertised. You can check again or use Single camera.").assertIsDisplayed()
        shot("no-pair")
    }
    @Test fun multiplePairsCanBeChosenAndStalePairIsExplained() {
        val other = CameraDescriptor("2", CameraFacing.REAR)
        val report = ConcurrentCameraCapabilities.Report(listOf(front, rear, other), listOf(ConcurrentCameraGroup(listOf(front, rear)), ConcurrentCameraGroup(listOf(front, other))))
        var selected: DualCameraPair? = null
        show(DualOptionsState(result = report), DualRecordingSpec(DualCameraPair(front, rear.copy(id = "absent"))), pair = { selected = it })
        compose.onNodeWithText("Front + rear · option 0").assertDoesNotExist()
        compose.onNodeWithText("This pair is no longer listed. Check again, choose another pair or use Single camera.").performScrollTo().assertIsDisplayed()
        shot("stale-pair")
        compose.onNodeWithText("Change cameras").performScrollTo().performClick()
        compose.onNodeWithText("Front + rear · option 2").performClick()
        compose.runOnIdle { assertEquals(DualCameraPair(front, other), selected) }
    }
}
