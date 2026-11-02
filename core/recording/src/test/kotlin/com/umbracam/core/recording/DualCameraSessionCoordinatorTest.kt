package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class DualCameraSessionCoordinatorTest {
    private var time = 0L
    private val coordinator = DualCameraSessionCoordinator { time }
    private val spec = DualRecordingSpec(DualCameraPair(CameraDescriptor("front", CameraFacing.FRONT), CameraDescriptor("rear", CameraFacing.REAR)))
    private val segment = SavedSegment("verified.mp4", 1_000)
    private val state get() = coordinator.session.value!!.state
    private fun begin(settings: DualRecordingSpec = spec, attempt: String = "a") = coordinator.begin("session", settings, attempt)!!
    private fun frames(token: DualAttemptToken, start: Long = 0) {
        for (id in listOf("front", "rear")) {
            coordinator.cameraAvailable(token, id, true)
            coordinator.cameraProgress(token, id, start)
            coordinator.cameraProgress(token, id, start + 1)
        }
    }
    private fun ready(token: DualAttemptToken) {
        frames(token)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 100)
        if (coordinator.specification!!.microphone) coordinator.microphoneAvailable(token, true)
        assertEquals(RecordingState.RECORDING, state)
    }
    private fun save(token: DualAttemptToken) {
        assertTrue(coordinator.finalizeReceived(token))
        coordinator.saved(token, segment)
    }

    @Test fun recorderStartAndOutputNeverSubstituteForBothCameras() {
        val token = begin()
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 100)
        coordinator.cameraAvailable(token, "front", true)
        coordinator.cameraProgress(token, "front", 0)
        coordinator.cameraProgress(token, "front", 1)
        assertEquals(RecordingState.PREPARING, state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
        coordinator.cameraAvailable(token, "rear", true)
        coordinator.cameraProgress(token, "rear", 0)
        assertEquals(RecordingState.PREPARING, state)
        coordinator.cameraProgress(token, "rear", 1)
        assertEquals(RecordingState.RECORDING, state)
        assertEquals(spec.requiredSources, coordinator.session.value!!.availableSources)
    }

    @Test fun cameraOpenAloneDoesNotEstablishFrames() {
        val token = begin()
        for (id in listOf("front", "rear")) coordinator.cameraAvailable(token, id, true)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 10)
        assertEquals(RecordingState.PREPARING, state)
    }

    @Test fun framesBeforeOpenAndUnknownIdsCannotEstablishReadiness() {
        val token = begin()
        for (id in listOf("front", "rear", "other")) {
            coordinator.cameraProgress(token, id, 1)
            coordinator.cameraProgress(token, id, 2)
        }
        coordinator.cameraAvailable(token, "other", false)
        coordinator.cameraAvailable(token, "front", true)
        coordinator.cameraAvailable(token, "rear", true)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 10)
        assertEquals(RecordingState.PREPARING, state)
        frames(token)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun advancingFramesNeedRecorderStartAndPositiveOutput() {
        val token = begin()
        frames(token)
        coordinator.encodedOutput(token, 0)
        coordinator.encodedOutput(token, -10)
        coordinator.recorderStarted(token)
        assertEquals(RecordingState.PREPARING, state)
        coordinator.encodedOutput(token, 1)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun positiveOutputDoesNotReplaceRecorderStart() {
        val token = begin()
        frames(token)
        coordinator.encodedOutput(token, 5)
        assertEquals(RecordingState.PREPARING, state)
        coordinator.recorderStarted(token)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun duplicateOrRegressingFramesDoNotSatisfyAcquisition() {
        val token = begin()
        for (id in listOf("front", "rear")) {
            coordinator.cameraAvailable(token, id, true)
            for (frame in listOf(-1L, 3L, 3L, 2L)) coordinator.cameraProgress(token, id, frame)
        }
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 1)
        assertEquals(RecordingState.PREPARING, state)
        for (id in listOf("front", "rear")) coordinator.cameraProgress(token, id, 4)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun twoFramesMustBothBeRecentAtAcquisition() {
        val token = begin()
        for (id in listOf("front", "rear")) {
            coordinator.cameraAvailable(token, id, true)
            coordinator.cameraProgress(token, id, 0)
        }
        time = 1_500
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 10)
        for (id in listOf("front", "rear")) coordinator.cameraProgress(token, id, 1)
        assertEquals(RecordingState.PREPARING, state)
        for (id in listOf("front", "rear")) coordinator.cameraProgress(token, id, 2)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun optionalMicrophoneMustBeReadyAndLossInterrupts() {
        val token = begin(spec.copy(microphone = true))
        frames(token)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 100)
        assertEquals(RecordingState.PREPARING, state)
        coordinator.microphoneAvailable(token, true)
        assertEquals(RecordingState.RECORDING, state)
        coordinator.microphoneAvailable(token, false)
        assertEquals(RecordingState.INTERRUPTED, state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
    }

    @Test fun unrequestedMicrophoneEventsDoNotChangeSilentSession() {
        val token = begin()
        ready(token)
        coordinator.microphoneAvailable(token, false)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun activeFreshnessBoundaryIsExclusive() {
        val token = begin()
        ready(token)
        time = 1_499
        coordinator.tick(token)
        assertEquals(RecordingState.RECORDING, state)
        time = 1_500
        coordinator.tick(token)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun growingCompositeOutputCannotHideStalledSecondary() {
        val token = begin()
        ready(token)
        time = 1_000
        coordinator.cameraProgress(token, "rear", 2)
        coordinator.encodedOutput(token, 1_000)
        time = 1_500
        coordinator.cameraProgress(token, "rear", 3)
        coordinator.encodedOutput(token, 2_000)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun lateFrameCannotReviveExpiredSource() {
        val token = begin()
        ready(token)
        time = 1_500
        coordinator.cameraProgress(token, "front", 2)
        frames(token, 10)
        coordinator.recorderStarted(token)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun duplicateFramesDoNotRefreshAnActiveSource() {
        val token = begin()
        ready(token)
        time = 1_400
        coordinator.cameraProgress(token, "front", 1)
        coordinator.cameraProgress(token, "rear", 1)
        time = 1_500
        coordinator.tick(token)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun duplicateEncodedStatsStillDetectExpiredCameraProgress() {
        val token = begin()
        ready(token)
        time = 1_500
        coordinator.encodedOutput(token, 100)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun acquisitionExpiresAtTenSecondsWithoutStartingLate() {
        val token = begin()
        time = 9_999
        coordinator.tick(token)
        assertEquals(RecordingState.PREPARING, state)
        time = 10_000
        frames(token)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 1)
        assertEquals(RecordingState.INTERRUPTED, state)
        assertTrue(coordinator.isBusy)
    }

    @Test fun explicitCameraLossInterruptsDuringPreparationOrRecording() {
        val token = begin()
        coordinator.cameraAvailable(token, "front", false)
        assertEquals(RecordingState.INTERRUPTED, state)
        save(token)
        val next = coordinator.recover(spec, "b")!!
        ready(next)
        coordinator.cameraAvailable(next, "rear", false)
        assertEquals(RecordingState.INTERRUPTED, state)
    }

    @Test fun stopWinsOverEveryLateReadinessSignal() {
        val token = begin(spec.copy(microphone = true))
        coordinator.stop(token)
        frames(token)
        coordinator.recorderStarted(token)
        coordinator.encodedOutput(token, 10)
        coordinator.microphoneAvailable(token, true)
        coordinator.tick(token)
        assertEquals(RecordingState.STOPPING, state)
        assertNull(coordinator.begin("other", spec))
    }

    @Test fun sourceWarningSurvivesSuccessfulFinalizeAndOtherWarning() {
        val token = begin()
        ready(token)
        coordinator.cameraAvailable(token, "front", false)
        assertTrue(coordinator.finalizeReceived(token))
        coordinator.saved(token, segment, RecordingError(RecordingErrorCode.PERSISTENCE_FAILED, false))
        assertEquals(RecordingState.SAVED, state)
        assertEquals(RecordingErrorCode.SOURCE_UNAVAILABLE, coordinator.session.value!!.error?.code)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
    }

    @Test fun explicitSameSpecRecoveryRetainsSegmentsAndRequiresAllFreshEvidence() {
        val token = begin()
        ready(token)
        coordinator.cameraAvailable(token, "rear", false)
        save(token)
        assertNull(coordinator.recover(spec.copy(layout = DualLayout.SPLIT), "b"))
        val next = coordinator.recover(spec, "b")!!
        assertEquals(RecordingState.RECOVERING, state)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
        coordinator.recorderStarted(next)
        coordinator.encodedOutput(next, 200)
        assertEquals(RecordingState.RECOVERING, state)
        frames(next)
        assertEquals(RecordingState.RECORDING, state)
        assertNotEquals(token.generation, next.generation)
    }

    @Test fun staleRecoveryCallbacksCannotStartStopOrPublishNewAttempt() {
        val old = begin()
        coordinator.cameraAvailable(old, "rear", false)
        save(old)
        val next = coordinator.recover(spec, "b")!!
        frames(old)
        coordinator.recorderStarted(old)
        coordinator.encodedOutput(old, 500)
        coordinator.stop(old)
        coordinator.failed(old, RecordingErrorCode.UNKNOWN)
        assertFalse(coordinator.finalizeReceived(old))
        assertFalse(coordinator.timeoutWaitingForFinalize(old))
        coordinator.saved(old, SavedSegment("stale.mp4", 1))
        assertEquals(RecordingState.RECOVERING, state)
        ready(next)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
    }

    @Test fun generationProtectsReusedAttemptStringInNewLogicalSession() {
        val old = begin()
        coordinator.failed(old, RecordingErrorCode.ENCODER_FAILED)
        val next = coordinator.begin("different-session", spec, "a")!!
        assertNotEquals(old, next)
        frames(old)
        coordinator.recorderStarted(old)
        coordinator.encodedOutput(old, 10)
        assertEquals(RecordingState.PREPARING, state)
        ready(next)
    }

    @Test fun finalizationFenceRejectsWatchdogAndDuplicatePublication() {
        val token = begin()
        ready(token)
        assertTrue(coordinator.finalizeReceived(token))
        assertFalse(coordinator.finalizeReceived(token))
        assertFalse(coordinator.timeoutWaitingForFinalize(token))
        coordinator.failed(token, RecordingErrorCode.ENCODER_FAILED)
        assertEquals(RecordingState.STOPPING, state)
        assertNull(coordinator.begin("other", spec))
        coordinator.saved(token, segment)
        coordinator.saved(token, segment)
        coordinator.failed(token, RecordingErrorCode.UNKNOWN)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
        assertEquals(RecordingState.SAVED, state)
        assertNull(coordinator.recover(spec, "b"))
    }

    @Test fun missingFinalizeFailsHonestlyAndRetainsEarlierSegments() {
        val token = begin()
        coordinator.cameraAvailable(token, "rear", false)
        save(token)
        val next = coordinator.recover(spec, "b")!!
        coordinator.stop(next)
        assertTrue(coordinator.timeoutWaitingForFinalize(next))
        assertEquals(RecordingState.FAILED, state)
        assertEquals(RecordingErrorCode.FINALIZATION_FAILED, coordinator.session.value!!.error?.code)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
    }

    @Test fun publicationCannotOccurDuringRecordingAndZeroDurationIsRejected() {
        val token = begin()
        ready(token)
        coordinator.saved(token, segment)
        assertEquals(RecordingState.RECORDING, state)
        coordinator.stop(token)
        coordinator.saved(token, segment)
        assertEquals(RecordingState.STOPPING, state)
        assertTrue(coordinator.session.value!!.completedSegments.isEmpty())
        assertTrue(coordinator.finalizeReceived(token))
        assertThrows(IllegalArgumentException::class.java) { coordinator.saved(token, SavedSegment("empty.mp4", 0)) }
    }

    @Test fun backwardsClockIsRejectedAndStaleTokenDoesNotReadClock() {
        val token = begin()
        time = 100
        coordinator.tick(token)
        time = 99
        assertThrows(IllegalStateException::class.java) { coordinator.tick(token) }
        coordinator.tick(DualAttemptToken(token.attemptId, token.generation - 1))
    }

    @Test fun recoveryClearsMicrophoneAndOldRecorderEvidence() {
        val audioSpec = spec.copy(microphone = true)
        val old = begin(audioSpec)
        ready(old)
        coordinator.microphoneAvailable(old, false)
        save(old)
        val next = coordinator.recover(audioSpec, "b")!!
        frames(next)
        coordinator.microphoneAvailable(next, true)
        coordinator.recorderStarted(next)
        assertEquals(RecordingState.RECOVERING, state)
        coordinator.encodedOutput(next, 1)
        assertEquals(RecordingState.RECORDING, state)
    }

    @Test fun recoveryRejectsReusedAttemptAndCannotOverlapFinalization() {
        val old = begin()
        coordinator.cameraAvailable(old, "front", false)
        assertNull(coordinator.recover(spec, "b"))
        save(old)
        assertThrows(IllegalArgumentException::class.java) { coordinator.recover(spec, "a") }
        assertEquals(RecordingState.SAVED, state)
        assertSame(old, coordinator.token)
        val next = coordinator.recover(spec, "b")!!
        coordinator.stop(next)
        assertTrue(coordinator.finalizeReceived(next))
        assertNull(coordinator.recover(spec, "c"))
    }

    @Test fun storageFailureCompletesPublicationWithoutErasingPriorSegments() {
        val old = begin()
        coordinator.cameraAvailable(old, "front", false)
        save(old)
        val next = coordinator.recover(spec, "b")!!
        ready(next)
        coordinator.publicationFailed(next)
        assertEquals(RecordingState.RECORDING, state)
        coordinator.finalizeReceived(next)
        coordinator.publicationFailed(old)
        assertEquals(RecordingState.STOPPING, state)
        coordinator.publicationFailed(next)
        assertEquals(RecordingState.FAILED, state)
        assertEquals(RecordingErrorCode.FINALIZATION_FAILED, coordinator.session.value!!.error?.code)
        assertEquals(listOf(segment), coordinator.session.value!!.completedSegments)
        coordinator.saved(next, segment)
        assertEquals(RecordingState.FAILED, state)
    }
}
