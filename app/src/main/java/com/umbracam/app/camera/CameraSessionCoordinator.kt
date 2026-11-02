package com.umbracam.app.camera

import com.umbracam.core.recording.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serialized routing behind the process owner's single observable session. Never runs two engines. */
internal class CameraSessionCoordinator(clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val single = SingleCameraSessionCoordinator()
    private val dual = DualCameraSessionCoordinator(clock)
    private val mutableSession = MutableStateFlow<RecordingSession?>(null)
    val session = mutableSession.asStateFlow()
    var selected: DualRecordingSpec? = null
        private set
    var activeDual = false
        private set
    private var restoredDescriptor: DualRecordingSpec? = null
    val descriptor: DualRecordingSpec? get() = if (activeDual) dual.specification else restoredDescriptor
    val token: DualAttemptToken? get() = if (activeDual) dual.token else null
    val isBusy get() = session.value?.state in SingleCameraSessionCoordinator.busyStates
    private fun publish() { mutableSession.value = if (activeDual) dual.session.value else single.session.value }
    fun recordingDeleted(storageId: String) {
        if (activeDual) dual.recordingDeleted(storageId) else single.recordingDeleted(storageId)
        publish()
    }
    fun select(spec: DualRecordingSpec?): Boolean {
        if (isBusy) return false
        selected = spec
        return true
    }
    fun begin(id: String, request: RecordingRequest, attemptId: String): Boolean {
        if (isBusy) return false
        val spec = selected
        val accepted = if (spec != null) {
            require(request.requiredSources == spec.requiredSources)
            dual.begin(id, spec, attemptId) != null
        } else single.begin(id, request, attemptId)
        if (accepted) { activeDual = spec != null; restoredDescriptor = null; publish() }
        return accepted
    }
    fun recover(attemptId: String): Boolean {
        if (isBusy) return false
        val accepted = if (activeDual) selected?.let { dual.recover(it, attemptId) != null } ?: false
            else if (selected == null) single.recover(attemptId) else false
        publish()
        return accepted
    }
    fun restoreInterrupted(previous: RecordingSession, descriptor: DualRecordingSpec? = null) {
        check(!isBusy)
        activeDual = false
        selected = null
        restoredDescriptor = descriptor
        single.restoreInterrupted(previous)
        publish()
    }
    fun started(id: String, available: Set<CaptureSource>) {
        if (!activeDual) { single.started(id, available); publish() }
    }
    fun dualStats(value: DualAttemptToken, started: Boolean, bytes: Long, audioReady: Boolean) {
        if (value !== token) return
        if (started) dual.recorderStarted(value)
        if (descriptor?.microphone == true && audioReady) dual.microphoneAvailable(value, true)
        dual.encodedOutput(value, bytes)
        publish()
    }
    fun available(value: DualAttemptToken, id: String, open: Boolean) {
        if (value !== token) return
        dual.cameraAvailable(value, id, open); publish()
    }
    fun progress(value: DualAttemptToken, id: String, frame: Long) {
        if (value !== token) return
        dual.cameraProgress(value, id, frame); publish()
    }
    fun tick(value: DualAttemptToken) { if (value === token) { dual.tick(value); publish() } }
    fun interrupt(id: String, code: RecordingErrorCode) {
        if (activeDual) token?.takeIf { it.attemptId == id }?.let { dual.interrupt(it, code) }
        else single.interrupt(id, code)
        publish()
    }
    fun stop(id: String) {
        if (activeDual) token?.takeIf { it.attemptId == id }?.let(dual::stop) else single.stop(id)
        publish()
    }
    fun finalizeReceived(id: String): Boolean {
        val accepted = if (activeDual) token?.takeIf { it.attemptId == id }?.let(dual::finalizeReceived) ?: false else single.finalizeReceived(id)
        publish(); return accepted
    }
    fun timeoutWaitingForFinalize(id: String): Boolean {
        val timedOut = if (activeDual) token?.takeIf { it.attemptId == id }?.let(dual::timeoutWaitingForFinalize) ?: false else single.timeoutWaitingForFinalize(id)
        publish(); return timedOut
    }
    fun saved(id: String, segment: SavedSegment, warning: RecordingError?) {
        if (activeDual) token?.takeIf { it.attemptId == id }?.let { dual.saved(it, segment, warning) } else single.saved(id, segment, warning)
        publish()
    }
    fun failed(id: String, code: RecordingErrorCode, recoverable: Boolean = false) {
        if (activeDual) token?.takeIf { it.attemptId == id }?.let { dual.failed(it, code, recoverable) } else single.failed(id, code, recoverable)
        publish()
    }
    fun publicationFailed(id: String, code: RecordingErrorCode, recoverable: Boolean) {
        if (activeDual) token?.takeIf { it.attemptId == id }?.let { dual.publicationFailed(it, code) } else single.failed(id, code, recoverable)
        publish()
    }
}
