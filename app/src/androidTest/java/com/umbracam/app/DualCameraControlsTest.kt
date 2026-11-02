package com.umbracam.app

import android.Manifest
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

/** Real controls and real capture on the explicitly selected project emulator. */
@RunWith(AndroidJUnit4::class)
class DualCameraControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CameraStudioViewModel
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Before fun setup() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dualControls") == "true")
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT >= 30)
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        compose.waitUntil(30_000) { !model.ui.value.initializing && !model.ui.value.serviceRunning }
        compose.runOnIdle { model.selectDual(null) }
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.waitUntil(40_000) { model.ui.value.ready && model.dualOptions.value.pairs.isNotEmpty() }
    }
    @After fun stop() { if (this::model.isInitialized) compose.runOnUiThread { model.stop() } }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun ready() = compose.waitUntil(40_000) { model.ui.value.ready && !model.ui.value.loading }
    private fun shot(name: String) {
        compose.waitForIdle()
        // Readiness precedes the first texture frame. The composed wrapper does not reliably
        // drive PreviewView.StreamState; inspect actual pixels from this known bright AVD feed.
        fun preview(view: android.view.View): androidx.camera.view.PreviewView? {
            if (view is androidx.camera.view.PreviewView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                preview(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        compose.waitUntil(15_000) {
            var hasPixels = false
            compose.runOnUiThread {
                preview(compose.activity.window.decorView)?.bitmap?.let { frame ->
                    hasPixels = (1..3).any { x -> (1..3).any { y ->
                        (frame.getPixel(frame.width * x / 4, frame.height * y / 4) and 0x00ffffff) != 0
                    } }
                    frame.recycle()
                }
            }
            hasPixels
        }
        android.os.SystemClock.sleep(350)
        // Capture the actual display buffer, including native window chrome and texture layers.
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(compose.activity.getExternalFilesDir(null), "phase4-controls-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun record(audio: Boolean) {
        click("Start recording")
        compose.waitUntil(35_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= 2_000 }
        compose.onNodeWithText("Single camera").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Dual camera").assertIsNotEnabled()
        compose.onNodeWithText("Split screen").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Microphone audio").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Stop recording").performScrollTo().assertIsEnabled()
        shot("recording")
        click("Stop recording")
        compose.waitUntil(45_000) { !model.ui.value.serviceRunning && model.session.value?.state in setOf(RecordingState.SAVED, RecordingState.FAILED) }
        assertEquals(model.ui.value.toString(), RecordingState.SAVED, model.session.value?.state)
        val file = File(compose.activity.noBackupFilesDir, "camera/recordings/${model.session.value!!.completedSegments.last().storageId}")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            assertEquals(1, tracks.count { it.startsWith("video/") })
            assertEquals(audio, tracks.any { it.startsWith("audio/") })
        } finally { extractor.release() }
    }
    @Test fun actualModeLayoutMicrophoneAndFrozenSettingsProduceFiles() {
        compose.onNodeWithText("Single camera").assertIsSelected()
        click("Dual camera"); ready()
        assertEquals(DualLayout.PICTURE_IN_PICTURE, model.ui.value.dualSpecification!!.layout)
        assertFalse(model.ui.value.audioEnabled)
        compose.onNodeWithText("Dual camera").performScrollTo(); shot("dual-top")
        compose.onNodeWithText("Picture in picture").performScrollTo().assertIsSelected(); shot("pip-settings")
        record(false)
        click("Split screen"); ready()
        click("Microphone audio"); ready()
        assertTrue(model.ui.value.dualSpecification!!.microphone)
        click("Dual camera") // Selecting the current mode preserves the chosen layout and audio.
        assertEquals(DualLayout.SPLIT, model.ui.value.dualSpecification!!.layout)
        assertTrue(model.ui.value.audioEnabled)
        compose.activityRule.scenario.recreate(); compose.waitForIdle(); ready()
        assertEquals(DualLayout.SPLIT, model.ui.value.dualSpecification!!.layout)
        compose.onNodeWithText("Split screen").performScrollTo().assertIsSelected(); shot("split-settings")
        record(true)
        click("Single camera"); ready()
        assertNull(model.ui.value.dualSpecification)
        compose.onNodeWithText("Front camera").performScrollTo().assertIsEnabled()
        assertFalse(model.ui.value.serviceRunning)
    }
    @Test fun captureNativeStudioStatesWithoutStartingCapture() {
        compose.onNodeWithText("Rear camera").performScrollTo(); shot("single-preview")
        compose.onNodeWithText("Single camera").performScrollTo(); shot("single-top")
        click("Dual camera"); ready()
        compose.onNodeWithText("Cameras").performScrollTo()
        shot("pip-preview")
        compose.onNodeWithText("Dual camera").performScrollTo(); shot("dual-top")
        compose.onNodeWithText("Picture in picture").performScrollTo(); shot("pip-settings")
        click("Split screen"); ready()
        compose.onNodeWithText("Cameras").performScrollTo(); shot("split-preview")
        compose.onNodeWithText("Start recording").performScrollTo(); shot("split-actions")
        assertFalse(model.ui.value.serviceRunning)
    }
}
