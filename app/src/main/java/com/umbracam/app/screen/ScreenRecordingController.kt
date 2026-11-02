package com.umbracam.app.screen

import android.app.Activity
import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaMetadataRetriever
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.umbracam.app.UmbraCamApplication
import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

enum class ScreenIssue { CONSENT, CAMERA_PERMISSION, CAMERA_BUSY, FRONT_CAMERA, MICROPHONE_PERMISSION, NOTIFICATIONS, UNSUPPORTED, STORAGE, CAPTURE, REVOKED, SIZE_CHANGED, FINALIZATION, PREVIOUS, METADATA, THERMAL }
data class ScreenStudioState(val initializing: Boolean = true, val awaitingConsent: Boolean = false,
    val serviceRunning: Boolean = false, val elapsedMillis: Long = 0, val issue: ScreenIssue? = null)

/** Main-thread owner. Consent data is in memory only, consumed once after foreground promotion. */
class ScreenRecordingController(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val thermals = (app as UmbraCamApplication).captureThermals
    val thermalStatus = thermals.status
    private val io = Executors.newSingleThreadExecutor { Thread(it, "UmbraCam-screen-storage") }.asCoroutineDispatcher()
    private val policy = ScreenSessionCoordinator { SystemClock.elapsedRealtime() }
    val session = policy.session
    private val mutableUi = MutableStateFlow(ScreenStudioState())
    val ui = mutableUi.asStateFlow()
    val isBusy: Boolean get() = policy.isBusy || flight != null || ui.value.serviceRunning
    private val files = RecordingFileStore(File(app.noBackupFilesDir, "screen"), AndroidVideoInspector())
    private val journal = ScreenSessionJournal(File(app.noBackupFilesDir, "screen/sessions"))
    private val manager = app.getSystemService(MediaProjectionManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var flight: ScreenConsentRequest? = null
    /** Request policy is ephemeral and correlated only with the current chooser result. */
    private var consentRequest: ReactionRecordingRequest? = null
    private var activeRequest: ReactionRecordingRequest? = null
    private var consentCamera: FrontCameraConfiguration? = null
    private var activeCamera: FrontCameraConfiguration? = null
    private var grant: Intent? = null
    private var token: ScreenCaptureAttempt? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var encoder: ScreenEncoder? = null
    private var compositor: ScreenCameraCompositor? = null
    private var output: File? = null
    private var size: ScreenOutputSize? = null
    private var codecName: String? = null
    private var sourceRotation = 0
    private var sourceBounds = 0 to 0
    private var stopping = false
    private var recordingStartedAt = 0L
    private var lastCameraFrameAt = 0L
    private var tick: Job? = null
    private var deadline: Job? = null
    private val displayManager = app.getSystemService(DisplayManager::class.java)
    private var displayListener: DisplayManager.DisplayListener? = null

    init {
        scope.launch { thermals.status.collect { status ->
            if (CaptureThermalPolicy.mustStop(status) && policy.isBusy)
                token?.let { interrupt(it, RecordingErrorCode.THERMAL_LIMIT, ScreenIssue.THERMAL) }
        } }
        scope.launch {
        try {
            val previous = withContext(io) { journal.reconcile(files) }
            previous?.let { policy.restoreInterrupted(it) }
            if (previous != null || journal.warning) mutableUi.update { it.copy(issue = ScreenIssue.PREVIOUS) }
        } catch (_: Exception) { mutableUi.update { it.copy(issue = ScreenIssue.METADATA) } }
        finally { mutableUi.update { it.copy(initializing = false) } }
    } }

    @Suppress("DEPRECATION")
    fun requestConsent(recordingRequest: ReactionRecordingRequest = ReactionRecordingRequest(ReactionMode.SCREEN_ONLY)): Intent? {
        if (isBusy || ui.value.initializing || (app as UmbraCamApplication).libraryOperation) return null
        val capability = ReactionCapabilityDetector(app).read()
        if (recordingRequest.validate(capability).isFailure) {
            mutableUi.update { it.copy(issue = ScreenIssue.UNSUPPORTED) }; return null
        }
        if (recordingRequest.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA &&
            app.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableUi.update { it.copy(issue = ScreenIssue.CAMERA_PERMISSION) }; return null
        }
        val selectedCamera = if (recordingRequest.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA)
            runCatching { FrontCameraConfigurationReader.read(app) }.getOrNull() else null
        if (recordingRequest.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA && selectedCamera == null) {
            mutableUi.update { it.copy(issue = ScreenIssue.FRONT_CAMERA) }; return null
        }
        if (recordingRequest.microphone && app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableUi.update { it.copy(issue = ScreenIssue.MICROPHONE_PERMISSION) }; return null
        }
        if (!CaptureThermalPolicy.canStart(thermals.refresh())) {
            mutableUi.update { it.copy(issue = ScreenIssue.THERMAL) }; return null
        }
        if ((app as UmbraCamApplication).recording.session.value?.state in SingleCameraSessionCoordinator.busyStates) {
            mutableUi.update { it.copy(issue = ScreenIssue.CAMERA_BUSY) }; return null
        }
        if (!ScreenRecordingService.notificationsAllowed(app)) {
            mutableUi.update { it.copy(issue = ScreenIssue.NOTIFICATIONS) }; return null
        }
        return try {
            val bounds = readBounds()
            val supported = listOf(1280, 960, 720, 480).mapNotNull {
                runCatching { ScreenOutputSize.fromSource(bounds.first, bounds.second, it) }.getOrNull()
            }.distinct().firstNotNullOfOrNull { candidate ->
                ScreenEncoder.codecFor(candidate)?.let { candidate to it }
            } ?: error("No supported screen encoder")
            val (candidate, codec) = supported
            sourceRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation; sourceBounds = bounds
            val chooserIntent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
            size = candidate; codecName = codec
            flight = policy.requestConsent() ?: return null
            consentRequest = recordingRequest
            consentCamera = selectedCamera
            mutableUi.update { it.copy(awaitingConsent = true, issue = null, elapsedMillis = 0) }
            chooserIntent
        } catch (error: Exception) {
            android.util.Log.w("UmbraCamScreen", "Screen capture format preflight failed", error)
            mutableUi.update { it.copy(issue = ScreenIssue.UNSUPPORTED) }; null
        }
    }

    fun consentResult(resultCode: Int, data: Intent?, visible: Boolean) {
        val request = flight ?: return
        flight = null
        val captureRequest = consentRequest
        consentRequest = null
        val selectedCamera = consentCamera
        consentCamera = null
        mutableUi.update { it.copy(awaitingConsent = false) }
        if (resultCode != Activity.RESULT_OK || data == null || !visible) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.CONSENT) }; return
        }
        if (captureRequest == null || captureRequest.validate(ReactionCapabilityDetector(app).read()).isFailure) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.UNSUPPORTED) }; return
        }
        if (captureRequest.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA &&
            app.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.CAMERA_PERMISSION) }; return
        }
        if (captureRequest.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA &&
            (selectedCamera == null || runCatching { FrontCameraConfigurationReader.read(app)?.id }.getOrNull() != selectedCamera.id)) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.FRONT_CAMERA) }; return
        }
        if (captureRequest.microphone && app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.MICROPHONE_PERMISSION) }; return
        }
        if (!CaptureThermalPolicy.canStart(thermals.refresh())) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.THERMAL) }; return
        }
        if (!ScreenRecordingService.notificationsAllowed(app) || (app as UmbraCamApplication).recording.session.value?.state in SingleCameraSessionCoordinator.busyStates) {
            policy.cancelConsent(request); mutableUi.update { it.copy(issue = ScreenIssue.CAMERA_BUSY) }; return
        }
        val attempt = policy.consented(request, UUID.randomUUID().toString(), UUID.randomUUID().toString(), captureRequest.sources()) ?: return
        token = attempt; grant = data; activeRequest = captureRequest; activeCamera = selectedCamera; stopping = false
        recordingStartedAt = 0L
        lastCameraFrameAt = 0L
        try { app.startForegroundService(Intent(app, ScreenRecordingService::class.java)
            .setAction(ScreenRecordingService.START).putExtra(ScreenRecordingService.ATTEMPT, attempt.id)) }
        catch (_: Exception) { grant = null; activeRequest = null; activeCamera = null; policy.failed(attempt, RecordingErrorCode.PERMISSION_DENIED)
            mutableUi.update { it.copy(issue = ScreenIssue.CAPTURE) } }
    }

    fun cancelConsent() { consentResult(Activity.RESULT_CANCELED, null, false) }
    fun cameraPermissionDenied() { mutableUi.update { it.copy(issue = ScreenIssue.CAMERA_PERMISSION) } }
    fun notificationDenied() { mutableUi.update { it.copy(issue = ScreenIssue.NOTIFICATIONS) } }
    @Suppress("DEPRECATION")
    private fun readBounds(): Pair<Int, Int> {
        val wm = app.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        else DisplayMetrics().let { wm.defaultDisplay.getRealMetrics(it); it.widthPixels to it.heightPixels }
    }
    fun recordingDeleted(storageId: String) {
        if (policy.isBusy) return
        policy.recordingDeleted(storageId)
        session.value?.let { snapshot -> scope.launch { withContext(io) { runCatching { journal.write(snapshot) } } } }
    }
    fun pendingAttempt(): String? = token?.id?.takeIf {
        grant != null && session.value?.state in SingleCameraSessionCoordinator.busyStates &&
            activeRequest?.validate(ReactionCapabilityDetector(app).read())?.isSuccess == true &&
            (activeRequest?.mode != ReactionMode.SCREEN_AND_FRONT_CAMERA || activeCamera != null)
    }
    fun cameraRequired(attemptId: String): Boolean = token?.id == attemptId &&
        activeRequest?.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA
    fun serviceAccepted(attemptId: String) {
        val attempt = token?.takeIf { it.id == attemptId } ?: return
        mutableUi.update { it.copy(serviceRunning = true) }
        if (session.value?.state != RecordingState.PREPARING || stopping ||
            activeRequest?.validate(ReactionCapabilityDetector(app).read())?.isSuccess != true ||
            (activeRequest?.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA && activeCamera == null)) {
            grant = null; activeRequest = null; activeCamera = null; policy.failed(attempt, RecordingErrorCode.UNKNOWN); return
        }
        tick?.cancel()
        tick = scope.launch { while (isActive && session.value?.state in SingleCameraSessionCoordinator.busyStates) {
            policy.tick(attempt)
            if (session.value?.state == RecordingState.RECORDING && recordingStartedAt != 0L)
                mutableUi.update { it.copy(elapsedMillis = SystemClock.elapsedRealtime() - recordingStartedAt) }
            if (session.value?.state == RecordingState.RECORDING && activeRequest?.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA &&
                (lastCameraFrameAt == 0L || SystemClock.elapsedRealtime() - lastCameraFrameAt > CAMERA_FRAME_TIMEOUT_MILLIS))
                interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.FRONT_CAMERA)
            if (session.value?.state == RecordingState.INTERRUPTED && !stopping) stop()
            if (!ScreenRecordingService.notificationsAllowed(app) && !stopping) interrupt(attempt, RecordingErrorCode.PERMISSION_DENIED, ScreenIssue.NOTIFICATIONS)
            delay(250)
        } }
        scope.launch {
            try {
                val file = withContext(io) { files.reserve().also { output = it; journal.write(session.value!!) } }
                if (token !== attempt || session.value?.state != RecordingState.PREPARING || stopping) {
                    withContext(io) { files.quarantine(file) }; grant = null; policy.failed(attempt, RecordingErrorCode.UNKNOWN); return@launch
                }
                val result = checkNotNull(grant); grant = null
                val mp = checkNotNull(manager.getMediaProjection(Activity.RESULT_OK, result)); projection = mp
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { if (token === attempt && !stopping) interrupt(attempt, RecordingErrorCode.CONSENT_REVOKED, ScreenIssue.REVOKED) }
                    override fun onCapturedContentResize(width: Int, height: Int) {
                        if (token === attempt && !stopping && (width to height) != sourceBounds)
                            interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.SIZE_CHANGED)
                    }
                }, main)
                val listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(id: Int) = Unit
                    override fun onDisplayRemoved(id: Int) { if (id == Display.DEFAULT_DISPLAY) interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.SIZE_CHANGED) }
                    override fun onDisplayChanged(id: Int) {
                        if (id == Display.DEFAULT_DISPLAY && (displayManager.getDisplay(id)?.rotation != sourceRotation || readBounds() != sourceBounds))
                            interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.SIZE_CHANGED)
                    }
                }
                displayListener = listener; displayManager.registerDisplayListener(listener, main)
                val frozen = checkNotNull(size)
                encoder = ScreenEncoder(file, frozen, checkNotNull(codecName), { surface ->
                    if (token !== attempt || stopping) { encoder?.stop() }
                    else if (activeRequest?.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA) {
                        try {
                            val source = checkNotNull(activeCamera)
                            compositor = ScreenCameraCompositor(app, mp, sourceBounds.first, sourceBounds.second,
                                app.resources.configuration.densityDpi, frozen, checkNotNull(activeRequest).layout,
                                source, surface, { if (token === attempt && !stopping) policy.projectionReady(attempt) },
                                { at -> if (token === attempt && !stopping) lastCameraFrameAt = at },
                                { error -> if (token === attempt && !stopping) interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.FRONT_CAMERA) },
                            ).also { it.start() }
                        } catch (_: Exception) { interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.FRONT_CAMERA) }
                    } else try {
                        display = mp.createVirtualDisplay("UmbraCam screen", frozen.width, frozen.height, app.resources.configuration.densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, main)
                        checkNotNull(display); policy.projectionReady(attempt)
                    } catch (_: Exception) { interrupt(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.CAPTURE) }
                }, { pts, bytes ->
                    if (token === attempt && !stopping) {
                        val sources = if (activeRequest?.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA && lastCameraFrameAt > 0L &&
                            SystemClock.elapsedRealtime() - lastCameraFrameAt <= CAMERA_FRAME_TIMEOUT_MILLIS)
                            setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA) else setOf(CaptureSource.SCREEN)
                        policy.sampleWritten(attempt, pts, bytes, sources)
                        if (session.value?.state == RecordingState.RECORDING && recordingStartedAt == 0L)
                            recordingStartedAt = SystemClock.elapsedRealtime()
                    }
                }, { error -> finished(attempt, file, frozen, error) }).also { it.start() }
            } catch (error: Exception) {
                grant = null; activeRequest = null; activeCamera = null
                mutableUi.update { it.copy(issue = if (error is InsufficientRecordingSpace) ScreenIssue.STORAGE else ScreenIssue.CAPTURE) }
                releaseProjection()
                val file = output
                if (file != null) withContext(io) { runCatching { files.quarantine(file) } }
                policy.failed(attempt, if (error is InsufficientRecordingSpace) RecordingErrorCode.STORAGE_FULL else RecordingErrorCode.ENCODER_FAILED)
            }
        }
    }

    private fun interrupt(attempt: ScreenCaptureAttempt, code: RecordingErrorCode, issue: ScreenIssue) {
        if (token !== attempt || stopping) return
        policy.interrupted(attempt, code); mutableUi.update { it.copy(issue = issue) }; stop()
    }
    fun stop() {
        if (stopping) return
        val attempt = token ?: return
        if (session.value?.state !in SingleCameraSessionCoordinator.busyStates) return
        stopping = true; policy.stop(attempt)
        val activeCompositor = compositor
        if (activeCompositor != null) activeCompositor.stop { if (compositor === activeCompositor) compositor = null; encoder?.stop() }
        else { display?.release(); display = null; encoder?.stop() }
        deadline?.cancel()
        deadline = scope.launch { delay(20_000); if (token === attempt && session.value?.state == RecordingState.STOPPING) encoder?.abort() }
    }

    private fun finished(attempt: ScreenCaptureAttempt, file: File, frozen: ScreenOutputSize, error: Throwable?) {
        if (token !== attempt) return
        if (error != null && !stopping) policy.interrupted(attempt, RecordingErrorCode.ENCODER_FAILED)
        stopping = true; policy.stop(attempt); releaseProjection(); encoder = null
        deadline?.cancel()
        if (!policy.finalizeReceived(attempt)) return
        scope.launch {
            try {
                val published = withContext(io) {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(file.path)
                        check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() == frozen.width)
                        check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() == frozen.height)
                        check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) == "0")
                        check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes")
                    } finally { metadata.release() }
                    files.publish(file)
                }
                val warning = if (error != null) RecordingError(RecordingErrorCode.ENCODER_FAILED, false) else null
                policy.saved(attempt, published.segment, warning)
                try { withContext(io) { journal.write(session.value!!) } }
                catch (_: Exception) { mutableUi.update { it.copy(issue = ScreenIssue.METADATA) } }
            } catch (_: Exception) {
                withContext(io) { runCatching { files.quarantine(file) } }
                policy.publicationFailed(attempt); mutableUi.update { it.copy(issue = ScreenIssue.FINALIZATION) }
                withContext(io) { runCatching { journal.write(session.value!!) } }
            } finally { output = null; activeRequest = null; activeCamera = null; compositor = null }
        }
    }
    private fun releaseProjection() {
        displayListener?.let(displayManager::unregisterDisplayListener); displayListener = null
        display?.release(); display = null
        compositor?.stop(); compositor = null
        val old = projection; projection = null; old?.stop()
    }
    fun serviceEnded(attemptId: String) {
        if (token?.id != attemptId) return
        mutableUi.update { it.copy(serviceRunning = false) }
        if (session.value?.state in SingleCameraSessionCoordinator.busyStates && !stopping)
            token?.let { interrupt(it, RecordingErrorCode.SOURCE_UNAVAILABLE, ScreenIssue.CAPTURE) }
    }
    fun serviceFailed(attemptId: String) {
        if (token?.id != attemptId) return
        grant = null; activeRequest = null; activeCamera = null; consentCamera = null
        token?.let { policy.failed(it, RecordingErrorCode.PERMISSION_DENIED) }
        mutableUi.update { it.copy(serviceRunning = false, issue = ScreenIssue.CAPTURE) }
    }

    companion object { private const val CAMERA_FRAME_TIMEOUT_MILLIS = 1_200L }
}
