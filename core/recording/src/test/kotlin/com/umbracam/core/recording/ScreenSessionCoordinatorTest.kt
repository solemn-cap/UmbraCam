package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class ScreenSessionCoordinatorTest {
    private var now = 0L
    private val coordinator = ScreenSessionCoordinator { now }
    private fun begin(id: String = "session", attempt: String = "attempt") =
        coordinator.consented(coordinator.requestConsent()!!, id, attempt)!!
    private fun recording(token: ScreenCaptureAttempt) {
        coordinator.projectionReady(token); coordinator.sampleWritten(token, 1, 100)
    }
    @Test fun requestAndCancellationNeverCreateAnActiveSession() {
        val request = coordinator.requestConsent()!!
        assertTrue(coordinator.isBusy); assertNull(coordinator.session.value)
        assertNull(coordinator.requestConsent())
        coordinator.cancelConsent(request)
        assertFalse(coordinator.isBusy)
        assertNull(coordinator.consented(request, "s", "a"))
    }
    @Test fun lateCancelledResultCannotAuthorizeTheNewRequest() {
        val old = coordinator.requestConsent()!!; coordinator.cancelConsent(old)
        val fresh = coordinator.requestConsent()!!
        assertNull(coordinator.consented(old, "s", "a"))
        coordinator.cancelConsent(old)
        assertNotNull(coordinator.consented(fresh, "s", "a"))
    }
    @Test fun consentCannotBeReusedAfterCompletion() {
        val request = coordinator.requestConsent()!!
        val token = coordinator.consented(request, "s", "a")!!
        recording(token); coordinator.stop(token); coordinator.finalizeReceived(token)
        coordinator.saved(token, SavedSegment("video.mp4", 10))
        assertNull(coordinator.consented(request, "new", "new"))
        assertNotNull(coordinator.requestConsent())
    }
    @Test fun sourceAndRealSampleAreBothRequired() {
        val token = begin()
        coordinator.sampleWritten(token, 1, 100)
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        coordinator.projectionReady(token)
        coordinator.sampleWritten(token, 0, 0); coordinator.sampleWritten(token, -1, 100)
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        coordinator.sampleWritten(token, 1, 100)
        assertEquals(setOf(CaptureSource.SCREEN), coordinator.session.value!!.availableSources)
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
    }
    @Test fun combinedSessionWaitsForCameraPixelsInTheEncodedOutput() {
        val consent = coordinator.requestConsent()!!
        val required = setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA)
        val token = coordinator.consented(consent, "combined", "attempt", required)!!
        coordinator.projectionReady(token)
        coordinator.sampleWritten(token, 1, 100, setOf(CaptureSource.SCREEN))
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        coordinator.sampleWritten(token, 2, 100, required)
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
        assertEquals(required, coordinator.session.value!!.availableSources)
    }
    @Test fun combinedOutputLosesRecordingWhenCameraSourceIsLost() {
        val consent = coordinator.requestConsent()!!
        val required = setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA)
        val token = coordinator.consented(consent, "combined", "attempt", required)!!
        coordinator.projectionReady(token)
        coordinator.sampleWritten(token, 1, 100, required)
        coordinator.interrupted(token, RecordingErrorCode.SOURCE_UNAVAILABLE)
        coordinator.sampleWritten(token, 2, 100, setOf(CaptureSource.SCREEN))
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
    }
    @Test fun stopWinsOverLateSourceAndSample() {
        val token = begin(); coordinator.stop(token); recording(token)
        assertEquals(RecordingState.STOPPING, coordinator.session.value!!.state)
    }
    @Test fun revocationExitsRecordingAndCannotResumeFromSamples() {
        val token = begin(); recording(token)
        coordinator.interrupted(token, RecordingErrorCode.CONSENT_REVOKED)
        recording(token)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
        assertFalse(coordinator.session.value!!.error!!.recoverable)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
        coordinator.stop(token); coordinator.finalizeReceived(token)
        coordinator.saved(token, SavedSegment("partial.mp4", 20))
        assertEquals(RecordingErrorCode.CONSENT_REVOKED, coordinator.session.value!!.error!!.code)
        assertNotNull(coordinator.requestConsent())
    }
    @Test fun deadlineIsCheckedBeforeLateReadinessEvents() {
        val token = begin(); now = ScreenSessionCoordinator.ACQUISITION_MILLIS
        recording(token)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
    }
    @Test fun ownerTickExpiresMissingOutput() {
        val token = begin(); coordinator.projectionReady(token)
        now = ScreenSessionCoordinator.ACQUISITION_MILLIS; coordinator.tick(token)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
    }
    @Test fun idleStaticScreenDoesNotImplyRevocation() {
        val token = begin(); recording(token); now = 120_000; coordinator.tick(token)
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
    }
    @Test fun staleTokenCannotAffectNewAttemptEvenIfIdsMatch() {
        val old = begin(); coordinator.failed(old, RecordingErrorCode.ENCODER_FAILED)
        val fresh = begin(); recording(fresh)
        coordinator.stop(old); coordinator.interrupted(old, RecordingErrorCode.CONSENT_REVOKED)
        assertFalse(coordinator.finalizeReceived(old))
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
    }
    @Test fun publicationFenceBlocksMissingCallbackTimeout() {
        val token = begin(); recording(token); coordinator.stop(token)
        assertTrue(coordinator.finalizeReceived(token)); assertFalse(coordinator.timeoutWaitingForFinalize(token))
        coordinator.saved(token, SavedSegment("saved.mp4", 10))
        coordinator.failed(token, RecordingErrorCode.UNKNOWN)
        assertEquals(RecordingState.SAVED, coordinator.session.value!!.state)
    }
    @Test fun missingFinalizeFailsHonestly() {
        val token = begin(); coordinator.stop(token)
        assertTrue(coordinator.timeoutWaitingForFinalize(token))
        assertEquals(RecordingState.FAILED, coordinator.session.value!!.state)
        assertFalse(coordinator.session.value!!.error!!.recoverable)
    }
    @Test fun oldFilesSurviveRestorationWithoutAnyConsent() {
        val old = RecordingSession("s", RecordingRequest(setOf(CaptureSource.SCREEN)), RecordingState.RECORDING,
            setOf(CaptureSource.SCREEN), listOf(SavedSegment("old.mp4", 100)))
        coordinator.restoreInterrupted(old)
        assertEquals(RecordingState.SAVED, coordinator.session.value!!.state)
        assertEquals(old.completedSegments, coordinator.session.value!!.completedSegments)
        assertFalse(coordinator.awaitingConsent)
        assertFalse(coordinator.session.value!!.error!!.recoverable)
        assertNotNull(coordinator.requestConsent())
    }
    @Test fun restoringAnActiveOwnerIsRejected() {
        val token = begin()
        val previous = coordinator.session.value!!
        assertThrows(IllegalStateException::class.java) { coordinator.restoreInterrupted(previous) }
        coordinator.stop(token)
    }
    @Test fun foreignConsentAndCameraSnapshotsAreRejected() {
        val other = ScreenSessionCoordinator { now }
        assertNull(coordinator.consented(other.requestConsent()!!, "s", "a"))
        val camera = RecordingSession("s", RecordingRequest(setOf(CaptureSource.REAR_CAMERA)), RecordingState.PREPARING)
        assertThrows(IllegalArgumentException::class.java) { coordinator.restoreInterrupted(camera) }
    }
    @Test fun finalizeIsClaimedOnceAndLateBackendErrorsCannotStealPublication() {
        val token = begin(); recording(token); coordinator.stop(token)
        assertTrue(coordinator.finalizeReceived(token)); assertFalse(coordinator.finalizeReceived(token))
        coordinator.failed(token, RecordingErrorCode.ENCODER_FAILED)
        assertEquals(RecordingState.STOPPING, coordinator.session.value!!.state)
        coordinator.saved(token, SavedSegment("valid.mp4", 20))
        assertEquals(RecordingState.SAVED, coordinator.session.value!!.state)
    }
    @Test fun publicationOwnerCanReportStorageFailure() {
        val token = begin(); recording(token); coordinator.stop(token)
        coordinator.publicationFailed(token)
        assertEquals(RecordingState.STOPPING, coordinator.session.value!!.state)
        coordinator.finalizeReceived(token); coordinator.publicationFailed(token)
        assertEquals(RecordingState.FAILED, coordinator.session.value!!.state)
        assertEquals(RecordingErrorCode.FINALIZATION_FAILED, coordinator.session.value!!.error!!.code)
    }
    @Test fun savedBeforeFinalizeClaimIsIgnored() {
        val token = begin(); coordinator.stop(token)
        coordinator.saved(token, SavedSegment("not-published.mp4", 20))
        assertEquals(RecordingState.STOPPING, coordinator.session.value!!.state)
    }
    @Test fun busyAttemptRejectsAnotherConsentRequest() {
        val token = begin(); recording(token)
        assertNull(coordinator.requestConsent())
        coordinator.stop(token)
        assertNull(coordinator.requestConsent())
    }
    @Test fun emptyEncoderOutputCannotExtendAcquisitionDeadline() {
        val token = begin(); coordinator.projectionReady(token)
        now = 9_999; coordinator.sampleWritten(token, 0, 0)
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        now = 10_000; coordinator.sampleWritten(token, 1, 100)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
    }
    @Test fun invalidBeginBurnsConsentRatherThanPermittingReplay() {
        val request = coordinator.requestConsent()!!
        assertThrows(IllegalArgumentException::class.java) { coordinator.consented(request, "", "attempt") }
        assertNull(coordinator.consented(request, "valid", "attempt"))
        assertFalse(coordinator.isBusy)
    }
    @Test fun oldConsentCannotAuthorizeAnotherCoordinator() {
        val request = coordinator.requestConsent()!!
        assertNull(ScreenSessionCoordinator { now }.consented(request, "s", "a"))
        assertNotNull(coordinator.consented(request, "s", "a"))
    }
    @Test fun interruptionWarningSurvivesValidPublicationAndDisallowsResume() {
        val token = begin(); recording(token)
        coordinator.interrupted(token, RecordingErrorCode.CONSENT_REVOKED)
        coordinator.stop(token); coordinator.finalizeReceived(token)
        coordinator.saved(token, SavedSegment("valid.mp4", 100))
        assertEquals(RecordingState.SAVED, coordinator.session.value!!.state)
        assertFalse(coordinator.session.value!!.error!!.recoverable)
        assertEquals(RecordingErrorCode.CONSENT_REVOKED, coordinator.session.value!!.error!!.code)
    }
    @Test fun explicitDeletionClearsSavedReferenceAndAllowsFreshConsent() {
        val token = begin(); recording(token); coordinator.stop(token); coordinator.finalizeReceived(token)
        coordinator.saved(token, SavedSegment("valid.mp4", 100))
        coordinator.recordingDeleted("unrelated.mp4")
        assertEquals(1, coordinator.session.value!!.completedSegments.size)
        coordinator.recordingDeleted("valid.mp4")
        assertEquals(RecordingState.IDLE, coordinator.session.value!!.state)
        assertTrue(coordinator.session.value!!.completedSegments.isEmpty())
        assertNotNull(coordinator.requestConsent())
    }
    @Test fun deletionCannotMutateAnActiveSession() {
        val token = begin(); recording(token)
        assertThrows(IllegalStateException::class.java) { coordinator.recordingDeleted("valid.mp4") }
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
    }
    @Test fun originalInterruptionSurvivesLaterEncoderWarning() {
        val token = begin(); recording(token)
        coordinator.interrupted(token, RecordingErrorCode.CONSENT_REVOKED)
        coordinator.stop(token); coordinator.finalizeReceived(token)
        coordinator.saved(token, SavedSegment("valid.mp4", 100), RecordingError(RecordingErrorCode.ENCODER_FAILED, false))
        assertEquals(RecordingErrorCode.CONSENT_REVOKED, coordinator.session.value!!.error!!.code)
    }
}
