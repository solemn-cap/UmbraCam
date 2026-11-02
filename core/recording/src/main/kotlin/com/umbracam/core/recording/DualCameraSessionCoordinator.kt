package com.umbracam.core.recording

/** Fences callbacks across logical sessions, binding lifecycles and explicit recovery attempts. */
class DualAttemptToken internal constructor(val attemptId: String, val generation: Long)

/** Pure, serialized domain policy. No permissions, timers, CameraX or file publication live here.
 * The adapter must deliver explicit source loss immediately and schedule tick with a monotonic
 * clock; time-based staleness is only detected when a callback/tick is processed.
 */
class DualCameraSessionCoordinator(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val transitions = RecordingSessionTransitions { request ->
        require(request.requiredSources == setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA) ||
            request.requiredSources == setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE))
    }
    val session = transitions.session
    val isBusy: Boolean get() = transitions.isBusy
    fun recordingDeleted(storageId: String) = transitions.recordingDeleted(storageId)
    var specification: DualRecordingSpec? = null
        private set
    var token: DualAttemptToken? = null
        private set
    private var generation = 0L
    private var acquiredAt = 0L
    private var observedAt: Long? = null
    private var recorderStarted = false
    private var encodedBytes = 0L
    private var microphoneReady = false
    private var publicationClaimed = false
    private val open = mutableSetOf<String>()
    private data class Progress(val frame: Long, val received: Long, val previous: Long?)
    private val progress = mutableMapOf<String, Progress>()

    fun begin(id: String, spec: DualRecordingSpec, attemptId: String = id): DualAttemptToken? {
        if (isBusy) return null
        val now = now()
        check(generation < Long.MAX_VALUE)
        if (!transitions.begin(id, RecordingRequest(spec.requiredSources), attemptId)) return null
        specification = spec
        return reset(attemptId, now)
    }

    /** Only an explicit same-spec recovery may reuse the logical session and its completed segments. */
    fun recover(spec: DualRecordingSpec, attemptId: String): DualAttemptToken? {
        if (spec != specification || isBusy || session.value?.error?.recoverable != true) return null
        val now = now()
        check(generation < Long.MAX_VALUE)
        if (!transitions.recover(attemptId)) return null
        return reset(attemptId, now)
    }

    fun cameraAvailable(token: DualAttemptToken, cameraId: String, available: Boolean) {
        if (!accepts(token) || !known(cameraId)) return
        val now = now()
        if (!evaluate(now)) return
        if (!available) { interrupt(); return }
        open.add(cameraId)
        evaluate(now)
    }

    /** Duplicate/regressing frame numbers never refresh source health. Receipt time is local. */
    fun cameraProgress(token: DualAttemptToken, cameraId: String, frameNumber: Long) {
        if (!accepts(token) || !known(cameraId) || frameNumber < 0) return
        val now = now()
        if (!evaluate(now) || cameraId !in open) return
        val prior = progress[cameraId]
        if (prior != null && frameNumber <= prior.frame) return
        progress[cameraId] = Progress(frameNumber, now, prior?.received)
        evaluate(now)
    }

    fun microphoneAvailable(token: DualAttemptToken, available: Boolean) {
        if (!accepts(token) || specification?.microphone != true) return
        val now = now()
        if (!evaluate(now)) return
        if (!available) { interrupt(); return }
        microphoneReady = true
        evaluate(now)
    }

    fun recorderStarted(token: DualAttemptToken) {
        if (!accepts(token)) return
        val now = now()
        if (!evaluate(now)) return
        recorderStarted = true
        evaluate(now)
    }

    fun encodedOutput(token: DualAttemptToken, totalBytes: Long) {
        if (!accepts(token)) return
        val now = now()
        if (!evaluate(now)) return
        if (totalBytes <= encodedBytes) return
        encodedBytes = totalBytes
        evaluate(now)
    }

    fun tick(token: DualAttemptToken) { if (accepts(token)) evaluate(now()) }
    fun interrupt(token: DualAttemptToken, code: RecordingErrorCode) {
        if (accepts(token)) transitions.interrupt(token.attemptId, code)
    }
    fun stop(token: DualAttemptToken) { if (this.token == token) transitions.stop(token.attemptId) }
    fun finalizeReceived(token: DualAttemptToken): Boolean {
        if (this.token != token || publicationClaimed) return false
        return transitions.finalizeReceived(token.attemptId).also { if (it) publicationClaimed = true }
    }
    fun timeoutWaitingForFinalize(token: DualAttemptToken): Boolean = this.token == token && transitions.timeoutWaitingForFinalize(token.attemptId)

    /** The owner calls this only after validated atomic publication, not on Recorder Finalize. */
    fun saved(token: DualAttemptToken, segment: SavedSegment, warning: RecordingError? = null) {
        if (this.token != token || !publicationClaimed) return
        transitions.saved(token.attemptId, segment, session.value?.error ?: warning)
    }

    fun failed(token: DualAttemptToken, code: RecordingErrorCode, recoverable: Boolean = false) {
        if (this.token == token && !publicationClaimed) transitions.failed(token.attemptId, code, recoverable)
    }

    /** Only the storage owner may complete an already-claimed publication with a failure. */
    fun publicationFailed(token: DualAttemptToken, code: RecordingErrorCode = RecordingErrorCode.FINALIZATION_FAILED) {
        if (this.token == token && publicationClaimed) transitions.failed(token.attemptId, code)
    }

    private fun reset(attemptId: String, now: Long): DualAttemptToken {
        acquiredAt = now
        recorderStarted = false
        encodedBytes = 0
        microphoneReady = false
        publicationClaimed = false
        open.clear()
        progress.clear()
        return DualAttemptToken(attemptId, ++generation).also { token = it }
    }

    private fun accepts(value: DualAttemptToken) = token == value && session.value?.state in activeStates
    private fun known(id: String) = id == specification?.pair?.front?.id || id == specification?.pair?.rear?.id
    private fun interrupt() = transitions.interrupt(token!!.attemptId, RecordingErrorCode.SOURCE_UNAVAILABLE)

    /** Check expiry BEFORE consuming new evidence, so a late frame cannot revive a stalled input. */
    private fun evaluate(now: Long): Boolean {
        val current = session.value ?: return false
        if (current.state !in activeStates) return false
        val ids = listOf(specification!!.pair.front.id, specification!!.pair.rear.id)
        val fresh = ids.all { id -> id in open && progress[id]?.let { now - it.received < PROGRESS_TIMEOUT_MILLIS } == true }
        if (current.state == RecordingState.RECORDING) {
            if (!fresh || (specification!!.microphone && !microphoneReady)) { interrupt(); return false }
        } else {
            if (now - acquiredAt >= ACQUISITION_TIMEOUT_MILLIS) { interrupt(); return false }
            val advancing = ids.all { id -> progress[id]?.previous?.let { now - it < PROGRESS_TIMEOUT_MILLIS } == true }
            if (fresh && advancing && recorderStarted && encodedBytes > 0 && (!specification!!.microphone || microphoneReady)) {
                transitions.started(token!!.attemptId, specification!!.requiredSources)
            }
        }
        return true
    }

    private fun now(): Long = clockMillis().also {
        check(observedAt == null || it >= observedAt!!) { "A monotonic clock is required" }
        observedAt = it
    }

    companion object {
        const val ACQUISITION_TIMEOUT_MILLIS = 10_000L
        const val PROGRESS_TIMEOUT_MILLIS = 1_500L
        private val activeStates = setOf(RecordingState.PREPARING, RecordingState.RECOVERING, RecordingState.RECORDING)
    }
}
