package com.umbracam.app.camera

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Surface
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.AudioStats
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.umbracam.core.recording.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect

enum class CameraLens(val selector: CameraSelector, val source: CaptureSource) {
    REAR(CameraSelector.DEFAULT_BACK_CAMERA, CaptureSource.REAR_CAMERA),
    FRONT(CameraSelector.DEFAULT_FRONT_CAMERA, CaptureSource.FRONT_CAMERA),
}

enum class CameraIssue { CAMERA_PERMISSION, MICROPHONE_PERMISSION, NOTIFICATION_PERMISSION, NO_CAMERA, CAMERA_UNAVAILABLE, UNSUPPORTED_QUALITY, STORAGE, PREPARE, AUDIO_UNAVAILABLE, FINALIZATION, NO_DATA, INTERRUPTED, SAVED_PARTIAL, PREVIOUS_SESSION, METADATA, RECOVERY_SETTINGS, THERMAL }

data class CameraStudioState(
    val initializing: Boolean = true,
    val serviceRunning: Boolean = false,
    val loading: Boolean = false,
    val ready: Boolean = false,
    val lenses: List<CameraLens> = emptyList(),
    val lens: CameraLens = CameraLens.REAR,
    val qualities: List<Quality> = emptyList(),
    val quality: Quality? = null,
    val audioEnabled: Boolean = false,
    val durationMillis: Long = 0,
    val issue: CameraIssue? = null,
    val dualSpecification: DualRecordingSpec? = null,
    val diagnostics: List<RecordingErrorCode> = emptyList(),
)

/** Application-scoped CameraX owner. Only an accepted foreground service keeps capture alive off-screen. */
@MainThread
class CameraRecordingController(application: Application) {
    private val app = application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val thermals = (app as com.umbracam.app.UmbraCamApplication).captureThermals
    val thermalStatus = thermals.status
    private val coordinator = CameraSessionCoordinator { android.os.SystemClock.elapsedRealtime() }
    val session = coordinator.session
    private val mutableUi = MutableStateFlow(CameraStudioState())
    val ui = mutableUi.asStateFlow()
    private val files = RecordingFileStore(File(app.noBackupFilesDir, "camera"), AndroidVideoInspector(),
        vaultCodec = (app as com.umbracam.app.UmbraCamApplication).vaultCodec)
    private val journal = RecordingSessionJournal(File(app.noBackupFilesDir, "camera/sessions"))
    private val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "UmbraCam-storage") }.asCoroutineDispatcher()
    private val captureLifecycle = object : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }
    private val providerFuture by lazy { ProcessCameraProvider.getInstance(app) }
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var previewView: WeakReference<PreviewView>? = null
    private var boundOwner: LifecycleOwner? = null
    private var boundView: WeakReference<PreviewView>? = null
    private var preview: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var boundLens: CameraLens? = null
    private var cameraObserver: Observer<CameraState>? = null
    private var recording: Recording? = null
    private var generation = 0L
    private var foregroundUi = false
    private var serviceActive = false
    private var pendingServiceAttempt: String? = null
    private var activeOutput: Pair<String, File>? = null
    private var stopDeadline: Job? = null
    private var dualBinding: DualCameraBinding? = null
    private var healthTicker: Job? = null
    private var reportedDualAttempt: String? = null

    init {
        scope.launch { thermals.status.collect { status ->
            if (CaptureThermalPolicy.mustStop(status) && coordinator.isBusy) interrupt(RecordingErrorCode.THERMAL_LIMIT, CameraIssue.THERMAL)
            else if (!CaptureThermalPolicy.canStart(status) && !coordinator.isBusy) {
                generation++; unbind(); mutableUi.update { it.copy(issue = CameraIssue.THERMAL) }
            }
        } }
        scope.launch {
            try {
                val result = withContext(io) {
                    val migration = (app as com.umbracam.app.UmbraCamApplication).vaultMigration.ensureMigrated()
                    Triple(migration, journal.reconcile(files), journal.hadUnverifiedOutput)
                }
                val (migration, previous, unverified) = result
                if (migration.failed > 0) mutableUi.update { it.copy(issue = CameraIssue.METADATA) }
                previous?.let { coordinator.restoreInterrupted(it, journal.reconciledDual); mutableUi.update { state -> state.copy(issue = CameraIssue.PREVIOUS_SESSION, diagnostics = journal.reconciledDiagnostics) } }
                if (previous == null && unverified) mutableUi.update { it.copy(issue = CameraIssue.PREVIOUS_SESSION) }
            } catch (_: Exception) {
                mutableUi.update { it.copy(issue = CameraIssue.METADATA) }
            } finally { mutableUi.update { it.copy(initializing = false) } }
        }
    }

    fun hasPermission(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun attach(lifecycleOwner: LifecycleOwner, view: PreviewView) {
        if (owner === lifecycleOwner && previewView?.get() === view) return
        owner = lifecycleOwner
        previewView = WeakReference(view)
        foregroundUi = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
        updateCaptureLifecycle()
        if (coordinator.isBusy) { preview?.setSurfaceProvider(view.surfaceProvider); return }
        bind()
    }

    fun detach(lifecycleOwner: LifecycleOwner) {
        if (owner !== lifecycleOwner) return
        owner = null
        previewView = null
        boundOwner = null
        boundView = null
        generation++
        foregroundUi = false
        onForegroundLost()
        preview?.setSurfaceProvider(null)
        if (!coordinator.isBusy) unbind()
        updateCaptureLifecycle()
    }

    fun onUiResumed(lifecycleOwner: LifecycleOwner) {
        owner = lifecycleOwner
        foregroundUi = true
        permissionsChanged()
        updateCaptureLifecycle()
        if (!coordinator.isBusy) bind()
    }

    private fun updateCaptureLifecycle() {
        captureLifecycle.registry.currentState = if (serviceActive || foregroundUi) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        dualBinding?.resumed(serviceActive || foregroundUi)
    }

    fun cameraPermissionDenied() { mutableUi.update { it.copy(issue = CameraIssue.CAMERA_PERMISSION) } }
    fun notificationPermissionDenied() { mutableUi.update { it.copy(issue = CameraIssue.NOTIFICATION_PERMISSION) } }

    fun setMicrophone(enabled: Boolean) {
        if (coordinator.isBusy || ui.value.serviceRunning) return
        val allowed = !enabled || hasPermission(Manifest.permission.RECORD_AUDIO)
        coordinator.selected?.let { spec ->
            if (allowed) selectDual(spec.copy(microphone = enabled))
            else mutableUi.update { it.copy(issue = CameraIssue.MICROPHONE_PERMISSION) }
            return
        }
        mutableUi.update { it.copy(audioEnabled = enabled && allowed, issue = if (allowed) null else CameraIssue.MICROPHONE_PERMISSION) }
    }

    fun permissionsChanged() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            mutableUi.update { it.copy(ready = false) }
            interrupt(RecordingErrorCode.PERMISSION_DENIED, CameraIssue.CAMERA_PERMISSION)
            if (!coordinator.isBusy) unbind()
        }
        if (ui.value.audioEnabled && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            interrupt(RecordingErrorCode.PERMISSION_DENIED, CameraIssue.MICROPHONE_PERMISSION)
            mutableUi.update { it.copy(audioEnabled = coordinator.selected?.microphone ?: false) }
        }
    }

    fun chooseLens(lens: CameraLens) {
        if (coordinator.selected != null || coordinator.isBusy || lens !in ui.value.lenses || lens == ui.value.lens) return
        mutableUi.update { it.copy(lens = lens, quality = null) }
        bind(force = true)
    }

    fun chooseQuality(quality: Quality) {
        if (coordinator.selected != null || coordinator.isBusy || quality !in ui.value.qualities || quality == ui.value.quality) return
        mutableUi.update { it.copy(quality = quality) }
        bind(force = true)
    }

    fun retryPreview() { if (!coordinator.isBusy && !ui.value.ready) bind(force = true) }

    /** Select the predictable, low-setup single rear-camera path for an explicitly requested SOS start. */
    fun prepareSosMode(): Boolean {
        if (ui.value.initializing || coordinator.isBusy || ui.value.serviceRunning) {
            mutableUi.update { it.copy(issue = CameraIssue.PREPARE) }
            return false
        }
        if (coordinator.selected != null && !coordinator.select(null)) return false
        mutableUi.update { it.copy(dualSpecification = null, lens = CameraLens.REAR, quality = null, ready = false,
            audioEnabled = false, diagnostics = emptyList()) }
        bind(force = true)
        return true
    }

    fun canStartSos(): Boolean = !coordinator.isBusy && coordinator.selected == null && boundLens == CameraLens.REAR &&
        camera != null && ui.value.ready && ui.value.lens == CameraLens.REAR && !ui.value.audioEnabled

    fun startSos() {
        if (!canStartSos()) return
        requestStart(recovery = false, kind = RecordingKind.SOS)
    }

    fun quickStartUnavailable() { mutableUi.update { it.copy(issue = CameraIssue.PREPARE) } }

    /** Explicit mode/settings selection; accepted capture attempts keep their immutable specification. */
    fun selectDual(spec: DualRecordingSpec?): Boolean {
        if (ui.value.initializing || ui.value.serviceRunning || coordinator.isBusy) return false
        if (spec != null && (android.os.Build.VERSION.SDK_INT < 30 || spec.shape != DualOutputShape.PORTRAIT || spec.rotation != DualOutputRotation.DEGREES_0)) return false
        if (!coordinator.select(spec)) return false
        mutableUi.update { it.copy(dualSpecification = spec, audioEnabled = spec?.microphone ?: false, diagnostics = emptyList()) }
        bind(force = true)
        return true
    }

    private fun bindDual(cameraProvider: ProcessCameraProvider, attempt: DualAttemptToken?) {
        val spec = coordinator.selected ?: error("No dual selection")
        unbind()
        lateinit var binding: DualCameraBinding
        binding = DualCameraBinding(app, cameraProvider, spec, { id, available ->
            if (dualBinding === binding) {
                mutableUi.update { it.copy(ready = binding.ready && !it.loading) }
                attempt?.let { coordinator.available(it, id, available); dualOutcome() }
            }
        }, { id, frame ->
            if (dualBinding === binding) attempt?.let { coordinator.progress(it, id, frame); dualOutcome() }
        })
        dualBinding = binding
        boundLens = null
        try {
            binding.bind(previewView?.get()?.surfaceProvider, serviceActive || foregroundUi)
            preview = binding.preview
            capture = binding.capture
            boundOwner = owner
            boundView = previewView
            mutableUi.update { it.copy(loading = false, ready = binding.ready, quality = Quality.HD,
                qualities = listOf(Quality.HD), lenses = CameraLens.entries, lens = CameraLens.REAR, issue = null) }
        } catch (error: Exception) {
            unbind()
            mutableUi.update { it.copy(loading = false, ready = false, issue = CameraIssue.CAMERA_UNAVAILABLE) }
            throw error
        }
    }

    private fun dualOutcome() {
        val current = session.value ?: return
        if (coordinator.activeDual && current.state == RecordingState.RECORDING && reportedDualAttempt != current.attemptId) {
            reportedDualAttempt = current.attemptId
            persistState()
        }
        if (current.state == RecordingState.INTERRUPTED) {
            mutableUi.update { it.copy(issue = CameraIssue.INTERRUPTED,
                diagnostics = (it.diagnostics + listOfNotNull(current.error?.code)).distinct()) }
            stop()
        }
    }

    private fun bind(force: Boolean = false) {
        if (!CaptureThermalPolicy.canStart(thermals.refresh())) {
            mutableUi.update { it.copy(issue = CameraIssue.THERMAL, ready = false, loading = false) }; return
        }
        val targetOwner = owner ?: return
        val viewReference = previewView ?: return
        val currentView = viewReference.get() ?: return
        if (coordinator.isBusy || ui.value.loading || !foregroundUi || !hasPermission(Manifest.permission.CAMERA)) return
        if (!force && boundOwner === targetOwner && boundView?.get() === currentView && (camera != null || dualBinding != null)) return
        val token = ++generation
        mutableUi.update { it.copy(loading = true, ready = false) }
        providerFuture.addListener({
            if (token != generation || owner !== targetOwner || targetOwner.lifecycle.currentState == Lifecycle.State.DESTROYED || !foregroundUi) return@addListener
            val view = viewReference.get() ?: return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                unbind()
                if (!hasPermission(Manifest.permission.CAMERA)) {
                    cameraPermissionDenied()
                    mutableUi.update { it.copy(loading = false) }
                    return@addListener
                }
                if (coordinator.selected != null) { bindDual(cameraProvider, null); return@addListener }
                val lenses = CameraLens.entries.filter { cameraProvider.hasCamera(it.selector) }
                if (lenses.isEmpty()) {
                    mutableUi.update { it.copy(loading = false, lenses = emptyList(), qualities = emptyList(), quality = null, issue = CameraIssue.NO_CAMERA) }
                    return@addListener
                }
                val lens = ui.value.lens.takeIf { it in lenses } ?: lenses.first()
                val info = cameraProvider.getCameraInfo(lens.selector)
                val supported = Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR)
                val ordered = listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.UHD).filter { it in supported }
                val quality = ui.value.quality.takeIf { it in ordered } ?: ordered.firstOrNull()
                if (quality == null) {
                    mutableUi.update { it.copy(loading = false, lenses = lenses, lens = lens, qualities = emptyList(), quality = null, issue = CameraIssue.UNSUPPORTED_QUALITY) }
                    return@addListener
                }
                // Keep negotiated choices visible even if this preview/video combination fails to bind.
                mutableUi.update { it.copy(lenses = lenses, lens = lens, qualities = ordered, quality = quality) }
                val newPreview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val newCapture = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build())
                newCapture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                // Set references before binding, so a synchronous acquisition failure can release them.
                preview = newPreview
                capture = newCapture
                val newCamera = cameraProvider.bindToLifecycle(captureLifecycle, lens.selector, newPreview, newCapture)
                camera = newCamera
                boundLens = lens
                boundOwner = targetOwner
                boundView = WeakReference(view)
                mutableUi.update { it.copy(loading = false, lenses = lenses, lens = lens, qualities = ordered, quality = quality, issue = null) }
                val observer = Observer<CameraState> { state ->
                    val open = state.type == CameraState.Type.OPEN && state.error == null
                    mutableUi.update { it.copy(ready = open && !it.loading, issue = if (state.error != null) CameraIssue.CAMERA_UNAVAILABLE else it.issue) }
                    // A rebind can briefly close/reopen during acquisition. It is not an active
                    // recording claim; explicit errors still stop immediately, and acquisition is bounded.
                    if (state.error != null || (!open && session.value?.state == RecordingState.RECORDING)) {
                        interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.CAMERA_UNAVAILABLE)
                    }
                }
                cameraObserver = observer
                newCamera.cameraInfo.cameraState.observe(captureLifecycle, observer)
            } catch (_: Exception) {
                unbind()
                mutableUi.update { it.copy(loading = false, ready = false, issue = CameraIssue.CAMERA_UNAVAILABLE) }
            }
        }, app.mainExecutor)
    }

    fun start() = requestStart(recovery = false)
    fun recover() = requestStart(recovery = true)

    private fun requestStart(recovery: Boolean, kind: RecordingKind = RecordingKind.STANDARD) {
        if (!CaptureThermalPolicy.canStart(thermals.refresh())) {
            mutableUi.update { it.copy(issue = CameraIssue.THERMAL) }; return
        }
        val application = app as com.umbracam.app.UmbraCamApplication
        if (application.screenRecording.isBusy || application.libraryOperation) {
            mutableUi.update { it.copy(issue = CameraIssue.PREPARE) }; return
        }
        if (capture == null || ui.value.initializing || ui.value.loading || ui.value.serviceRunning || !ui.value.ready || coordinator.isBusy || !foregroundUi || owner?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
        if (!RecordingService.notificationsAllowed(app)) { notificationPermissionDenied(); return }
        val sources = coordinator.selected?.requiredSources ?: buildSet {
            add(ui.value.lens.source)
            if (ui.value.audioEnabled) add(CaptureSource.MICROPHONE)
        }
        if (recovery && (sources != session.value?.request?.requiredSources || coordinator.selected != coordinator.descriptor)) {
            mutableUi.update { it.copy(issue = CameraIssue.RECOVERY_SETTINGS) }
            return
        }
        val attempt = UUID.randomUUID().toString()
        val accepted = if (recovery) coordinator.recover(attempt) else coordinator.begin(UUID.randomUUID().toString(), RecordingRequest(sources), attempt, kind)
        if (!accepted) return
        if (!hasPermission(Manifest.permission.CAMERA) || (CaptureSource.MICROPHONE in sources && !hasPermission(Manifest.permission.RECORD_AUDIO))) {
            coordinator.failed(attempt, RecordingErrorCode.PERMISSION_DENIED)
            mutableUi.update { it.copy(issue = if (!hasPermission(Manifest.permission.CAMERA)) CameraIssue.CAMERA_PERMISSION else CameraIssue.MICROPHONE_PERMISSION) }
            persistState()
            return
        }
        pendingServiceAttempt = attempt
        mutableUi.update { it.copy(serviceRunning = true, issue = null) }
        persistState()
        try {
            app.startForegroundService(Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra(RecordingService.EXTRA_ATTEMPT, attempt))
        } catch (_: Exception) { serviceStartFailed(attempt) }
    }

    fun canPromoteService(attempt: String): Boolean = pendingServiceAttempt == attempt && session.value?.attemptId == attempt &&
        session.value?.state in setOf(RecordingState.PREPARING, RecordingState.RECOVERING) && foregroundUi &&
        owner?.lifecycle?.currentState == Lifecycle.State.RESUMED && hasPermission(Manifest.permission.CAMERA) &&
        (CaptureSource.MICROPHONE !in session.value!!.request.requiredSources || hasPermission(Manifest.permission.RECORD_AUDIO))

    fun pendingServiceSession(): RecordingSession? = session.value?.takeIf { it.attemptId == pendingServiceAttempt && ui.value.serviceRunning }
    fun recordingDeleted(storageId: String) {
        if (coordinator.isBusy) return
        coordinator.recordingDeleted(storageId)
        persistState()
    }

    fun serviceAccepted(attempt: String) {
        if (!canPromoteService(attempt)) { serviceStartFailed(attempt); return }
        pendingServiceAttempt = null
        serviceActive = true
        updateCaptureLifecycle()
        coordinator.token?.let { value ->
            try { bindDual(provider ?: error("Missing provider"), value) }
            catch (_: Exception) {
                coordinator.failed(attempt, RecordingErrorCode.SOURCE_UNAVAILABLE, true)
                mutableUi.update { state -> state.copy(issue = CameraIssue.CAMERA_UNAVAILABLE) }
                persistState(); finishAttempt(attempt); return
            }
            healthTicker?.cancel()
            healthTicker = scope.launch {
                while (session.value?.attemptId == attempt && coordinator.isBusy) {
                    coordinator.tick(value); dualOutcome(); delay(250)
                }
            }
        }
        prepareRecording(attempt)
    }

    fun serviceStartFailed(attempt: String, awaitServiceDestroy: Boolean = false) {
        if (session.value?.attemptId != attempt) return
        pendingServiceAttempt = null
        coordinator.failed(attempt, RecordingErrorCode.PERMISSION_DENIED)
        mutableUi.update { it.copy(serviceRunning = awaitServiceDestroy, issue = CameraIssue.PREPARE) }
        persistState()
        finishAttempt(attempt)
    }

    fun serviceEnded(attempt: String) {
        if (session.value?.attemptId != attempt) return
        serviceActive = false
        pendingServiceAttempt = null
        mutableUi.update { it.copy(serviceRunning = false) }
        interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.INTERRUPTED)
        updateCaptureLifecycle()
        if (!coordinator.isBusy) finishAttempt(attempt)
    }

    private fun prepareRecording(id: String) {
        val videoCapture = capture ?: run { serviceStartFailed(id); return }
        val sources = session.value!!.request.requiredSources
        val preparing = session.value!!
        val preparingDescriptor = coordinator.descriptor
        val preparingDiagnostics = ui.value.diagnostics
        mutableUi.update { it.copy(durationMillis = 0, issue = null) }
        scope.launch {
            var output: File? = null
            try {
                output = withContext(io) { files.reserve().also { journal.write(preparing, preparingDescriptor, preparingDiagnostics) } }
                if (session.value?.attemptId != id) { quarantine(output); return@launch }
                activeOutput = id to output
                if (session.value?.attemptId != id || session.value?.state !in setOf(RecordingState.PREPARING, RecordingState.RECOVERING) || !serviceActive) {
                    quarantine(output)
                    if (session.value?.attemptId == id && session.value?.error == null) mutableUi.update { it.copy(issue = CameraIssue.NO_DATA) }
                    coordinator.failed(id, RecordingErrorCode.SOURCE_UNAVAILABLE)
                    persistState()
                    finishAttempt(id)
                    return@launch
                }
                if (!hasPermission(Manifest.permission.CAMERA) ||
                    (CaptureSource.MICROPHONE in sources && !hasPermission(Manifest.permission.RECORD_AUDIO))) throw SecurityException()
                if (!serviceActive || capture !== videoCapture) throw IllegalStateException("Camera is not service-ready")
                var pending = videoCapture.output.prepareRecording(app, FileOutputOptions.Builder(output).build())
                if (CaptureSource.MICROPHONE in sources) {
                    if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                    pending = pending.withAudioEnabled()
                }
                val pendingFile = output
                var startConfirmed = false
                recording = pending.start(app.mainExecutor) recordEvent@{ event ->
                    if (session.value?.attemptId != id || !coordinator.isBusy) return@recordEvent
                    when (event) {
                        is VideoRecordEvent.Start -> { startConfirmed = true; updateStats(id, event, startConfirmed) }
                        is VideoRecordEvent.Status -> updateStats(id, event, startConfirmed)
                        is VideoRecordEvent.Finalize -> finalize(id, pendingFile, event)
                        is VideoRecordEvent.Pause -> interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.INTERRUPTED)
                    }
                }
                // A missing Start/usable-audio callback must never leave a permanently preparing UI.
                scope.launch {
                    delay(10_000)
                    if (session.value?.attemptId == id && session.value?.state in setOf(RecordingState.PREPARING, RecordingState.RECOVERING)) {
                        interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.PREPARE)
                    }
                }
            } catch (error: Exception) {
                output?.let { quarantine(it) }
                if (session.value?.attemptId != id) return@launch
                val code = when (error) {
                    is InsufficientRecordingSpace -> RecordingErrorCode.STORAGE_FULL
                    is SecurityException -> RecordingErrorCode.PERMISSION_DENIED
                    else -> RecordingErrorCode.UNKNOWN
                }
                coordinator.failed(id, code)
                persistState()
                mutableUi.update { it.copy(issue = when (code) {
                    RecordingErrorCode.STORAGE_FULL -> CameraIssue.STORAGE
                    RecordingErrorCode.PERMISSION_DENIED -> if (hasPermission(Manifest.permission.CAMERA)) CameraIssue.MICROPHONE_PERMISSION else CameraIssue.CAMERA_PERMISSION
                    else -> CameraIssue.PREPARE
                }) }
                finishAttempt(id)
            }
        }
    }

    private fun updateStats(id: String, event: VideoRecordEvent, startConfirmed: Boolean) {
        val current = session.value ?: return
        if (current.state !in setOf(RecordingState.PREPARING, RecordingState.RECOVERING, RecordingState.RECORDING)) return
        val requiresAudio = CaptureSource.MICROPHONE in current.request.requiredSources
        val audio = event.recordingStats.audioStats.audioState
        val audioAvailable = !requiresAudio || audio == AudioStats.AUDIO_STATE_ACTIVE
        if (!RecordingService.notificationsAllowed(app)) {
            interrupt(RecordingErrorCode.PERMISSION_DENIED, CameraIssue.NOTIFICATION_PERMISSION)
            return
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.CAMERA_UNAVAILABLE)
            return
        }
        if (!coordinator.activeDual && !ui.value.ready) {
            if (current.state == RecordingState.RECORDING) interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.CAMERA_UNAVAILABLE)
            return // Preparing/recovering waits for acquisition; its deadline remains in force.
        }
        if (requiresAudio && (!hasPermission(Manifest.permission.RECORD_AUDIO) ||
                audio in setOf(AudioStats.AUDIO_STATE_SOURCE_SILENCED, AudioStats.AUDIO_STATE_ENCODER_ERROR, AudioStats.AUDIO_STATE_SOURCE_ERROR, AudioStats.AUDIO_STATE_MUTED) ||
                (current.state == RecordingState.RECORDING && !audioAvailable))) {
            interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.AUDIO_UNAVAILABLE)
            return
        }
        if (coordinator.activeDual) {
            coordinator.token?.let { coordinator.dualStats(it, startConfirmed, event.recordingStats.numBytesRecorded, audioAvailable) }
            dualOutcome()
        } else if (startConfirmed && audioAvailable && event.recordingStats.numBytesRecorded > 0) {
            coordinator.started(id, current.request.requiredSources)
            if (current.state != RecordingState.RECORDING) persistState()
        }
        mutableUi.update { it.copy(durationMillis = event.recordingStats.recordedDurationNanos / 1_000_000) }
    }

    fun stop() {
        val id = session.value?.attemptId ?: return
        if (!coordinator.isBusy) return
        coordinator.stop(id)
        persistState()
        recording?.stop()
        armStopDeadline(id)
    }

    fun onForegroundLost() {
        foregroundUi = false
        if (!serviceActive) interrupt(RecordingErrorCode.SOURCE_UNAVAILABLE, CameraIssue.INTERRUPTED)
        if (!serviceActive && !coordinator.isBusy) mutableUi.update { it.copy(ready = false) }
        updateCaptureLifecycle()
    }

    private fun interrupt(code: RecordingErrorCode, issue: CameraIssue) {
        val current = session.value ?: return
        if (current.state !in setOf(RecordingState.PREPARING, RecordingState.RECOVERING, RecordingState.RECORDING)) return
        coordinator.interrupt(current.attemptId, code)
        mutableUi.update { it.copy(issue = issue, diagnostics = if (coordinator.activeDual) (it.diagnostics + code).distinct() else it.diagnostics) }
        stop()
    }

    private fun finalize(id: String, output: File, event: VideoRecordEvent.Finalize) {
        if (!coordinator.finalizeReceived(id)) return
        recording = null
        stopDeadline?.cancel()
        stopDeadline = null
        persistState()
        if (owner == null || !CaptureThermalPolicy.canStart(thermals.status.value)) unbind()
        val existingWarning = session.value?.error
        val outputSpecification = coordinator.descriptor
        val requiredAudio = CaptureSource.MICROPHONE in (session.value?.request?.requiredSources ?: emptySet())
        scope.launch {
            var published: PublishedVideo? = null
            try {
                val allowed = event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE, VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE, VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE)
                if (!allowed) throw IllegalStateException("Recorder finalization failed")
                val video = withContext(io) {
                    outputSpecification?.let { spec ->
                        val metadata = android.media.MediaMetadataRetriever()
                        try {
                            metadata.setDataSource(output.path)
                            check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() == spec.outputWidth)
                            check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() == spec.outputHeight)
                            check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() == 0)
                        } finally { metadata.release() }
                    }
                    files.publish(output)
                }.also { published = it }
                if (session.value?.attemptId != id || !coordinator.isBusy) return@launch
                val warning = existingWarning ?: when {
                    event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> RecordingError(RecordingErrorCode.STORAGE_FULL, true)
                    event.hasError() || (requiredAudio && !video.hasAudio) -> RecordingError(RecordingErrorCode.SOURCE_UNAVAILABLE, true)
                    else -> null
                }
                val candidate = session.value!!.copy(state = RecordingState.SAVED, completedSegments = session.value!!.completedSegments + video.segment, availableSources = emptySet(), error = warning ?: existingWarning)
                val descriptor = coordinator.descriptor
                val diagnostics = (ui.value.diagnostics + listOfNotNull(warning?.code,
                    if (event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE) RecordingErrorCode.STORAGE_FULL else null,
                    if (requiredAudio && !video.hasAudio) RecordingErrorCode.SOURCE_UNAVAILABLE else null)).distinct()
                if (coordinator.activeDual) mutableUi.update { it.copy(diagnostics = diagnostics) }
                val metadataSaved = withContext(io) { runCatching { journal.write(candidate, descriptor, diagnostics) }.isSuccess }
                if (session.value?.attemptId != id || !coordinator.isBusy) return@launch
                coordinator.saved(id, video.segment, if (metadataSaved) warning else RecordingError(RecordingErrorCode.PERSISTENCE_FAILED, false))
                mutableUi.update { it.copy(issue = if (warning?.code == RecordingErrorCode.THERMAL_LIMIT) CameraIssue.THERMAL else if (warning != null) CameraIssue.SAVED_PARTIAL else null) }
                if (!metadataSaved) mutableUi.update { it.copy(issue = CameraIssue.METADATA, diagnostics = (it.diagnostics + RecordingErrorCode.PERSISTENCE_FAILED).distinct()) }
            } catch (_: Exception) {
                if (published == null) quarantine(output)
                coordinator.publicationFailed(id, if (event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE) RecordingErrorCode.STORAGE_FULL else RecordingErrorCode.FINALIZATION_FAILED, existingWarning?.recoverable == true)
                if (coordinator.activeDual) mutableUi.update { it.copy(diagnostics = (it.diagnostics + listOfNotNull(session.value?.error?.code)).distinct()) }
                persistState()
                mutableUi.update { it.copy(issue = if (event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) CameraIssue.NO_DATA else CameraIssue.FINALIZATION) }
            } finally {
                finishAttempt(id)
            }
        }
    }

    private suspend fun quarantine(file: File) {
        // If storage is unavailable, retain the staging file rather than deleting potentially useful output.
        withContext(io) { runCatching { files.quarantine(file) } }
    }

    private fun finishAttempt(attempt: String) {
        if (session.value?.attemptId != attempt) return
        healthTicker?.cancel()
        healthTicker = null
        stopDeadline?.cancel()
        if (activeOutput?.first == attempt) activeOutput = null
        if (owner == null) unbind()
        // Keep an existing CameraX binding: it can reopen after access returns. Rebinding here
        // races service teardown, explicit Retry and a new attempt with queued camera closes.
        else bind()
        updateCaptureLifecycle()
    }

    private fun persistState() {
        val snapshot = session.value ?: return
        val descriptor = coordinator.descriptor
        val diagnostics = ui.value.diagnostics
        scope.launch {
            val saved = withContext(io) { runCatching { journal.write(snapshot, descriptor, diagnostics) }.isSuccess }
            if (!saved && session.value?.attemptId == snapshot.attemptId) {
                mutableUi.update { it.copy(issue = CameraIssue.METADATA, diagnostics = if (coordinator.activeDual) (it.diagnostics + RecordingErrorCode.PERSISTENCE_FAILED).distinct() else it.diagnostics) }
                interrupt(RecordingErrorCode.PERSISTENCE_FAILED, CameraIssue.METADATA)
            }
        }
    }

    private fun armStopDeadline(attempt: String) {
        if (stopDeadline?.isActive == true) return
        stopDeadline = scope.launch {
            delay(20_000)
            if (!coordinator.timeoutWaitingForFinalize(attempt)) return@launch
            recording?.close()
            recording = null
            unbind()
            mutableUi.update { it.copy(issue = CameraIssue.FINALIZATION) }
            persistState()
            activeOutput?.takeIf { it.first == attempt }?.second?.let { file -> scope.launch { quarantine(file) } }
            if (activeOutput?.first == attempt) activeOutput = null
            updateCaptureLifecycle()
        }
    }

    private fun unbind() {
        dualBinding?.close()
        dualBinding = null
        cameraObserver?.let { camera?.cameraInfo?.cameraState?.removeObserver(it) }
        cameraObserver = null
        val owned = listOfNotNull(preview, capture)
        if (owned.isNotEmpty()) provider?.unbind(*owned.toTypedArray())
        camera = null
        boundLens = null
        preview = null
        capture = null
        boundOwner = null
        boundView = null
        mutableUi.update { it.copy(ready = false, loading = false) }
    }

}
