package com.umbracam.app

import android.Manifest
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Bounded, emulator-only backend experiment. Not product recording or physical acceptance.
 * Files stay separate from the app's recording store. Never clears existing media.
 * Run with -e dualFeasibility true on an explicitly selected isolated emulator.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
@RunWith(AndroidJUnit4::class)
class DualCameraFeasibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Progress(val frame: Long, val count: Int, val receivedMs: Long)

    @Test fun composedLayoutsExposeBothInputProgressAndDecodableOutput() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit spike opt-in required", args.getString("dualFeasibility") == "true")
        assumeTrue("Isolated emulator only", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = compose.activity
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val provider = ProcessCameraProvider.getInstance(context).get(30, TimeUnit.SECONDS)
        val manager = context.getSystemService(CameraManager::class.java)
        val advertised = manager.concurrentCameraIds
        val candidates = provider.availableConcurrentCameraInfos.map { infos ->
            infos.map { Camera2CameraInfo.from(it).cameraId }.toSet()
        }
        val pair = candidates.firstOrNull { ids -> ids.size == 2 && ids in advertised &&
            ids.map { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) }.toSet() ==
            setOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK)
        }
        assertNotNull("No exact advertised front/rear pair: Camera2=$advertised CameraX=$candidates", pair)
        val rear = pair!!.single { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
        val front = (pair - rear).single()
        val directory = File(context.filesDir, "phase4-feasibility/${System.currentTimeMillis()}").apply { mkdirs() }
        val summary = JSONObject().put("fingerprint", Build.FINGERPRINT).put("camera2Groups", advertised.toString())
            .put("cameraXGroups", candidates.toString()).put("rear", rear).put("front", front)
            .put("markerFixture", args.getString("provenanceMarkers") == "true")
            .put("scope", "emulator composition and callback experiment; no product service or physical claims")
        val cases = JSONArray()
        summary.put("cases", cases)
        try {
            for (layout in listOf("pip", "split", "pip-loss")) {
                val result = runCase(provider, directory, rear, front, layout)
                cases.put(result)
                File(directory, "summary.json").writeText(summary.toString(2))
            }
            assertTrue("Both inputs must advance through interop in both layouts: $summary",
                (0 until cases.length()).all { cases.getJSONObject(it).getBoolean("bothInputsObserved") })
            assertTrue("Both inputs must advance after preview detach: $summary",
                (0 until cases.length()).all { cases.getJSONObject(it).getBoolean("bothAdvanceAfterDetach") })
            assertTrue("Both input signals must remain fresh after preview detach",
                (0 until cases.length()).all { cases.getJSONObject(it).getBoolean("bothFreshAfterDetach") })
            assertTrue("Both input progress signals must become stale after UID denial",
                cases.getJSONObject(2).getBoolean("bothStaleAfterDenial"))
        } finally {
            instrumentation.runOnMainSync { provider.unbindAll() }
            File(directory, "summary.json").writeText(summary.toString(2))
            Log.i("UmbraCamPhase4", "Evidence directory=${directory.absolutePath} summary=$summary")
        }
    }

    private fun runCase(provider: ProcessCameraProvider, directory: File, rear: String, front: String, layout: String): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val executor = ContextCompat.getMainExecutor(compose.activity)
        val progress = ConcurrentHashMap<String, Progress>()
        val deviceEvents = ConcurrentHashMap<String, String>()
        val denySources = layout == "pip-loss"
        val start = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val encodedBytes = AtomicLong()
        var finalEvent: VideoRecordEvent.Finalize? = null
        var recording: Recording? = null
        var preview: Preview? = null
        var view: PreviewView? = null
        var recorder: Recorder? = null
        // CameraX caches adapters by lifecycle identity and camera pair. A fresh owner is
        // required for a new immutable layout; unbindAll alone retained the prior PiP.
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        var beforeDetach = emptyMap<String, Progress>()
        var afterDetach = emptyMap<String, Progress>()
        val file = File(directory, "$layout.mp4")
        val result = JSONObject().put("layout", layout).put("requestedProfile", "HD SDR 30fps silent rotation0")
        try {
            instrumentation.runOnMainSync {
                compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                owner.registry.currentState = Lifecycle.State.RESUMED
                view = PreviewView(compose.activity).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                compose.activity.setContentView(view)
                val builder = Preview.Builder().setTargetRotation(Surface.ROTATION_0).setTargetFrameRate(Range(30, 30))
                Camera2Interop.Extender(builder).setDeviceStateCallback(object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) { deviceEvents[camera.id] = "opened" }
                    override fun onDisconnected(camera: CameraDevice) { deviceEvents[camera.id] = "disconnected" }
                    override fun onError(camera: CameraDevice, error: Int) { deviceEvents[camera.id] = "error:$error" }
                    override fun onClosed(camera: CameraDevice) { deviceEvents[camera.id] = "closed" }
                }).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, capture: TotalCaptureResult) {
                        val id = session.device.id
                        progress.compute(id) { _, prior ->
                            if (prior == null || capture.frameNumber > prior.frame)
                                Progress(capture.frameNumber, (prior?.count ?: 0) + 1, SystemClock.elapsedRealtime())
                            else prior
                        }
                    }
                })
                preview = builder.build().also { it.setSurfaceProvider(view!!.surfaceProvider) }
                recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
                val video = VideoCapture.Builder(recorder!!).setTargetRotation(Surface.ROTATION_0).build()
                val group = UseCaseGroup.Builder().addUseCase(preview!!).addUseCase(video).build()
                // The pinned concurrent binder branches on selector lensFacing as well as IDs.
                // ID-only filters leave both facings null and enter its same-camera path.
                fun selector(id: String) = CameraSelector.Builder().requireLensFacing(
                    if (id == rear) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                ).addCameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == id }
                }.build()
                val primary = if (layout.startsWith("pip")) CompositionSettings.DEFAULT else
                    CompositionSettings.Builder().setAlpha(1f).setScale(1f, .5f).setOffset(0f, .5f).build()
                val secondary = if (layout.startsWith("pip"))
                    CompositionSettings.Builder().setAlpha(1f).setScale(.32f, .32f).setOffset(.6f, .6f).build()
                else CompositionSettings.Builder().setAlpha(1f).setScale(1f, .5f).setOffset(0f, -.5f).build()
                val concurrent = provider.bindToLifecycle(listOf(
                    SingleCameraConfig(selector(rear), group, primary, owner),
                    SingleCameraConfig(selector(front), group, secondary, owner)
                ))
                assertTrue(provider.isConcurrentCameraModeOn)
                val boundIds = concurrent.cameras.map { Camera2CameraInfo.from(it.cameraInfo).cameraId }.toSet()
                result.put("boundIds", JSONArray(boundIds.toList()))
                // Composition returns one wrapper around both cameras, not two public Cameras.
                // Exact selector resolution and both per-device callbacks are the actual gates.
                assertEquals(rear, Camera2CameraInfo.from(provider.getCameraInfo(selector(rear))).cameraId)
                assertEquals(front, Camera2CameraInfo.from(provider.getCameraInfo(selector(front))).cameraId)
                recording = recorder!!.prepareRecording(compose.activity, FileOutputOptions.Builder(file).build())
                    .start(executor) { event ->
                        encodedBytes.set(event.recordingStats.numBytesRecorded)
                        if (event is VideoRecordEvent.Start) start.countDown()
                        if (event is VideoRecordEvent.Finalize) { finalEvent = event; finish.countDown() }
                    }
            }
            assertTrue("Recorder Start timed out", start.await(20, TimeUnit.SECONDS))
            // Recorder Start precedes usable input/output on a cold emulator. Begin the
            // observation window only once both sources AND encoded bytes are established.
            val acquisitionDeadline = SystemClock.elapsedRealtime() + 10_000
            fun acquired(): Boolean = encodedBytes.get() > 0 && setOf(rear, front).all {
                progress[it]?.let { value -> value.count >= 2 &&
                    SystemClock.elapsedRealtime() - value.receivedMs < 1_500 } == true
            }
            while (!acquired() && SystemClock.elapsedRealtime() < acquisitionDeadline) SystemClock.sleep(50)
            assertTrue("Both fresh inputs and encoded output required within acquisition deadline: ${progressJson(progress.toMap())}", acquired())
            result.put("readyAtMs", SystemClock.elapsedRealtime())
            SystemClock.sleep(3_000)
            beforeDetach = progress.toMap()
            instrumentation.runOnMainSync { preview!!.setSurfaceProvider(null) }
            SystemClock.sleep(3_000)
            afterDetach = progress.toMap()
            val detachedAt = SystemClock.elapsedRealtime()
            result.put("bothFreshAfterDetach", setOf(rear, front).all {
                afterDetach[it]?.let { value -> detachedAt - value.receivedMs < 1_500 } == true
            })
            if (denySources) {
                val previous = shell("cmd appops get com.umbracam.app CAMERA")
                // This API30 image omits the UID line for CAMERA's default allow mode.
                // Sending the CLI string "default" after ignore leaves ignore in place;
                // explicit allow removes that override. Verify restoration instead of assuming it.
                val uidMode = Regex("Uid mode: CAMERA: (\\w+)")
                val mode = uidMode.find(previous)?.groupValues?.get(1) ?: "allow"
                assertTrue("Unexpected initial UID mode: $previous", mode in setOf("allow", "foreground"))
                try {
                    shell("cmd appops set --uid com.umbracam.app CAMERA ignore")
                    assertTrue(shell("cmd appops get com.umbracam.app CAMERA").contains("Uid mode: CAMERA: ignore"))
                    SystemClock.sleep(3_000)
                    val now = SystemClock.elapsedRealtime()
                    result.put("afterDenial", progressJson(progress.toMap()))
                        .put("deviceEventsAfterDenial", JSONObject(deviceEvents.toMap()))
                        .put("bothStaleAfterDenial", setOf(rear, front).all {
                            progress[it]?.let { value -> now - value.receivedMs >= 1_500 } == true
                        })
                } finally {
                    shell("cmd appops set --uid com.umbracam.app CAMERA $mode")
                    val restored = shell("cmd appops get com.umbracam.app CAMERA")
                    assertEquals("UID camera policy was not restored: $restored", mode,
                        uidMode.find(restored)?.groupValues?.get(1) ?: "allow")
                }
            }
        } finally {
            instrumentation.runOnMainSync { recording?.stop() }
            if (recording != null) assertTrue("Finalize timed out", finish.await(30, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                provider.unbindAll()
                preview?.setSurfaceProvider(null)
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
        }
        result.put("finalizeError", finalEvent?.error).put("bytes", file.length())
        result.put("beforeDetach", progressJson(beforeDetach)).put("afterDetach", progressJson(afterDetach))
        result.put("bothInputsObserved", setOf(rear, front).all { (afterDetach[it]?.count ?: 0) >= 2 })
        result.put("bothAdvanceAfterDetach", setOf(rear, front).all {
            (afterDetach[it]?.count ?: 0) > (beforeDetach[it]?.count ?: Int.MAX_VALUE)
        })
        File(directory, "$layout-result.json").writeText(result.toString(2))
        assertNotNull(finalEvent)
        if (!denySources) assertFalse("Recorder failure: $finalEvent", finalEvent!!.hasError())
        val retriever = MediaMetadataRetriever()
        val verifyMarkers = InstrumentationRegistry.getArguments().getString("provenanceMarkers") == "true"
        val markerFrames = JSONArray()
        val rearSamples = mutableListOf<IntArray>()
        val frontSamples = mutableListOf<IntArray>()
        try {
            retriever.setDataSource(file.absolutePath)
            result.put("durationMs", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                .put("rotation", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
            for (time in listOf(500_000L, 2_000_000L, 5_000_000L)) {
                val bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
                assertNotNull("Missing decoded frame at $time", bitmap)
                File(directory, "$layout-$time.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                result.put("decodedSize", "${bitmap!!.width}x${bitmap.height}")
                if (verifyMarkers) {
                    val rearRegion = if (layout.startsWith("pip")) floatArrayOf(.02f, .42f, .98f, .96f)
                        else floatArrayOf(.02f, .03f, .98f, .47f)
                    val frontRegion = if (layout.startsWith("pip")) floatArrayOf(.65f, .05f, .95f, .35f)
                        else floatArrayOf(.02f, .53f, .98f, .97f)
                    val rearPixels = sample(bitmap, rearRegion).also { rearSamples.add(it) }
                    val frontPixels = sample(bitmap, frontRegion).also { frontSamples.add(it) }
                    markerFrames.put(JSONObject().put("timeUs", time)
                        .put("rearRedFraction", fraction(rearPixels, red = true))
                        .put("frontBlueFraction", fraction(frontPixels, red = false)))
                }
                bitmap.recycle()
            }
        } finally { retriever.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            result.put("tracks", JSONArray(tracks))
            assertEquals(1, tracks.count { it?.startsWith("video/") == true })
            assertEquals(0, tracks.count { it?.startsWith("audio/") == true })
        } finally { extractor.release() }
        if (verifyMarkers) {
            result.put("markerFrames", markerFrames)
                .put("rearMotionDifference", motion(rearSamples)).put("frontMotionDifference", motion(frontSamples))
        }
        File(directory, "$layout-result.json").writeText(result.toString(2))
        if (verifyMarkers) {
            assertTrue("Distinct input colors absent: $result", (0 until markerFrames.length()).all {
                markerFrames.getJSONObject(it).getDouble("rearRedFraction") > .55 &&
                    markerFrames.getJSONObject(it).getDouble("frontBlueFraction") > .55
            })
            assertTrue("Rear region must change across decoded timestamps", motion(rearSamples) > 2.5)
            assertTrue("Front region must change independently across decoded timestamps", motion(frontSamples) > 2.5)
        }
        return result
    }

    private fun progressJson(values: Map<String, Progress>) = JSONObject().apply {
        values.forEach { (id, value) -> put(id, JSONObject().put("frame", value.frame).put("count", value.count).put("receivedMs", value.receivedMs)) }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun sample(bitmap: Bitmap, box: FloatArray): IntArray = IntArray(32 * 24) { i ->
        val x = ((box[0] + (box[2] - box[0]) * ((i % 32 + .5f) / 32)) * bitmap.width).toInt()
        val y = ((box[1] + (box[3] - box[1]) * ((i / 32 + .5f) / 24)) * bitmap.height).toInt()
        bitmap.getPixel(x, y)
    }

    private fun fraction(pixels: IntArray, red: Boolean): Double = pixels.count {
        val r = (it shr 16) and 255; val g = (it shr 8) and 255; val b = it and 255
        if (red) r > 80 && r > b * 1.6 && r > g * 1.5
        else b > 80 && b > r * 1.6 && b > g * 1.5
    }.toDouble() / pixels.size

    private fun motion(samples: List<IntArray>): Double = samples.zipWithNext().maxOf { (a, b) ->
        a.indices.sumOf { i -> listOf(0, 8, 16).sumOf { shift ->
            kotlin.math.abs(((a[i] shr shift) and 255) - ((b[i] shr shift) and 255))
        } }.toDouble() / (a.size * 3)
    }
}
