package com.umbracam.app

import android.Manifest
import android.app.NotificationManager
import android.app.KeyguardManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.core.recording.RecordingState
import com.umbracam.app.library.RecordingPlayerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

/** Uses the real system MediaProjection dialog. Never supplies a synthetic grant. */
@RunWith(AndroidJUnit4::class)
class ScreenRecordingIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = (compose.activity.application as UmbraCamApplication).screenRecording

    @Before fun requireInteractiveUnlockedActivity() {
        val activity = compose.activity
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        assumeTrue("Unlock the device before running screen integration tests", !keyguard.isKeyguardLocked)
        val interactive = runCatching {
            compose.waitUntil(10_000) {
                activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                    activity.window.decorView.isShown && activity.hasWindowFocus()
            }
            true
        }.getOrDefault(false)
        assumeTrue("Bring UmbraCam to the foreground and collapse the notification shade before testing", interactive)
    }

    private fun enter() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.POST_NOTIFICATIONS)
        compose.onNodeWithText("Screen recorder").performScrollTo().performClick()
        compose.waitUntil(15_000) { !controller.ui.value.initializing && !controller.isBusy }
    }

    private fun systemButton(positive: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val text = node.text?.toString().orEmpty().lowercase()
            if (node.packageName == "com.android.systemui" && node.isClickable &&
                (if (positive) text in setOf("start now", "start recording", "share screen", "share", "start") else text in setOf("cancel", "don't share"))) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow)?.let {
                assertTrue(it.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return
            }
            SystemClock.sleep(100)
        }
        fail("Real system screen-sharing ${if (positive) "start" else "cancel"} button unavailable")
    }

    private fun enterInterruptionFixture() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit interruption and physical-device opt-in", arguments.getString("screenInterruptions") == "true" &&
            (Build.HARDWARE in setOf("ranchu", "goldfish") || arguments.getString("physicalAcceptance") == "true"))
        enter()
    }

    private fun thermalCommand(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cmd thermalservice $command"))
            .bufferedReader().use { it.readText() }
    }

    @Test fun emulatorThermalStopPreservesScreenAndBlocksWarmRestart() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") && InstrumentationRegistry.getArguments().getString("thermalAcceptance") == "true")
        thermalCommand("override-status 0")
        enter()
        try {
            startInterruptionCapture()
            thermalCommand("override-status 3")
            assertInterruptedOutput(com.umbracam.core.recording.RecordingErrorCode.THERMAL_LIMIT)
            assertEquals(com.umbracam.app.screen.ScreenIssue.THERMAL, controller.ui.value.issue)
            val after = completedHashes()
            val attempt = controller.session.value!!.attemptId
            thermalCommand("override-status 2")
            compose.waitUntil(10_000) { (compose.activity.application as UmbraCamApplication).captureThermals.status.value == 2 }
            compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.error_thermal)).assertExists()
            assertFalse(controller.isBusy)
            assertEquals(attempt, controller.session.value!!.attemptId)
            thermalCommand("override-status 0")
            compose.waitUntil(10_000) { (compose.activity.application as UmbraCamApplication).captureThermals.status.value == 0 }
            assertFalse("Cooling must not restart capture", controller.isBusy)
            compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
            systemButton(false)
            compose.waitUntil(10_000) { !controller.isBusy }
            assertEquals(after, completedHashes())
        } finally { instrumentation.runOnMainSync { controller.stop() }; thermalCommand("reset") }
    }

    private fun completedHashes(): Map<File, String> = listOf("camera", "screen").flatMap { source ->
        File(instrumentation.targetContext.noBackupFilesDir, "$source/recordings").listFiles().orEmpty().toList()
    }.filter { it.isFile }.associateWith { file ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
            val bytes = input.read(buffer); if (bytes < 0) break; digest.update(buffer, 0, bytes)
        } }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun startInterruptionCapture() {
        compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
        systemButton(true)
        compose.waitUntil(30_000) { controller.session.value?.state == RecordingState.RECORDING }
        compose.waitUntil(10_000) { controller.ui.value.elapsedMillis >= 3_000 }
    }

    private fun assertInterruptedOutput(code: com.umbracam.core.recording.RecordingErrorCode) {
        compose.waitUntil(40_000) { !controller.isBusy }
        val session = checkNotNull(controller.session.value)
        assertEquals("Issue: ${controller.ui.value.issue}", RecordingState.SAVED, session.state)
        assertEquals(code, session.error?.code)
        assertFalse(checkNotNull(session.error).recoverable)
        val file = File(instrumentation.targetContext.noBackupFilesDir, "screen/recordings/${session.completedSegments.last().storageId}")
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.path)
            assertTrue(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 0)
            val frame = reader.getFrameAtTime(0); assertNotNull(frame); frame?.recycle()
        } finally { reader.release() }
    }

    @Test fun rotationStopsCapturePreservesFilesAndRequiresFreshConsent() {
        enterInterruptionFixture()
        val before = completedHashes()
        val owner = controller
        val original = compose.activity.requestedOrientation
        try {
            startInterruptionCapture()
            compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            assertInterruptedOutput(com.umbracam.core.recording.RecordingErrorCode.SOURCE_UNAVAILABLE)
            assertEquals(com.umbracam.app.screen.ScreenIssue.SIZE_CHANGED, owner.ui.value.issue)
        } finally {
            instrumentation.runOnMainSync { owner.stop() }
            compose.activityRule.scenario.onActivity { it.requestedOrientation = original }
        }
        val after = completedHashes()
        before.forEach { (file, hash) -> assertEquals("Earlier file changed: ${file.name}", hash, after[file]) }
        compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
        systemButton(false)
        compose.waitUntil(10_000) { !owner.isBusy }
        assertEquals(after, completedHashes())
    }

    @Test fun projectionRevocationSavesPartialAndNeverReusesConsent() {
        enterInterruptionFixture()
        val before = completedHashes()
        val owner = controller
        try {
            startInterruptionCapture()
            // Revoke the real system projection through its public stop() API. Reflection only
            // obtains the owned handle; no consent, callback or encoded sample is fabricated.
            instrumentation.runOnMainSync {
                val field = owner.javaClass.getDeclaredField("projection").apply { isAccessible = true }
                (field.get(owner) as android.media.projection.MediaProjection).stop()
            }
            assertInterruptedOutput(com.umbracam.core.recording.RecordingErrorCode.CONSENT_REVOKED)
            assertEquals(com.umbracam.app.screen.ScreenIssue.REVOKED, owner.ui.value.issue)
        } finally { instrumentation.runOnMainSync { owner.stop() } }
        val after = completedHashes()
        before.forEach { (file, hash) -> assertEquals(hash, after[file]) }
        compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
        systemButton(false)
        compose.waitUntil(10_000) { !owner.isBusy }
        assertEquals(after, completedHashes())
    }

    @Test fun externalProcessTerminationFixture() {
        enterInterruptionFixture()
        val run = InstrumentationRegistry.getArguments().getString("screenTerminationRun")
        assumeTrue("Host must provide a unique termination marker", run != null && run.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        startInterruptionCapture()
        File(instrumentation.targetContext.filesDir, "screen-termination-$run.ready").writeText(checkNotNull(controller.session.value).id)
        // Host intentionally force-stops this runner; this method must never count as a pass.
        SystemClock.sleep(60_000)
        fail("Host did not terminate the active screen recorder")
    }

    @Test fun cancellationNeverCreatesOutputAndNextStartRequestsFreshConsent() {
        enter()
        val root = File(compose.activity.noBackupFilesDir, "screen/recordings")
        val before = root.listFiles()?.map { it.name }?.toSet().orEmpty()
        repeat(2) {
            compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
            systemButton(false)
            compose.waitUntil(10_000) { !controller.isBusy }
            assertEquals(before, root.listFiles()?.map { it.name }?.toSet().orEmpty())
        }
    }

    @Test fun frontCameraCompositionRecordsBothLayoutsWithFreshConsent() {
        assumeTrue("Device must advertise a usable front camera", com.umbracam.app.screen.ReactionCapabilityDetector(compose.activity).read().frontCamera)
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        enter()
        compose.onNodeWithText("Screen + camera").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("The selected adapter must support composition", controller.session.value?.availableSources.orEmpty().isEmpty())

        listOf("Picture in picture", "Split screen").forEach { layout ->
            val previousAttempt = controller.session.value?.attemptId
            compose.onNodeWithText(layout).performScrollTo().performClick()
            compose.onNodeWithText("Start screen + camera recording").performScrollTo().performClick()
            systemButton(true)
            compose.waitUntil(45_000) {
                val current = controller.session.value
                current != null && current.attemptId != previousAttempt &&
                    current.state in setOf(RecordingState.RECORDING, RecordingState.FAILED, RecordingState.INTERRUPTED)
            }
            assertEquals("$layout issue: ${controller.ui.value.issue}", RecordingState.RECORDING, controller.session.value?.state)
            assertEquals(setOf(com.umbracam.core.recording.CaptureSource.SCREEN, com.umbracam.core.recording.CaptureSource.FRONT_CAMERA),
                controller.session.value?.availableSources)
            compose.waitUntil(10_000) { controller.ui.value.elapsedMillis >= 2_000 }
            instrumentation.runOnMainSync { controller.stop() }
            compose.waitUntil(45_000) { !controller.isBusy }
            assertEquals("$layout finalization: ${controller.ui.value.issue}", RecordingState.SAVED, controller.session.value?.state)
            val segment = controller.session.value!!.completedSegments.last()
            val file = File(compose.activity.noBackupFilesDir, "screen/recordings/${segment.storageId}")
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.path)
                assertEquals(0, metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt())
                assertNotEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val frame = metadata.getFrameAtTime(0); assertNotNull("$layout must contain a decodable composite frame", frame); frame?.recycle()
            } finally { metadata.release() }
        }
    }

    @Test fun frontCameraLossStopsCombinedCaptureAndPreservesPartialVideo() {
        assumeTrue("Device must advertise a usable front camera", com.umbracam.app.screen.ReactionCapabilityDetector(compose.activity).read().frontCamera)
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        enter()
        val previousAttempt = controller.session.value?.attemptId
        compose.onNodeWithText("Screen + camera").performScrollTo().performClick()
        compose.onNodeWithText("Start screen + camera recording").performScrollTo().performClick()
        systemButton(true)
        compose.waitUntil(45_000) {
            val current = controller.session.value
            current != null && current.attemptId != previousAttempt &&
                current.state in setOf(RecordingState.RECORDING, RecordingState.FAILED, RecordingState.INTERRUPTED)
        }
        assertEquals("Capture issue: ${controller.ui.value.issue}", RecordingState.RECORDING, controller.session.value?.state)
        compose.waitUntil(10_000) { controller.ui.value.elapsedMillis >= 2_000 }

        // Close the actual opened Camera2 source. Its StateCallback reports loss to the owner;
        // this does not fabricate a camera event or a MediaProjection grant.
        instrumentation.runOnMainSync {
            val compositorField = controller.javaClass.getDeclaredField("compositor").apply { isAccessible = true }
            val compositor = checkNotNull(compositorField.get(controller))
            val cameraField = compositor.javaClass.getDeclaredField("camera").apply { isAccessible = true }
            val camera = checkNotNull(cameraField.get(compositor)) as android.hardware.camera2.CameraDevice
            camera.close()
        }
        assertInterruptedOutput(com.umbracam.core.recording.RecordingErrorCode.SOURCE_UNAVAILABLE)
        assertEquals(com.umbracam.app.screen.ScreenIssue.FRONT_CAMERA, controller.ui.value.issue)
    }

    @Test fun realConsentProducesSilentDecodableVideoAndLibraryEntry() {
        enter()
        val originalIntent = android.content.Intent(compose.activity.intent).apply {
            flags = (flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK.inv()) or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        compose.onNodeWithText("Start screen recording").performScrollTo().performClick()
        systemButton(true)
        try {
            compose.waitUntil(30_000) { controller.session.value?.state == RecordingState.RECORDING || controller.session.value?.state == RecordingState.FAILED }
            assertEquals("Capture issue: ${controller.ui.value.issue}", RecordingState.RECORDING, controller.session.value?.state)
            compose.activityRule.scenario.recreate()
            SystemClock.sleep(5_000)
            assertTrue("Elapsed timer advances even for a static screen", controller.ui.value.elapsedMillis >= 4_000)
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME))
            SystemClock.sleep(1_000)
            assertEquals(RecordingState.RECORDING, controller.session.value?.state)
            val notification = compose.activity.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1002 }.notification
            // Return through the launcher's original Intent; notification-opening is checked
            // separately because API36 ActivityScenario cannot track an onNewIntent identity.
            instrumentation.targetContext.startActivity(originalIntent)
            compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
            notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
        } finally { instrumentation.runOnMainSync { controller.stop() } }
        compose.waitUntil(40_000) { !controller.isBusy }
        assertEquals("Finalization issue: ${controller.ui.value.issue}", RecordingState.SAVED, controller.session.value?.state)
        val segment = controller.session.value!!.completedSegments.last()
        val file = File(compose.activity.noBackupFilesDir, "screen/recordings/${segment.storageId}")
        assertTrue(file.length() > 0)
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.path)
            assertTrue(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 1000)
            assertNotEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertTrue(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt() <= 1280)
            val frame = metadata.getFrameAtTime(0)
            assertNotNull("A real frame must decode", frame); frame?.recycle()
        } finally { metadata.release() }
        compose.onNodeWithText("Open Recordings").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("recording-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Screen recording").onFirst().assertExists()
        compose.onAllNodesWithTag("recording-actions").onFirst().performClick()
        compose.onNodeWithText("Play").performClick()
        fun findPlayer(view: android.view.View): RecordingPlayerView? {
            if (view is RecordingPlayerView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount)
                findPlayer(view.getChildAt(index))?.let { return it }
            return null
        }
        lateinit var player: RecordingPlayerView
        compose.runOnIdle { player = checkNotNull(findPlayer(compose.activity.window.decorView)) }
        compose.waitUntil(15_000) { var playing = false; instrumentation.runOnMainSync { playing = player.isPlaying() }; playing }
        compose.runOnIdle { player.pause(); assertFalse(player.isPlaying()); player.seekTo(0); player.start() }
        compose.waitUntil(10_000) { var advanced = false; instrumentation.runOnMainSync { advanced = player.getCurrentPosition() > 500 }; advanced }
        compose.onNodeWithText("Close player").performScrollTo()
        compose.waitForIdle()
        File(compose.activity.getExternalFilesDir(null), "beta-player-verified.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Close player").performClick()
        compose.runOnIdle { assertFalse("Closing releases playback", player.isPlaying()) }
    }
}

