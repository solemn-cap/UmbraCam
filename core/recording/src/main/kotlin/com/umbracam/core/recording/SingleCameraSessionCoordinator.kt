package com.umbracam.core.recording

/** The existing single-camera boundary; Android owner serializes all calls on Main. */
class SingleCameraSessionCoordinator {
    private val transitions = RecordingSessionTransitions { request ->
        require(request.requiredSources.count { it == CaptureSource.FRONT_CAMERA || it == CaptureSource.REAR_CAMERA } == 1)
        require(request.requiredSources.all { it in setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE) })
    }
    val session = transitions.session
    val isBusy: Boolean get() = transitions.isBusy
    fun begin(id: String, request: RecordingRequest, attemptId: String = id) = transitions.begin(id, request, attemptId)
    fun recover(attemptId: String) = transitions.recover(attemptId)
    fun restoreInterrupted(previous: RecordingSession) = transitions.restoreInterrupted(previous)
    fun started(id: String, available: Set<CaptureSource>) = transitions.started(id, available)
    fun interrupt(id: String, code: RecordingErrorCode) = transitions.interrupt(id, code)
    fun stop(id: String) = transitions.stop(id)
    fun finalizeReceived(id: String) = transitions.finalizeReceived(id)
    fun timeoutWaitingForFinalize(id: String) = transitions.timeoutWaitingForFinalize(id)
    fun saved(id: String, segment: SavedSegment, warning: RecordingError? = null) = transitions.saved(id, segment, warning)
    fun failed(id: String, code: RecordingErrorCode, recoverable: Boolean = false) = transitions.failed(id, code, recoverable)
    fun recordingDeleted(storageId: String) = transitions.recordingDeleted(storageId)
    companion object { val busyStates = RecordingSessionTransitions.busyStates }
}
