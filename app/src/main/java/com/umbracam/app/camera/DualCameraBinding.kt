package com.umbracam.app.camera

import android.content.Context
import android.hardware.camera2.*
import android.os.Build
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.lifecycle.*
import com.umbracam.core.recording.*

/** One immutable pair/layout binding. The process controller is the only provider owner. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class DualCameraBinding(
    private val context: Context,
    private val provider: ProcessCameraProvider,
    val specification: DualRecordingSpec,
    private val availability: (String, Boolean) -> Unit,
    private val progress: (String, Long) -> Unit,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
    private var live = true
    private val openIds = mutableSetOf<String>()
    private val failedIds = mutableSetOf<String>()
    val ready get() = openIds.containsAll(setOf(specification.pair.front.id, specification.pair.rear.id))
    lateinit var preview: Preview
        private set
    lateinit var capture: VideoCapture<Recorder>
        private set

    fun bind(surfaceProvider: Preview.SurfaceProvider?, resumed: Boolean) {
        check(Build.VERSION.SDK_INT >= 30)
        require(specification.isSupportedByInitialBackend(Build.VERSION.SDK_INT)) {
            "Only validated portrait output is enabled in the initial backend"
        }
        val report = ConcurrentCameraDetector(AndroidCameraInventorySource(context)).read()
        val groups = provider.availableConcurrentCameraInfos.map { infos -> infos.map { Camera2CameraInfo.from(it).cameraId }.toSet() }.toSet()
        require(specification.pair.isAdvertisedBy(report, groups)) { "Selected pair is not currently advertised" }
        fun selector(id: String, facing: Int) = CameraSelector.Builder().requireLensFacing(facing)
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }.build()
        val rear = selector(specification.pair.rear.id, CameraSelector.LENS_FACING_BACK)
        val front = selector(specification.pair.front.id, CameraSelector.LENS_FACING_FRONT)
        for (selected in listOf(rear, front)) {
            require(Quality.HD in Recorder.getVideoCapabilities(provider.getCameraInfo(selected)).getSupportedQualities(DynamicRange.SDR))
        }
        registry.currentState = if (resumed) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        val builder = Preview.Builder().setTargetRotation(Surface.ROTATION_0).setTargetFrameRate(Range(30, 30))
        fun deliver(block: () -> Unit) { context.mainExecutor.execute { if (live) block() } }
        fun opened(id: String) { failedIds.remove(id); openIds.add(id); availability(id, true) }
        fun lost(id: String) { failedIds.add(id); openIds.remove(id); availability(id, false) }
        Camera2Interop.Extender(builder).setDeviceStateCallback(object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) = deliver { opened(camera.id) }
            override fun onDisconnected(camera: CameraDevice) = deliver { lost(camera.id) }
            override fun onError(camera: CameraDevice, error: Int) = deliver { lost(camera.id) }
            override fun onClosed(camera: CameraDevice) = deliver { if (camera.id in openIds) lost(camera.id) }
        }).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                val id = session.device.id
                val received = SystemClock.elapsedRealtime()
                deliver {
                    if (SystemClock.elapsedRealtime() - received >= DualCameraSessionCoordinator.PROGRESS_TIMEOUT_MILLIS) { lost(id); return@deliver }
                    if (id in failedIds) return@deliver
                    // A retained Camera2 device may not repeat onOpened for a new binding.
                    // A fresh completed capture is itself evidence of an open usable device.
                    if (id !in openIds) opened(id)
                    progress(id, result.frameNumber)
                }
            }
        })
        preview = builder.build().also { it.setSurfaceProvider(surfaceProvider) }
        capture = VideoCapture.Builder(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
            .setTargetRotation(Surface.ROTATION_0).build()
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).build()
        fun settings(rect: NormalizedRect) = CompositionSettings.Builder().setAlpha(1f)
            .setScale((rect.right - rect.left).toFloat(), (rect.bottom - rect.top).toFloat())
            .setOffset((rect.left + rect.right - 1).toFloat(), (1 - rect.top - rect.bottom).toFloat()).build()
        provider.unbindAll()
        provider.bindToLifecycle(listOf(SingleCameraConfig(rear, group, settings(specification.geometry.rear), this),
            SingleCameraConfig(front, group, settings(specification.geometry.front), this)))
    }
    fun resumed(value: Boolean) { if (live) registry.currentState = if (value) Lifecycle.State.RESUMED else Lifecycle.State.CREATED }
    fun close() {
        if (!live) return
        live = false
        provider.unbindAll()
        if (this::preview.isInitialized) preview.setSurfaceProvider(null)
        registry.currentState = Lifecycle.State.DESTROYED
        openIds.clear()
    }
}
