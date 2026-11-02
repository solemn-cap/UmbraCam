package com.umbracam.app

import android.Manifest
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CameraManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
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

/** Emulator-only causal provenance: modulate ONE input through its CameraX control, then
 * decode the file and prove only that region darkens/returns. No extra camera stream,
 * raw capture requests, platform metadata override or product controls are introduced.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
@RunWith(AndroidJUnit4::class)
class DualCameraProvenanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private data class Sample(val frame: Long, val received: Long, val exposure: Long?, val ae: Int?)

    @Test fun eachCameraHasIndependentChangingContentInBothLayouts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dualProvenance") == "true")
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT >= 30)
        val context = compose.activity
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val manager = context.getSystemService(CameraManager::class.java)
        val provider = ProcessCameraProvider.getInstance(context).get(30, TimeUnit.SECONDS)
        val pairs = provider.availableConcurrentCameraInfos.map { infos -> infos.map { Camera2CameraInfo.from(it).cameraId }.toSet() }
        val pair = pairs.firstOrNull { ids -> ids.size == 2 && ids in manager.concurrentCameraIds &&
            ids.map { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) }.toSet() ==
            setOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK) }
        assertNotNull("Exact independent front/rear advertisement required", pair)
        val directory = File(context.filesDir, "phase4-provenance/${System.currentTimeMillis()}").apply { mkdirs() }
        val cases = JSONArray()
        val summary = JSONObject().put("fingerprint", Build.FINGERPRINT).put("pair", JSONArray(pair!!.sorted()))
            .put("method", "per-camera manual exposure modulation; no replacement input feeds").put("cases", cases)
        try {
            for (layout in listOf("split", "pip")) for (primary in pair.sorted()) {
                cases.put(recordCase(provider, manager, directory, primary, (pair - primary).single(), layout))
                File(directory, "summary.json").writeText(summary.toString(2))
            }
        } finally {
            instrumentation.runOnMainSync { provider.unbindAll() }
            File(directory, "summary.json").writeText(summary.toString(2))
            Log.i("UmbraCamProvenance", "directory=${directory.absolutePath} summary=$summary")
        }
    }

    private fun recordCase(provider: ProcessCameraProvider, manager: CameraManager, directory: File,
        primary: String, secondary: String, layout: String): JSONObject {
        val name = "$layout-primary-$primary"
        val file = File(directory, "$name.mp4")
        val result = JSONObject().put("layout", layout).put("primary", primary).put("secondary", secondary)
        val characteristics = manager.getCameraCharacteristics(primary)
        assertTrue("Manual sensor capability required", characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
            .contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR))
        val exposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)!!.lower
        val sensitivity = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)!!.lower
        val secondaryCharacteristics = manager.getCameraCharacteristics(secondary)
        assertTrue(secondaryCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
            .contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR))
        val secondaryExposure = secondaryCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)!!.lower
        val secondarySensitivity = secondaryCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)!!.lower
        result.put("manualExposureNs", exposure).put("manualSensitivity", sensitivity)
        val samples = ConcurrentHashMap<String, Sample>()
        val durationUs = AtomicLong()
        val bytes = AtomicLong()
        val started = CountDownLatch(1)
        val finalized = CountDownLatch(1)
        var finalEvent: VideoRecordEvent.Finalize? = null
        var recording: Recording? = null
        var camera: Camera? = null
        var control: Camera2CameraControl? = null
        var secondaryControl: Camera2CameraControl? = null
        var preview: Preview? = null
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val times = mutableListOf<Long>()
        try {
            instrumentation.runOnMainSync {
                compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                owner.registry.currentState = Lifecycle.State.RESUMED
                val view = PreviewView(compose.activity).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
                compose.activity.setContentView(view)
                val builder = Preview.Builder().setTargetRotation(Surface.ROTATION_0)
                Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, capture: TotalCaptureResult) {
                        samples[session.device.id] = Sample(capture.frameNumber, SystemClock.elapsedRealtime(),
                            capture.get(CaptureResult.SENSOR_EXPOSURE_TIME), capture.get(CaptureResult.CONTROL_AE_MODE))
                    }
                })
                preview = builder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
                val video = VideoCapture.Builder(recorder).setTargetRotation(Surface.ROTATION_0).build()
                val group = UseCaseGroup.Builder().addUseCase(preview!!).addUseCase(video).build()
                fun selector(id: String) = CameraSelector.Builder().requireLensFacing(
                    if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                ).addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }.build()
                val first = if (layout == "pip") CompositionSettings.DEFAULT else
                    CompositionSettings.Builder().setAlpha(1f).setScale(1f, .5f).setOffset(0f, .5f).build()
                val second = if (layout == "pip") CompositionSettings.Builder().setAlpha(1f).setScale(.32f, .32f).setOffset(.6f, .6f).build()
                    else CompositionSettings.Builder().setAlpha(1f).setScale(1f, .5f).setOffset(0f, -.5f).build()
                // Composition exposes only its primary wrapper. Resolve the other public
                // CameraX control with an unserved Preview, then fully unbind before composing.
                // Apply options only after that exact internal camera is live in the pair;
                // per-device result metadata below must prove the command reached it.
                val secondaryCamera = provider.bindToLifecycle(owner, selector(secondary), Preview.Builder().build())
                assertEquals(secondary, Camera2CameraInfo.from(secondaryCamera.cameraInfo).cameraId)
                secondaryControl = Camera2CameraControl.from(secondaryCamera.cameraControl)
                provider.unbindAll()
                camera = provider.bindToLifecycle(listOf(SingleCameraConfig(selector(primary), group, first, owner),
                    SingleCameraConfig(selector(secondary), group, second, owner))).cameras.single()
                assertTrue(provider.isConcurrentCameraModeOn)
                assertEquals(primary, Camera2CameraInfo.from(camera!!.cameraInfo).cameraId)
                recording = recorder.prepareRecording(compose.activity, FileOutputOptions.Builder(file).build())
                    .start(ContextCompat.getMainExecutor(compose.activity)) { event ->
                        durationUs.set(event.recordingStats.recordedDurationNanos / 1_000)
                        bytes.set(event.recordingStats.numBytesRecorded)
                        if (event is VideoRecordEvent.Start) started.countDown()
                        if (event is VideoRecordEvent.Finalize) { finalEvent = event; finalized.countDown() }
                    }
            }
            assertTrue(started.await(20, TimeUnit.SECONDS))
            await("both inputs and encoded output") { bytes.get() > 0 && listOf(primary, secondary).all {
                samples[it]?.let { value -> value.frame >= 2 && SystemClock.elapsedRealtime() - value.received < 1_500 } == true } }
            fun probe() { SystemClock.sleep(1_000); times.add(durationUs.get()); SystemClock.sleep(600) }
            probe(); probe()
            result.put("normalMetadata", metadata(samples))
            control = Camera2CameraControl.from(camera!!.cameraControl)
            control!!.setCaptureRequestOptions(CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, sensitivity).build()).get(10, TimeUnit.SECONDS)
            await("primary manual exposure result") { samples[primary]?.let {
                it.ae == CaptureRequest.CONTROL_AE_MODE_OFF && it.exposure == exposure } == true }
            probe()
            result.put("darkMetadata", metadata(samples))
            assertEquals("Secondary must retain independent AE", CaptureRequest.CONTROL_AE_MODE_ON, samples[secondary]?.ae)
            control!!.clearCaptureRequestOptions().get(10, TimeUnit.SECONDS)
            await("primary automatic exposure restored") { samples[primary]?.ae == CaptureRequest.CONTROL_AE_MODE_ON }
            probe()
            result.put("restoredMetadata", metadata(samples))
            secondaryControl!!.setCaptureRequestOptions(CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, secondaryExposure)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, secondarySensitivity).build()).get(10, TimeUnit.SECONDS)
            await("secondary manual exposure result") { samples[secondary]?.let {
                it.ae == CaptureRequest.CONTROL_AE_MODE_OFF && it.exposure == secondaryExposure } == true }
            probe()
            result.put("secondaryDarkMetadata", metadata(samples))
            assertEquals("Primary must retain independent AE", CaptureRequest.CONTROL_AE_MODE_ON, samples[primary]?.ae)
            secondaryControl!!.clearCaptureRequestOptions().get(10, TimeUnit.SECONDS)
            await("secondary automatic exposure restored") { samples[secondary]?.ae == CaptureRequest.CONTROL_AE_MODE_ON }
            probe()
            result.put("bothRestoredMetadata", metadata(samples))
        } finally {
            runCatching { control?.clearCaptureRequestOptions()?.get(5, TimeUnit.SECONDS) }
                .onFailure { result.put("controlCleanupError", it.toString()) }
            runCatching { secondaryControl?.clearCaptureRequestOptions()?.get(5, TimeUnit.SECONDS) }
                .onFailure { result.put("secondaryControlCleanupError", it.toString()) }
            try {
                instrumentation.runOnMainSync { recording?.stop() }
                if (recording != null) assertTrue("Finalize timed out", finalized.await(30, TimeUnit.SECONDS))
            } finally {
                instrumentation.runOnMainSync { provider.unbindAll(); preview?.setSurfaceProvider(null); owner.registry.currentState = Lifecycle.State.DESTROYED }
                result.put("finalizeError", finalEvent?.error).put("bytes", file.length()).put("sampleTimesUs", JSONArray(times))
                File(directory, "$name.json").writeText(result.toString(2))
            }
        }
        assertFalse("Recorder must finalize cleanly", finalEvent!!.hasError())
        val primaryPixels = mutableListOf<IntArray>()
        val secondaryPixels = mutableListOf<IntArray>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            for ((index, time) in times.withIndex()) {
                val frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)!!
                File(directory, "$name-$index.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                primaryPixels.add(sample(frame, if (layout == "pip") floatArrayOf(.02f, .42f, .98f, .96f) else floatArrayOf(.02f, .03f, .98f, .47f)))
                secondaryPixels.add(sample(frame, if (layout == "pip") floatArrayOf(.65f, .05f, .95f, .35f) else floatArrayOf(.02f, .53f, .98f, .97f)))
                frame.recycle()
            }
        } finally { retriever.release() }
        val p = primaryPixels.map(::luma); val s = secondaryPixels.map(::luma)
        result.put("primaryLuma", JSONArray(p)).put("secondaryLuma", JSONArray(s))
            .put("ambientPrimaryDifference", difference(primaryPixels[0], primaryPixels[1]))
            .put("ambientSecondaryDifference", difference(secondaryPixels[0], secondaryPixels[1]))
        File(directory, "$name.json").writeText(result.toString(2))
        assertTrue("Only controlled primary should darken: $result", p[2] < (p[0] + p[1]) / 2 * .4 && p[3] > p[2] + 25)
        assertTrue("Independent secondary must stay visible: $result", s[2] > (s[0] + s[1]) / 2 * .55)
        assertTrue("Secondary must independently darken and recover: $result", s[4] < s[3] * .4 && s[5] > s[4] + 25)
        assertTrue("Primary must stay visible during secondary modulation: $result", p[4] > p[3] * .55)
        return result
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out: $label", condition())
    }
    private fun metadata(samples: Map<String, Sample>) = JSONObject().apply { samples.forEach { (id, value) ->
        put(id, JSONObject().put("frame", value.frame).put("receiptMs", value.received).put("exposureNs", value.exposure).put("ae", value.ae)) } }
    private fun sample(frame: Bitmap, box: FloatArray) = IntArray(32 * 24) { i -> frame.getPixel(
        ((box[0] + (box[2] - box[0]) * ((i % 32 + .5f) / 32)) * frame.width).toInt(),
        ((box[1] + (box[3] - box[1]) * ((i / 32 + .5f) / 24)) * frame.height).toInt()) }
    private fun luma(pixels: IntArray) = pixels.sumOf { .2126 * ((it shr 16) and 255) + .7152 * ((it shr 8) and 255) + .0722 * (it and 255) } / pixels.size
    private fun difference(a: IntArray, b: IntArray) = a.indices.sumOf { i -> listOf(0, 8, 16).sumOf { shift ->
        kotlin.math.abs(((a[i] shr shift) and 255) - ((b[i] shr shift) and 255)) } }.toDouble() / (a.size * 3)
}
