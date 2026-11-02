package com.umbracam.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Opt-in sustained product-path observations. Emulator evidence never certifies physical support. */
@RunWith(AndroidJUnit4::class)
class DualRecordingAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CameraStudioViewModel
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var spec: DualRecordingSpec
    private lateinit var runId: String
    private var seconds = 300

    @Before fun setup() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("dualAcceptance") == "true")
        assumeTrue((Build.HARDWARE in setOf("ranchu", "goldfish") || args.getString("physicalAcceptance") == "true") && Build.VERSION.SDK_INT >= 30)
        runId = UUID.fromString(requireNotNull(args.getString("acceptanceRun"))).toString()
        seconds = args.getString("acceptanceSeconds", "300").toInt().also { require(it in 30..300) }
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission("com.umbracam.app", Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        compose.waitUntil(30_000) { !model.ui.value.initializing && !model.ui.value.serviceRunning }
        compose.runOnIdle { assertTrue(model.selectDual(null)) }
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.waitUntil(40_000) { model.ui.value.ready && model.dualOptions.value.pairs.isNotEmpty() }
        spec = DualRecordingSpec(model.dualOptions.value.pairs.first(), microphone = true)
    }

    @After fun cleanup() {
        if (!this::model.isInitialized) return
        compose.runOnUiThread { model.stop() }
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (model.ui.value.serviceRunning && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
    }

    @Test fun sustainedPictureInPicture() = capture(DualLayout.PICTURE_IN_PICTURE)
    @Test fun sustainedSplit() = capture(DualLayout.SPLIT)

    private fun capture(layout: DualLayout) {
        val root = File(compose.activity.noBackupFilesDir, "camera/recordings")
        val before = root.listFiles().orEmpty().associate { it.name to sha(it) }
        val output = File(compose.activity.filesDir, "phase4-acceptance/$runId-${layout.name}.json")
        check(!output.exists()) { "Acceptance artifact already exists; use a fresh run UUID" }
        output.parentFile!!.mkdirs()
        val result = JSONObject().put("runId", runId).put("environment",
            if (Build.HARDWARE in setOf("ranchu", "goldfish")) "EMULATOR" else "PHYSICAL")
            .put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT).put("sdk", Build.VERSION.SDK_INT)
            .put("layout", layout.name).put("frontId", spec.pair.front.id).put("rearId", spec.pair.rear.id)
            .put("microphone", true).put("requestedHoldSeconds", seconds).put("profile", "portrait HD SDR; requested 30fps")
        try {
            compose.runOnIdle { assertTrue(model.selectDual(spec.copy(layout = layout))) }
            compose.waitUntil(40_000) { model.ui.value.ready && !model.ui.value.loading }
            compose.onNodeWithText("Start recording").performScrollTo().performClick()
            compose.waitUntil(30_000) { model.session.value?.state == RecordingState.RECORDING }
            val attempt = model.session.value!!.attemptId
            val start = SystemClock.elapsedRealtime()
            var home = false
            var nextLog = 0L
            while (SystemClock.elapsedRealtime() - start < seconds * 1_000L) {
                val elapsed = SystemClock.elapsedRealtime() - start
                assertEquals("Capture ended early: ${model.ui.value}", RecordingState.RECORDING, model.session.value?.state)
                assertEquals(attempt, model.session.value?.attemptId)
                if (!home && elapsed >= seconds * 500L) {
                    assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                    home = true
                    result.put("homeAtElapsedMs", elapsed)
                }
                if (elapsed >= nextLog) {
                    Log.i("UmbraAcceptance", "$runId $layout elapsedMs=$elapsed state=${model.session.value?.state}")
                    nextLog += 30_000
                }
                SystemClock.sleep(100)
            }
            result.put("heldMs", SystemClock.elapsedRealtime() - start)
            val notification = compose.activity.getSystemService(NotificationManager::class.java).activeNotifications
                .single { it.id == RecordingService.NOTIFICATION_ID }.notification
            notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
            compose.waitUntil(45_000) { !model.ui.value.serviceRunning && model.session.value?.state in setOf(RecordingState.SAVED, RecordingState.FAILED) }
            assertEquals(model.ui.value.toString(), RecordingState.SAVED, model.session.value?.state)
            assertNull(model.session.value!!.error)
            val file = File(root, model.session.value!!.completedSegments.last().storageId)
            result.put("storageId", file.name).put("bytes", file.length()).put("sha256", sha(file))
            inspect(file, result)
            assertTrue("Earlier completed bytes changed", before.all { (name, hash) -> File(root, name).isFile && sha(File(root, name)) == hash })
            result.put("priorCompletedCount", before.size).put("priorFilesUnchanged", true).put("outcome", "PASS")
        } catch (error: Throwable) {
            result.put("outcome", "FAIL").put("failure", error.toString()).put("session", model.session.value.toString()).put("ui", model.ui.value.toString())
            throw error
        } finally {
            output.writeText(result.toString(2))
            Log.i("UmbraAcceptance", "artifact=${output.name} outcome=${result.optString("outcome")}")
        }
    }

    private fun inspect(file: File, result: JSONObject) {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.path)
            val durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Recorded duration shorter than hold", durationMs >= seconds * 1_000L - 2_000)
            assertEquals("0", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
            for (timeMs in listOf(500L, durationMs / 2, durationMs - 500)) {
                val frame = checkNotNull(metadata.getFrameAtTime(timeMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST))
                assertEquals(720, frame.width); assertEquals(1280, frame.height)
                result.put("decodedWidth", frame.width).put("decodedHeight", frame.height)
                frame.recycle()
            }
            result.put("durationMs", durationMs).put("decodedProbes", 3)
        } finally { metadata.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks = JSONArray()
            var video = 0; var audio = 0
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) video++ else if (mime.startsWith("audio/")) audio++
                extractor.selectTrack(index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val times = mutableListOf<Long>()
                while (extractor.sampleTime >= 0) { times.add(extractor.sampleTime); extractor.advance() }
                extractor.unselectTrack(index)
                assertTrue("Empty track $mime", times.size > 1)
                val sorted = times.sorted()
                val gaps = sorted.zipWithNext { a, b -> b - a }.sorted()
                val span = sorted.last() - sorted.first()
                assertTrue("Zero track span", span > 0)
                val track = JSONObject().put("mime", mime).put("samples", times.size)
                    .put("firstPtsUs", sorted.first()).put("lastPtsUs", sorted.last())
                    .put("observedSamplesPerSecond", (times.size - 1) * 1_000_000.0 / span)
                    .put("p95GapUs", gaps[((gaps.size - 1) * .95).toInt()]).put("maxGapUs", gaps.last())
                    .put("decodeOrderReversals", times.zipWithNext().count { (a, b) -> b < a })
                if (mime.startsWith("video/")) {
                    track.put("formatWidth", format.getInteger(MediaFormat.KEY_WIDTH))
                        .put("formatHeight", format.getInteger(MediaFormat.KEY_HEIGHT))
                        .put("reportedFrameRate", runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() ?: JSONObject.NULL)
                }
                tracks.put(track)
            }
            assertEquals(1, video); assertEquals(1, audio)
            result.put("tracks", tracks)
        } finally { extractor.release() }
    }

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65_536); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
