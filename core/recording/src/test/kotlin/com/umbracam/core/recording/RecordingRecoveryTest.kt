package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class RecordingRecoveryTest {
    private val coordinator = SingleCameraSessionCoordinator()
    private val request = RecordingRequest(setOf(CaptureSource.REAR_CAMERA))
    private val first = SavedSegment("first.mp4", 1000)
    private fun interruptedSegment() {
        coordinator.begin("session", request, "attempt-1")
        coordinator.started("attempt-1", request.requiredSources)
        coordinator.interrupt("attempt-1", RecordingErrorCode.SOURCE_UNAVAILABLE)
        coordinator.stop("attempt-1")
        coordinator.saved("attempt-1", first)
    }

    @Test fun recoveryStartsOnlyAfterExplicitRequestAndRetainsSegments() {
        interruptedSegment()
        assertEquals(RecordingState.SAVED, coordinator.session.value?.state)
        assertTrue(coordinator.recover("attempt-2"))
        assertEquals(RecordingState.RECOVERING, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
        assertEquals(listOf(first), coordinator.session.value?.completedSegments)
    }

    @Test fun staleAttemptCannotResurrectOrFinishRecoveredSegment() {
        interruptedSegment()
        coordinator.recover("attempt-2")
        coordinator.started("attempt-1", request.requiredSources)
        coordinator.stop("attempt-1")
        coordinator.failed("attempt-1", RecordingErrorCode.UNKNOWN)
        assertEquals(RecordingState.RECOVERING, coordinator.session.value?.state)
        coordinator.started("attempt-2", request.requiredSources)
        assertEquals(RecordingState.RECORDING, coordinator.session.value?.state)
    }

    @Test fun recoveryAppendsASeparateFinalizedSegment() {
        interruptedSegment()
        coordinator.recover("attempt-2")
        coordinator.started("attempt-2", request.requiredSources)
        coordinator.stop("attempt-2")
        val second = SavedSegment("second.mp4", 500)
        coordinator.saved("attempt-2", second)
        assertEquals(listOf(first, second), coordinator.session.value?.completedSegments)
        assertEquals("session", coordinator.session.value?.id)
    }

    @Test fun failedRecoveryPreservesEarlierSegment() {
        interruptedSegment()
        coordinator.recover("attempt-2")
        coordinator.failed("attempt-2", RecordingErrorCode.PERMISSION_DENIED)
        assertEquals(listOf(first), coordinator.session.value?.completedSegments)
    }

    @Test fun stopWinsOverLateRecoveryStart() {
        interruptedSegment()
        coordinator.recover("attempt-2")
        coordinator.stop("attempt-2")
        coordinator.started("attempt-2", request.requiredSources)
        assertEquals(RecordingState.STOPPING, coordinator.session.value?.state)
    }

    @Test fun processRestoreNeverRestartsRecordingOrReusesAuthorization() {
        val disk = RecordingSession("session", request, RecordingState.RECORDING, request.requiredSources, listOf(first))
        coordinator.restoreInterrupted(disk)
        assertEquals(RecordingState.SAVED, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
        assertFalse(coordinator.recover("attempt-2"))
    }

    @Test fun restoreWithoutFinalizedSegmentsReportsFailure() {
        coordinator.restoreInterrupted(RecordingSession("session", request, RecordingState.PREPARING))
        assertEquals(RecordingState.FAILED, coordinator.session.value?.state)
        assertEquals(RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED, coordinator.session.value?.error?.code)
    }

    @Test fun normalUserStopDoesNotAuthorizeRecovery() {
        coordinator.begin("session", request)
        coordinator.stop("session")
        coordinator.saved("session", first)
        assertFalse(coordinator.recover("attempt-2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recoveryCannotReuseOldAttemptToken() { interruptedSegment(); coordinator.recover("attempt-1") }

    @Test fun slowPublicationCannotBeTimedOutOrOverlappedAfterFinalize() {
        coordinator.begin("session", request, "attempt-1")
        coordinator.stop("attempt-1")
        assertTrue(coordinator.finalizeReceived("attempt-1"))
        // Simulate publication being suspended longer than the callback watchdog.
        assertFalse(coordinator.timeoutWaitingForFinalize("attempt-1"))
        assertFalse(coordinator.begin("new-session", request, "attempt-2"))
        coordinator.saved("attempt-1", first)
        assertEquals(listOf(first), coordinator.session.value?.completedSegments)
    }

    @Test fun lateFinalizeAfterMissingCallbackTimeoutCannotAffectNewAttempt() {
        coordinator.begin("session", request, "attempt-1")
        coordinator.stop("attempt-1")
        assertTrue(coordinator.timeoutWaitingForFinalize("attempt-1"))
        assertTrue(coordinator.begin("new-session", request, "attempt-2"))
        assertFalse(coordinator.finalizeReceived("attempt-1"))
        assertFalse(coordinator.timeoutWaitingForFinalize("attempt-1"))
        assertEquals("attempt-2", coordinator.session.value?.attemptId)
        assertEquals(RecordingState.PREPARING, coordinator.session.value?.state)
    }
}
