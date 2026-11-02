package com.umbracam.core.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread confined by its Android owner; accepts callbacks only for the current attempt.
 * Stop wins over late Start callbacks. Recovery is explicit and uses a new attempt token.
 */
internal class RecordingSessionTransitions(private val validateRequest: (RecordingRequest) -> Unit) {
    private val mutableSession = MutableStateFlow<RecordingSession?>(null)
    val session: StateFlow<RecordingSession?> = mutableSession.asStateFlow()
    private var finalizingAttempt: String? = null
    val isBusy: Boolean get() = session.value?.state in busyStates

    fun begin(id: String, request: RecordingRequest, attemptId: String = id): Boolean {
        if (isBusy) return false
        validateRequest(request)
        mutableSession.value = RecordingSession(id, request.copy(requiredSources = request.requiredSources.toSet()), RecordingState.PREPARING, attemptId = attemptId)
        finalizingAttempt = null
        return true
    }

    fun recover(attemptId: String): Boolean {
        val current = session.value ?: return false
        if (isBusy || current.error?.recoverable != true) return false
        require(attemptId != current.attemptId && attemptId.isNotBlank())
        mutableSession.value = current.copy(attemptId = attemptId, state = RecordingState.RECOVERING, availableSources = emptySet(), error = null)
        finalizingAttempt = null
        return true
    }

    /** Disk state never restores an active capture claim or reusable authorization. */
    fun restoreInterrupted(previous: RecordingSession) {
        check(!isBusy)
        mutableSession.value = previous.copy(
            state = if (previous.completedSegments.isEmpty()) RecordingState.FAILED else RecordingState.SAVED,
            availableSources = emptySet(), error = RecordingError(RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED, false),
        )
    }

    fun started(id: String, available: Set<CaptureSource>) {
        val current = session.value ?: return
        if (current.attemptId != id || current.state !in setOf(RecordingState.PREPARING, RecordingState.RECOVERING)) return
        if (!available.containsAll(current.request.requiredSources)) {
            interrupt(id, RecordingErrorCode.SOURCE_UNAVAILABLE)
        } else {
            mutableSession.value = current.copy(state = RecordingState.RECORDING, availableSources = available.toSet())
        }
    }

    fun interrupt(id: String, code: RecordingErrorCode) {
        interrupt(id, code, recoverable = true)
    }

    fun interrupt(id: String, code: RecordingErrorCode, recoverable: Boolean) {
        val current = session.value ?: return
        if (current.attemptId != id || current.state !in setOf(RecordingState.PREPARING, RecordingState.RECORDING, RecordingState.RECOVERING)) return
        mutableSession.value = current.copy(state = RecordingState.INTERRUPTED, availableSources = emptySet(), error = RecordingError(code, recoverable))
    }

    fun stop(id: String) {
        val current = session.value ?: return
        if (current.attemptId != id || !isBusy) return
        mutableSession.value = current.copy(state = RecordingState.STOPPING, availableSources = emptySet())
    }

    /** Once Finalize arrives, publication owns completion; a missing-callback timer cannot race it. */
    fun finalizeReceived(id: String): Boolean {
        if (session.value?.attemptId != id || !isBusy) return false
        stop(id)
        finalizingAttempt = id
        return true
    }

    fun timeoutWaitingForFinalize(id: String): Boolean {
        if (session.value?.attemptId != id || session.value?.state != RecordingState.STOPPING || finalizingAttempt == id) return false
        failed(id, RecordingErrorCode.FINALIZATION_FAILED)
        return true
    }

    /** Called only after validation and atomic publication, never on Stop/Finalize receipt alone. */
    fun saved(id: String, segment: SavedSegment, warning: RecordingError? = null) {
        val current = session.value ?: return
        if (current.attemptId != id || current.state != RecordingState.STOPPING) return
        require(segment.durationMillis > 0)
        mutableSession.value = current.copy(state = RecordingState.SAVED, availableSources = emptySet(), completedSegments = current.completedSegments + segment, error = warning ?: current.error)
    }

    fun failed(id: String, code: RecordingErrorCode, recoverable: Boolean = false) {
        val current = session.value ?: return
        if (current.attemptId != id || !isBusy) return
        mutableSession.value = current.copy(state = RecordingState.FAILED, availableSources = emptySet(), error = RecordingError(code, recoverable))
    }

    /** Explicit library deletion invalidates recovery references; never edits an active attempt. */
    fun recordingDeleted(storageId: String) {
        check(!isBusy)
        val current = session.value ?: return
        if (current.completedSegments.none { it.storageId == storageId }) return
        val remaining = current.completedSegments.filterNot { it.storageId == storageId }
        mutableSession.value = current.copy(state = if (remaining.isEmpty()) RecordingState.IDLE else RecordingState.SAVED,
            availableSources = emptySet(), completedSegments = remaining, error = null)
    }

    companion object {
        val busyStates = setOf(RecordingState.PREPARING, RecordingState.RECORDING, RecordingState.INTERRUPTED, RecordingState.RECOVERING, RecordingState.STOPPING)
    }
}
