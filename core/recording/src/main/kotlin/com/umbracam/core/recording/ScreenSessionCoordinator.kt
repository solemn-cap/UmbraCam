package com.umbracam.core.recording

/** Ephemeral correlation only; never an Android projection grant or a persisted permission. */
class ScreenConsentRequest internal constructor()
class ScreenCaptureAttempt internal constructor(val id: String)

/** Serialized by the Android owner. Every new attempt needs a distinct system consent result. */
class ScreenSessionCoordinator(private val clockMillis: () -> Long) {
    private val transitions = RecordingSessionTransitions { request ->
        require(request.requiredSources == setOf(CaptureSource.SCREEN) ||
            request.requiredSources == setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA))
    }
    val session = transitions.session
    private var pending: ScreenConsentRequest? = null
    private var attempt: ScreenCaptureAttempt? = null
    private var projectionReady = false
    private var deadline = 0L
    private var publicationClaimed = false
    private var requiredSources = setOf(CaptureSource.SCREEN)
    val awaitingConsent: Boolean get() = pending != null
    val isBusy: Boolean get() = awaitingConsent || transitions.isBusy

    fun requestConsent(): ScreenConsentRequest? {
        if (isBusy) return null
        return ScreenConsentRequest().also { pending = it }
    }

    fun cancelConsent(request: ScreenConsentRequest) {
        if (pending === request) pending = null
    }

    /** Call only for RESULT_OK with fresh data; the platform still enforces actual authorization. */
    fun consented(
        request: ScreenConsentRequest,
        id: String,
        attemptId: String,
        sources: Set<CaptureSource> = setOf(CaptureSource.SCREEN),
    ): ScreenCaptureAttempt? {
        if (pending !== request) return null
        pending = null // Burn the result even if a caller supplies invalid session identifiers.
        if (!transitions.begin(id, RecordingRequest(sources), attemptId)) return null
        requiredSources = sources.toSet()
        projectionReady = false
        publicationClaimed = false
        deadline = clockMillis() + ACQUISITION_MILLIS
        return ScreenCaptureAttempt(attemptId).also { attempt = it }
    }

    fun projectionReady(token: ScreenCaptureAttempt) {
        if (!preparing(token)) return
        projectionReady = true
    }

    /** A real encoded media sample written to the muxer, not header/config bytes or start() return. */
    fun sampleWritten(
        token: ScreenCaptureAttempt,
        presentationTimeUs: Long,
        bytes: Int,
        outputSources: Set<CaptureSource> = setOf(CaptureSource.SCREEN),
    ) {
        if (!preparing(token) || !projectionReady || bytes <= 0 || presentationTimeUs < 0 ||
            !outputSources.containsAll(requiredSources)) return
        transitions.started(token.id, requiredSources)
    }

    fun tick(token: ScreenCaptureAttempt) { preparing(token) }

    fun interrupted(token: ScreenCaptureAttempt, code: RecordingErrorCode) {
        if (owns(token)) transitions.interrupt(token.id, code, recoverable = false)
    }
    fun stop(token: ScreenCaptureAttempt) {
        if (!owns(token)) return
        projectionReady = false
        transitions.stop(token.id)
    }
    fun finalizeReceived(token: ScreenCaptureAttempt): Boolean {
        if (!owns(token) || publicationClaimed || !transitions.finalizeReceived(token.id)) return false
        publicationClaimed = true
        return true
    }
    fun timeoutWaitingForFinalize(token: ScreenCaptureAttempt): Boolean = owns(token) && transitions.timeoutWaitingForFinalize(token.id)
    fun saved(token: ScreenCaptureAttempt, segment: SavedSegment, warning: RecordingError? = null) {
        if (owns(token) && publicationClaimed) transitions.saved(token.id, segment, (session.value?.error ?: warning)?.copy(recoverable = false))
    }
    fun failed(token: ScreenCaptureAttempt, code: RecordingErrorCode) {
        if (owns(token) && !publicationClaimed) transitions.failed(token.id, code, recoverable = false)
    }
    fun publicationFailed(token: ScreenCaptureAttempt) {
        if (owns(token) && publicationClaimed) transitions.failed(token.id, RecordingErrorCode.FINALIZATION_FAILED, recoverable = false)
    }
    fun recordingDeleted(storageId: String) = transitions.recordingDeleted(storageId)
    fun restoreInterrupted(previous: RecordingSession) {
        check(!isBusy)
        require(previous.request.requiredSources == setOf(CaptureSource.SCREEN) ||
            previous.request.requiredSources == setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA))
        pending = null; attempt = null; projectionReady = false; publicationClaimed = false
        requiredSources = previous.request.requiredSources.toSet()
        transitions.restoreInterrupted(previous)
    }

    private fun owns(token: ScreenCaptureAttempt) = attempt === token && session.value?.attemptId == token.id
    private fun preparing(token: ScreenCaptureAttempt): Boolean {
        if (!owns(token) || session.value?.state != RecordingState.PREPARING) return false
        if (clockMillis() >= deadline) {
            transitions.interrupt(token.id, RecordingErrorCode.SOURCE_UNAVAILABLE, recoverable = false)
            return false
        }
        return true
    }
    companion object { const val ACQUISITION_MILLIS = 10_000L }
}
