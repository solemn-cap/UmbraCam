package com.umbracam.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.CameraStudioViewModel
import com.umbracam.app.camera.CameraIssue
import com.umbracam.app.camera.RecordingService
import com.umbracam.core.recording.RecordingState
import com.umbracam.core.recording.SingleCameraSessionCoordinator
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real CameraX integration on the selected Android target. Emulator results are not hardware proof.
 * Fixture grants are test-only; product permission flows are exercised separately.
 * Test-created video files are retained; existing recordings are never deleted.
 */
@RunWith(AndroidJUnit4::class)
class CameraRecordingTest {
    @Test fun emulatorThermalStopFinalizesCameraAndReleasesPreview() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") && InstrumentationRegistry.getArguments().getString("thermalAcceptance") == "true")
        shell("cmd thermalservice override-status 0")
        try {
            enterCamera()
            startAndWaitForFrames()
            shell("cmd thermalservice override-status 3")
            val file = awaitSavedFile()
            verifyDecodableFrameAndTracks(file, false, "thermal-stop")
            assertEquals(com.umbracam.core.recording.RecordingErrorCode.THERMAL_LIMIT, model.session.value!!.error!!.code)
            assertFalse("Hot preview must be released", model.ui.value.ready)
            shell("cmd thermalservice override-status 2")
            val attempt = model.session.value!!.attemptId
            compose.runOnIdle { model.start() }
            assertEquals(attempt, model.session.value!!.attemptId)
            assertEquals(com.umbracam.app.camera.CameraIssue.THERMAL, model.ui.value.issue)
            shell("cmd thermalservice override-status 0")
            compose.runOnIdle { model.retryPreview() }
            compose.waitUntil(30_000) { model.ui.value.ready }
            assertNotEquals(RecordingState.RECORDING, model.session.value!!.state)
        } finally { if (this::model.isInitialized) compose.runOnUiThread { model.stop() }; shell("cmd thermalservice reset") }
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CameraStudioViewModel

    @Before fun keepTestWindowAwake() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After fun stopTestCapture() {
        if (!this::model.isInitialized) return
        InstrumentationRegistry.getInstrumentation().runOnMainSync { model.stop() }
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline && (model.session.value?.state in SingleCameraSessionCoordinator.busyStates || model.ui.value.serviceRunning)) SystemClock.sleep(50)
    }

    private fun enterCamera(audio: Boolean = false) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission("com.umbracam.app", Manifest.permission.POST_NOTIFICATIONS)
        if (audio) automation.grantRuntimePermission("com.umbracam.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.waitUntil(45_000) { model.ui.value.ready && !model.ui.value.initializing && !model.ui.value.serviceRunning }
        compose.runOnIdle { model.setMicrophone(audio) }
    }

    private fun startAndWaitForFrames() {
        compose.waitUntil(10_000) { !model.ui.value.serviceRunning }
        compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().performClick()
        compose.waitUntil(30_000) {
            model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= 1_500
        }
    }

    private fun awaitSavedFile(): File {
        compose.waitUntil(45_000) { model.session.value?.state in setOf(RecordingState.SAVED, RecordingState.FAILED) }
        assertEquals("Finalization failed: ${model.ui.value.issue}", RecordingState.SAVED, model.session.value?.state)
        compose.waitUntil(10_000) { !model.ui.value.serviceRunning }
        val segment = model.session.value!!.completedSegments.last()
        return File(compose.activity.noBackupFilesDir, "camera/recordings/${segment.storageId}").also {
            assertTrue(it.isFile && it.length() > 0)
        }
    }

    private fun verifyDecodableFrameAndTracks(file: File, expectAudio: Boolean, caseName: String) {
        val retriever = MediaMetadataRetriever()
        var frameSize = ""
        var duration = ""
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            assertNotNull("A finalized file must contain a decodable video frame", frame)
            assertTrue(frame!!.width > 0 && frame.height > 0)
            frameSize = "${frame.width}x${frame.height}"
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).orEmpty()
            frame.recycle()
        } finally { retriever.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val types = (0 until extractor.trackCount).map {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
            }
            assertTrue(types.any { it.startsWith("video/") })
            assertEquals("Unexpected audio track set", expectAudio, types.any { it.startsWith("audio/") })
            Log.i("UmbraCamValidation", "PASS case=$caseName lens=${model.ui.value.lens} quality=${model.ui.value.quality} durationMs=$duration frame=$frameSize tracks=$types warning=${model.session.value?.error?.code}")
        } finally { extractor.release() }
    }

    @Test fun availableFrontAndRearRecordSilentDecodableVideos() {
        enterCamera()
        val lenses = model.ui.value.lenses.toList()
        assertTrue(lenses.isNotEmpty())
        Log.i("UmbraCamValidation", "Available lenses: $lenses")
        for (lens in lenses) {
            compose.runOnIdle { model.chooseLens(lens) }
            compose.waitUntil(45_000) { model.ui.value.ready && model.ui.value.lens == lens }
            startAndWaitForFrames()
            compose.onNodeWithText(compose.activity.getString(R.string.camera_stop)).performScrollTo().performClick()
            verifyDecodableFrameAndTracks(awaitSavedFile(), expectAudio = false, caseName = "silent-$lens")
        }
    }

    @Test fun microphoneRecordingContainsAudioAndVideoTracks() {
        enterCamera(audio = true)
        assertTrue(model.ui.value.audioEnabled)
        startAndWaitForFrames()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_stop)).performScrollTo().performClick()
        verifyDecodableFrameAndTracks(awaitSavedFile(), expectAudio = true, caseName = "microphone")
    }

    @Test fun recreationKeepsTheSameAuthorizedRecording() {
        enterCamera()
        startAndWaitForFrames()
        val original = model
        val attempt = model.session.value!!.attemptId
        val duration = model.ui.value.durationMillis
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java]
            assertSame(original, model)
            assertEquals(attempt, model.session.value?.attemptId)
        }
        compose.waitUntil(15_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= duration + 1_500 }
        compose.onNodeWithText(compose.activity.getString(R.string.camera_stop)).performScrollTo().performClick()
        verifyDecodableFrameAndTracks(awaitSavedFile(), expectAudio = false, caseName = "recreation")
        compose.waitUntil(45_000) { model.ui.value.ready }
        assertEquals(RecordingState.SAVED, model.session.value?.state)
    }

    @Test fun immediateStopDuringPreparationNeverStartsRecording() {
        enterCamera()
        compose.runOnIdle { model.start(); model.stop() }
        compose.waitUntil(30_000) { model.session.value?.state == RecordingState.FAILED }
        compose.waitUntil(10_000) { !model.ui.value.serviceRunning }
        // Keep instrumentation alive past Android's foreground-promotion deadline to catch delayed crashes.
        SystemClock.sleep(6_000)
        assertTrue(model.session.value!!.completedSegments.isEmpty())
        assertTrue(model.session.value!!.availableSources.isEmpty())
        assertNotEquals(RecordingState.RECORDING, model.session.value?.state)
        Log.i("UmbraCamValidation", "PASS case=immediate-stop state=${model.session.value?.state} savedSegments=0")
    }

    @Test fun homeKeepsRecordingAndNotificationStopFinalizes() {
        enterCamera(audio = true)
        startAndWaitForFrames()
        val lastDurationBeforeHome = model.ui.value.durationMillis
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        compose.waitUntil(15_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= lastDurationBeforeHome + 1_500 }
        val manager = compose.activity.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }.notification
        assertEquals(compose.activity.getString(R.string.notification_recording), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
        val saved = awaitSavedFile()
        assertTrue(model.session.value!!.completedSegments.last().durationMillis >= lastDurationBeforeHome + 1_000)
        assertFalse(manager.activeNotifications.any { it.id == RecordingService.NOTIFICATION_ID })
        // Foreground the stopped task explicitly, without creating or authorizing another recording.
        val launchFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val launchCommand = "am start -W -n com.umbracam.app/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f $launchFlags"
        val launchResult = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(launchCommand)
        ParcelFileDescriptor.AutoCloseInputStream(launchResult).bufferedReader().use { it.readText() }
        verifyDecodableFrameAndTracks(saved, expectAudio = true, caseName = "system-home")
        compose.waitUntil(45_000) { model.ui.value.ready }
        compose.runOnIdle { assertSame(model, ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java]) }
        assertEquals(RecordingState.SAVED, model.session.value?.state)
    }

    @Test fun navigatingHomeKeepsRecordingUntilExplicitStop() {
        enterCamera()
        startAndWaitForFrames()
        val attempt = model.session.value!!.attemptId
        val duration = model.ui.value.durationMillis
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back_home)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.tagline)).assertExists()
        compose.waitUntil(15_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= duration + 1_500 }
        compose.onNodeWithText(compose.activity.getString(R.string.open_recording)).performScrollTo().performClick()
        assertEquals(attempt, model.session.value?.attemptId)
        compose.onNodeWithText(compose.activity.getString(R.string.camera_stop)).performScrollTo().performClick()
        verifyDecodableFrameAndTracks(awaitSavedFile(), expectAudio = false, caseName = "navigate-home")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    @Test fun blockedNotificationsPreventRecording() {
        assumeTrue("Notification Settings fixture targets the API29 emulator", Build.FINGERPRINT.contains("generic") && Build.VERSION.SDK_INT == 29)
        enterCamera()
        assertTrue(RecordingService.notificationsAllowed(compose.activity))
        val before = model.session.value
        try {
            setEmulatorNotificationsEnabled(false)
            compose.waitUntil(5_000) { !RecordingService.notificationsAllowed(compose.activity) }
            compose.waitUntil(30_000) { model.ui.value.ready && !model.ui.value.loading }
            compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().performClick()
            compose.waitUntil(5_000) { model.ui.value.issue == CameraIssue.NOTIFICATION_PERMISSION }
            assertEquals(before, model.session.value)
            assertFalse(model.ui.value.serviceRunning)
        } finally { setEmulatorNotificationsEnabled(true) }
    }

    /** Exercise Android's actual user setting; changing AppOps alone does not update API29's
     * notification preference. The caller always restores the initially enabled state. */
    private fun setEmulatorNotificationsEnabled(enabled: Boolean) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("am start -W -a android.settings.APP_NOTIFICATION_SETTINGS --es android.provider.extra.APP_PACKAGE com.umbracam.app")
            val until = SystemClock.uptimeMillis() + 10_000
            var control: android.view.accessibility.AccessibilityNodeInfo? = null
            while (control == null && SystemClock.uptimeMillis() < until) {
                control = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")?.firstOrNull()
                if (control == null) SystemClock.sleep(50)
            }
            val toggle = control ?: throw AssertionError("API29 Show notifications switch not found")
            if (toggle.isChecked != enabled) {
                val bounds = Rect().also(toggle::getBoundsInScreen)
                shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            }
            while (SystemClock.uptimeMillis() < until) {
                val current = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")?.firstOrNull()
                if (current?.isChecked == enabled) break
                SystemClock.sleep(50)
            }
            assertEquals(enabled, automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")?.firstOrNull()?.isChecked)
        } finally { automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags } }
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        shell("am start -W -n com.umbracam.app/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f $flags")
    }

    @Test fun unwritableStagingDirectoryPreservesCompletedFiles() {
        assumeTrue("Filesystem fault fixture is emulator-only", Build.FINGERPRINT.contains("generic"))
        enterCamera()
        val root = File(compose.activity.noBackupFilesDir, "camera")
        val completed = File(root, "recordings")
        fun savedFiles() = completed.listFiles().orEmpty().associate { it.name to it.length() }
        val preserved = savedFiles()
        val previousAttempt = model.session.value?.attemptId
        val pending = File(root, "pending").also { assertTrue(it.isDirectory || it.mkdirs()) }
        val previousMode = android.system.Os.stat(pending.path).st_mode and 0x1ff
        try {
            android.system.Os.chmod(pending.path, 0x140) // owner read/execute, no write
            assertFalse(pending.canWrite())
            compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().performClick()
            compose.waitUntil(15_000) { model.session.value?.state == RecordingState.FAILED && !model.ui.value.serviceRunning }
            assertNotEquals(previousAttempt, model.session.value?.attemptId)
            assertNotNull(model.session.value?.error)
            assertTrue(model.session.value!!.availableSources.isEmpty())
            assertEquals(preserved, savedFiles())
        } finally { android.system.Os.chmod(pending.path, previousMode) }
    }

    @Test fun notificationOpensCameraOnceWithoutRestartingCapture() {
        enterCamera()
        startAndWaitForFrames()
        val attempt = model.session.value!!.attemptId
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back_home)).performClick()
        val manager = compose.activity.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }.notification.contentIntent.send()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_intro)).performScrollTo().assertIsDisplayed()
        assertEquals(attempt, model.session.value?.attemptId)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back_home)).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.tagline)).assertExists()
        assertEquals(attempt, model.session.value?.attemptId)
        assertEquals(RecordingState.RECORDING, model.session.value?.state)
        manager.activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }.notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
        verifyDecodableFrameAndTracks(awaitSavedFile(), expectAudio = false, caseName = "notification-open")
    }

    @Test fun sourceLossRequiresExplicitRecoveryAndPreservesCompletedSegments() {
        assumeTrue("Use -e cameraLossTest true to opt in on physical hardware",
            Build.FINGERPRINT.contains("generic") || InstrumentationRegistry.getArguments().getString("cameraLossTest") == "true")
        enterCamera()
        // Runtime permission policy is UID-scoped; package mode can be overridden by UID mode.
        val previousOps = shell("cmd appops get com.umbracam.app CAMERA")
        val previousUidMode = Regex("Uid mode: CAMERA: (\\w+)").find(previousOps)?.groupValues?.get(1) ?: "default"
        assumeTrue("Camera UID mode must initially permit capture", previousUidMode in setOf("allow", "foreground", "default"))
        startAndWaitForFrames()
        val logicalSession = model.session.value!!.id
        val oldAttempt = model.session.value!!.attemptId
        try {
            shell("cmd appops set --uid com.umbracam.app CAMERA ignore")
            assertTrue("UID policy change must take effect", shell("cmd appops get com.umbracam.app CAMERA").contains("Uid mode: CAMERA: ignore"))
            compose.waitUntil(30_000) { model.session.value?.state in setOf(RecordingState.SAVED, RecordingState.FAILED) && !model.ui.value.serviceRunning }
            val preserved = model.session.value!!.completedSegments.toList()
            assertTrue(model.session.value!!.error!!.recoverable)
            assertTrue(model.session.value!!.availableSources.isEmpty())
            shell("cmd appops set --uid com.umbracam.app CAMERA $previousUidMode")
            compose.runOnIdle { model.retryPreview() }
            compose.waitUntil(30_000) { model.ui.value.ready }
            assertEquals(oldAttempt, model.session.value?.attemptId)
            compose.onNodeWithText(compose.activity.getString(R.string.camera_resume)).performScrollTo().performClick()
            try {
                compose.waitUntil(30_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= 1_500 }
            } catch (failure: Throwable) {
                throw AssertionError("Recovery did not record: state=${model.session.value?.state}, error=${model.session.value?.error}, newAttempt=${oldAttempt != model.session.value?.attemptId}, ui=${model.ui.value}", failure)
            }
            assertNotEquals(oldAttempt, model.session.value?.attemptId)
            assertEquals(logicalSession, model.session.value?.id)
            compose.onNodeWithText(compose.activity.getString(R.string.camera_stop)).performScrollTo().performClick()
            val video = awaitSavedFile()
            assertEquals(preserved, model.session.value!!.completedSegments.take(preserved.size))
            assertEquals(preserved.size + 1, model.session.value!!.completedSegments.size)
            verifyDecodableFrameAndTracks(video, expectAudio = false, caseName = "explicit-recovery")
        } finally { shell("cmd appops set --uid com.umbracam.app CAMERA $previousUidMode") }
    }
}
