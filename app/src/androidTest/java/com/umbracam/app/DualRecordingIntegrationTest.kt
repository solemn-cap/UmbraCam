package com.umbracam.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import org.json.JSONObject

/** Actual application controller/service/storage integration; selected emulator only. */
@RunWith(AndroidJUnit4::class)
class DualRecordingIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CameraStudioViewModel
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var spec: DualRecordingSpec
    @Before fun setup() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dualIntegration") == "true")
        assumeTrue((Build.HARDWARE in setOf("ranchu", "goldfish") ||
            InstrumentationRegistry.getArguments().getString("physicalAcceptance") == "true") && Build.VERSION.SDK_INT >= 30)
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        compose.waitUntil(30_000) { !model.ui.value.initializing && !model.ui.value.serviceRunning }
        compose.runOnIdle { assertTrue(model.selectDual(null)) }
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.waitUntil(45_000) { model.ui.value.ready && !model.ui.value.initializing }
        val report = ConcurrentCameraDetector(AndroidCameraInventorySource(compose.activity)).read() as ConcurrentCameraCapabilities.Report
        val pair = report.groups.first { it.cameras.size == 2 && it.includesFrontAndRear }.cameras
        spec = DualRecordingSpec(DualCameraPair(pair.single { it.facing == CameraFacing.FRONT }, pair.single { it.facing == CameraFacing.REAR }))
    }
    @After fun cleanup() {
        if (!this::model.isInitialized) return
        compose.runOnUiThread { model.stop() }
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline && model.ui.value.serviceRunning) SystemClock.sleep(50)
    }
    private fun select(value: DualRecordingSpec = spec) {
        compose.runOnIdle { assertTrue(model.selectDual(value)) }
        compose.waitUntil(30_000) { model.ui.value.ready && !model.ui.value.loading }
        assertEquals(value, model.ui.value.dualSpecification)
    }
    private fun start() {
        compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().performClick()
        try {
            compose.waitUntil(30_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= 2_000 }
        } catch (error: Throwable) { throw AssertionError("Dual did not record: session=${model.session.value} ui=${model.ui.value}", error) }
        assertTrue(model.session.value!!.request.requiredSources.containsAll(setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA)))
    }
    private fun saved(audio: Boolean = false): File {
        compose.waitUntil(45_000) { model.session.value?.state in setOf(RecordingState.SAVED, RecordingState.FAILED) && !model.ui.value.serviceRunning }
        assertEquals("Dual finalization: ${model.ui.value}", RecordingState.SAVED, model.session.value?.state)
        val file = File(compose.activity.noBackupFilesDir, "camera/recordings/${model.session.value!!.completedSegments.last().storageId}")
        val video = MediaMetadataRetriever()
        try {
            video.setDataSource(file.path)
            val frame = video.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            assertEquals(720, frame.width); assertEquals(1280, frame.height)
            frame.recycle()
        } finally { video.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            assertEquals(1, tracks.count { it.startsWith("video/") })
            assertEquals(audio, tracks.any { it.startsWith("audio/") })
        } finally { extractor.release() }
        val journal = File(compose.activity.noBackupFilesDir, "camera/sessions/${model.session.value!!.id}.properties")
        val properties = Properties().also { journal.inputStream().use(it::load) }
        assertEquals("2", properties.getProperty("version"))
        assertEquals(model.ui.value.dualSpecification!!.layout.name, properties.getProperty("layout"))
        return file
    }
    @Test fun bothLayoutsUseTheServiceAndPublishVersionTwoWithFrozenSettings() {
        for (layout in DualLayout.entries) {
            select(spec.copy(layout = layout)); start()
            compose.runOnIdle { assertFalse(model.selectDual(spec.copy(layout = DualLayout.SPLIT))) }
            val notification = compose.activity.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }
            assertNotNull(notification)
            compose.runOnIdle { model.stop() }
            saved()
            assertNull(model.session.value!!.error)
        }
    }
    @Test fun microphoneRecreationAndHomeKeepSameAttemptUntilNotificationStop() {
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.RECORD_AUDIO)
        select(spec.copy(microphone = true)); start()
        val attempt = model.session.value!!.attemptId
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        assertEquals(attempt, model.session.value!!.attemptId)
        val duration = model.ui.value.durationMillis
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        compose.waitUntil(15_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis > duration + 1_500 }
        val notification = compose.activity.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }.notification
        notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
        saved(audio = true)
        compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    @Test fun sourceLossFinalizesWithWarningAndExplicitRecoveryRetainsEarlierBytes() {
        select(); start()
        val logical = model.session.value!!.id
        val attempt = model.session.value!!.attemptId
        val previous = Regex("Uid mode: CAMERA: (\\w+)").find(shell("cmd appops get com.umbracam.app CAMERA"))?.groupValues?.get(1) ?: "allow"
        assertTrue(previous in setOf("allow", "foreground"))
        try {
            shell("cmd appops set --uid com.umbracam.app CAMERA ignore")
            assertTrue(shell("cmd appops get com.umbracam.app CAMERA").contains("Uid mode: CAMERA: ignore"))
            compose.waitUntil(10_000) { model.session.value?.state != RecordingState.RECORDING }
            val first = saved(); val original = hash(first)
            assertEquals(RecordingErrorCode.SOURCE_UNAVAILABLE, model.session.value!!.error!!.code)
            shell("cmd appops set --uid com.umbracam.app CAMERA $previous")
            compose.runOnIdle { model.retryPreview() }
            compose.waitUntil(30_000) { model.ui.value.ready && !model.ui.value.loading }
            compose.runOnIdle { model.recover() }
            compose.waitUntil(30_000) { model.session.value?.state == RecordingState.RECORDING && model.ui.value.durationMillis >= 2_000 }
            assertEquals(logical, model.session.value!!.id)
            assertNotEquals(attempt, model.session.value!!.attemptId)
            compose.runOnIdle { model.stop() }
            saved()
            assertEquals(2, model.session.value!!.completedSegments.size)
            assertEquals(original, hash(first))
        } finally { shell("cmd appops set --uid com.umbracam.app CAMERA $previous") }
    }
    @Test fun immediateStopAndUnsupportedOrientationNeverClaimRecording() {
        compose.runOnIdle { assertFalse(model.selectDual(spec.copy(shape = DualOutputShape.LANDSCAPE))) }
        select()
        compose.runOnIdle { model.start(); model.stop() }
        compose.waitUntil(30_000) { model.session.value?.state == RecordingState.FAILED && !model.ui.value.serviceRunning }
        assertTrue(model.session.value!!.completedSegments.isEmpty())
        assertTrue(model.session.value!!.availableSources.isEmpty())
        SystemClock.sleep(6_000)
    }
    @Test fun publicationFailurePreservesPriorFilesAndRetainsUnpublishedOutput() {
        select(); start()
        val folder = File(compose.activity.noBackupFilesDir, "camera/recordings")
        val before = folder.listFiles().orEmpty().associate { it.name to hash(it) }
        val quarantinedBefore = File(compose.activity.noBackupFilesDir, "camera/quarantine").listFiles().orEmpty().size
        val mode = android.system.Os.stat(folder.path).st_mode and 0x1ff
        try {
            android.system.Os.chmod(folder.path, 0x140)
            compose.runOnIdle { model.stop() }
            compose.waitUntil(30_000) { model.session.value?.state == RecordingState.FAILED && !model.ui.value.serviceRunning }
            assertEquals(RecordingErrorCode.FINALIZATION_FAILED, model.session.value!!.error!!.code)
            assertEquals(before, folder.listFiles().orEmpty().associate { it.name to hash(it) })
            assertTrue(File(compose.activity.noBackupFilesDir, "camera/quarantine").listFiles().orEmpty().size > quarantinedBefore ||
                File(compose.activity.noBackupFilesDir, "camera/pending").listFiles().orEmpty().isNotEmpty())
        } finally { android.system.Os.chmod(folder.path, mode) }
    }
    @Test fun deniedRequestedMicrophoneNeverBecomesSilentDualRecording() {
        assumeTrue("Revoke RECORD_AUDIO on the selected emulator before starting this fixture", !model.hasPermission(Manifest.permission.RECORD_AUDIO))
        select(spec.copy(microphone = true))
        compose.runOnIdle { model.start() }
        compose.waitUntil(30_000) { model.session.value?.state == RecordingState.FAILED && !model.ui.value.serviceRunning }
        assertTrue(CaptureSource.MICROPHONE in model.session.value!!.request.requiredSources)
        assertTrue(model.session.value!!.completedSegments.isEmpty())
        assertTrue(model.session.value!!.availableSources.isEmpty())
    }
    @Test fun unadvertisedPairNeverStarts() {
        val previous = model.session.value
        val invalid = spec.copy(pair = spec.pair.copy(front = CameraDescriptor("missing-camera-id", CameraFacing.FRONT)))
        compose.runOnIdle { assertTrue(model.selectDual(invalid)) }
        compose.waitUntil(15_000) { !model.ui.value.loading && model.ui.value.issue != null }
        assertFalse(model.ui.value.ready)
        compose.runOnIdle { model.start() }
        assertEquals(previous, model.session.value)
        assertFalse(model.ui.value.serviceRunning)
    }
    /** Host must terminate the app after the unique marker appears; a killed runner is not a pass. */
    @Test fun externalTerminationFixture() {
        val run = InstrumentationRegistry.getArguments().getString("terminationRun")
        assumeTrue("External termination fixture is explicitly opted in", run?.matches(Regex("[a-f0-9-]{36}")) == true)
        select(); start()
        compose.runOnIdle { model.stop() }
        val prior = saved()
        compose.waitUntil(15_000) { model.ui.value.ready }
        start()
        val value = JSONObject().put("id", model.session.value!!.id).put("attempt", model.session.value!!.attemptId)
            .put("state", model.session.value!!.state.name).put("serviceRunning", model.ui.value.serviceRunning)
            .put("prior", prior.name).put("priorSHA256", MessageDigest.getInstance("SHA-256").digest(prior.readBytes()).joinToString("") { "%02x".format(it) })
        File(compose.activity.filesDir, "phase4-termination-$run.json").writeText(value.toString())
        SystemClock.sleep(60_000)
        fail("Host did not terminate the opted-in fixture within its deadline")
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
}
