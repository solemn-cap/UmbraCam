package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class SingleCameraSessionCoordinatorTest {
    private val coordinator = SingleCameraSessionCoordinator()
    private val request = RecordingRequest(setOf(CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE))
    private val segment = SavedSegment("verified.mp4", 1000)

    @Test fun preparingIsNotRecordingBeforeStartCallback() {
        assertTrue(coordinator.begin("one", request))
        assertEquals(RecordingState.PREPARING, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
    }

    @Test fun sosKindIsRetainedThroughStartAndFinalization() {
        assertTrue(coordinator.begin("sos-one", RecordingRequest(setOf(CaptureSource.REAR_CAMERA)), kind = RecordingKind.SOS))
        coordinator.started("sos-one", setOf(CaptureSource.REAR_CAMERA))
        assertEquals(RecordingKind.SOS, coordinator.session.value?.kind)
        coordinator.stop("sos-one")
        coordinator.saved("sos-one", SavedSegment("sos.mp4", 1_000))
        assertEquals(RecordingKind.SOS, coordinator.session.value?.kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sosCannotRequestFrontCameraOrMicrophone() {
        coordinator.begin("sos-bad", request, kind = RecordingKind.SOS)
    }

    @Test fun missingMicrophoneNeverReportsRecording() {
        coordinator.begin("one", request)
        coordinator.started("one", setOf(CaptureSource.REAR_CAMERA))
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
    }

    @Test fun stopWinsOverDelayedStart() {
        coordinator.begin("one", request)
        coordinator.stop("one")
        coordinator.started("one", request.requiredSources)
        assertEquals(RecordingState.STOPPING, coordinator.session.value?.state)
    }

    @Test fun requiredSourceLossImmediatelyLeavesRecording() {
        coordinator.begin("one", request)
        coordinator.started("one", request.requiredSources)
        coordinator.interrupt("one", RecordingErrorCode.SOURCE_UNAVAILABLE)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.availableSources.isEmpty())
    }

    @Test fun anotherAttemptCannotStartWhileFinalizing() {
        coordinator.begin("one", request)
        coordinator.stop("one")
        assertFalse(coordinator.begin("two", request))
        assertEquals("one", coordinator.session.value?.id)
    }

    @Test fun savedRequiresFinalizingAndPublishedSegment() {
        coordinator.begin("one", request)
        coordinator.started("one", request.requiredSources)
        coordinator.saved("one", segment)
        assertEquals(RecordingState.RECORDING, coordinator.session.value?.state)
        coordinator.stop("one")
        coordinator.saved("one", segment)
        assertEquals(RecordingState.SAVED, coordinator.session.value?.state)
        assertEquals(listOf(segment), coordinator.session.value?.completedSegments)
        assertFalse(coordinator.isBusy)
    }

    @Test fun interruptedVideoCanBePreservedWithWarningAfterValidation() {
        coordinator.begin("one", request)
        coordinator.interrupt("one", RecordingErrorCode.SOURCE_UNAVAILABLE)
        coordinator.stop("one")
        coordinator.saved("one", segment)
        assertEquals(RecordingState.SAVED, coordinator.session.value?.state)
        assertEquals(RecordingErrorCode.SOURCE_UNAVAILABLE, coordinator.session.value?.error?.code)
    }

    @Test fun finalizationFailureIsNotSaved() {
        coordinator.begin("one", request)
        coordinator.stop("one")
        coordinator.failed("one", RecordingErrorCode.FINALIZATION_FAILED)
        assertEquals(RecordingState.FAILED, coordinator.session.value?.state)
        assertTrue(coordinator.session.value!!.completedSegments.isEmpty())
    }

    @Test fun callbacksFromPreviousAttemptCannotChangeCurrentAttempt() {
        coordinator.begin("one", request)
        coordinator.failed("one", RecordingErrorCode.STORAGE_FULL)
        coordinator.begin("two", request)
        coordinator.started("one", request.requiredSources)
        coordinator.stop("one")
        coordinator.saved("one", segment)
        coordinator.failed("one", RecordingErrorCode.UNKNOWN)
        assertEquals("two", coordinator.session.value?.id)
        assertEquals(RecordingState.PREPARING, coordinator.session.value?.state)
    }

    @Test fun duplicateStopOrFailureCannotEraseSavedSegment() {
        coordinator.begin("one", request)
        coordinator.stop("one")
        coordinator.stop("one")
        coordinator.saved("one", segment)
        coordinator.stop("one")
        coordinator.failed("one", RecordingErrorCode.UNKNOWN)
        assertEquals(listOf(segment), coordinator.session.value?.completedSegments)
        assertEquals(RecordingState.SAVED, coordinator.session.value?.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dualCameraIsOutsideThisCoordinatorScope() {
        coordinator.begin("one", RecordingRequest(setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun screenInputIsNotAcceptedAsSingleCameraRecording() {
        coordinator.begin("one", RecordingRequest(setOf(CaptureSource.REAR_CAMERA, CaptureSource.SCREEN)))
    }
}
