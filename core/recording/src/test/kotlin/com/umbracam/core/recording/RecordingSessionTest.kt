package com.umbracam.core.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSessionTest {
    private val request = RecordingRequest(setOf(CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE))

    @Test(expected = IllegalArgumentException::class)
    fun recordingRejectsMissingRequiredSource() {
        RecordingSession("session", request, RecordingState.RECORDING, setOf(CaptureSource.REAR_CAMERA))
    }

    @Test fun recordingAcceptsAllRequiredSources() {
        val session = RecordingSession("session", request, RecordingState.RECORDING, request.requiredSources)
        assertEquals(RecordingState.RECORDING, session.state)
    }

    @Test fun interruptedSessionCanPreserveCompletedSegmentsWithoutSources() {
        val segments = listOf(SavedSegment("segment-1", 1000))
        val session = RecordingSession("session", request, RecordingState.INTERRUPTED, completedSegments = segments)
        assertEquals(segments, session.completedSegments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun savedRequiresFinalizedSegment() { RecordingSession("session", request, RecordingState.SAVED) }

    @Test(expected = IllegalArgumentException::class)
    fun failedRequiresError() { RecordingSession("session", request, RecordingState.FAILED) }

    @Test(expected = IllegalArgumentException::class)
    fun requestRejectsNoSources() { RecordingRequest(emptySet()) }

    @Test(expected = IllegalArgumentException::class)
    fun segmentRejectsNegativeDuration() { SavedSegment("segment", -1) }
}
