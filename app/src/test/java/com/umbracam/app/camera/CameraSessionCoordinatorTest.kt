package com.umbracam.app.camera

import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Test

class CameraSessionCoordinatorTest {
    private var time = 0L
    private val coordinator = CameraSessionCoordinator { time }
    private val spec = DualRecordingSpec(DualCameraPair(CameraDescriptor("f", CameraFacing.FRONT), CameraDescriptor("r", CameraFacing.REAR)))
    private val single = RecordingRequest(setOf(CaptureSource.REAR_CAMERA))
    private fun dualStart() {
        assertTrue(coordinator.select(spec))
        assertTrue(coordinator.begin("session", RecordingRequest(spec.requiredSources), "a"))
    }
    private fun ready() {
        val token = coordinator.token!!
        for (id in listOf("f", "r")) {
            coordinator.available(token, id, true)
            coordinator.progress(token, id, 0); coordinator.progress(token, id, 1)
        }
        coordinator.dualStats(token, true, 10, true)
    }

    @Test fun singleFlowStillRequiresItsOneCameraBoundary() {
        assertTrue(coordinator.begin("s", single, "a"))
        coordinator.started("a", single.requiredSources)
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
        assertFalse(coordinator.select(spec))
        assertFalse(coordinator.begin("overlap", single, "b"))
        coordinator.stop("a"); coordinator.finalizeReceived("a")
        coordinator.saved("a", SavedSegment("single.mp4", 1_000), null)
        assertTrue(coordinator.select(spec))
    }

    @Test fun dualCannotUseLegacyStartedCallbackToClaimRecording() {
        dualStart()
        coordinator.started("a", spec.requiredSources)
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        ready()
        assertEquals(RecordingState.RECORDING, coordinator.session.value!!.state)
    }

    @Test fun timerInterruptAndPermissionDiagnosticsSurvivePublication() {
        dualStart(); ready()
        coordinator.interrupt("a", RecordingErrorCode.PERMISSION_DENIED)
        coordinator.stop("a"); assertTrue(coordinator.finalizeReceived("a"))
        coordinator.saved("a", SavedSegment("dual.mp4", 1_000), null)
        assertEquals(RecordingErrorCode.PERMISSION_DENIED, coordinator.session.value!!.error!!.code)
        assertTrue(coordinator.recover("b"))
        ready()
        time = 1_500
        coordinator.tick(coordinator.token!!)
        assertEquals(RecordingState.INTERRUPTED, coordinator.session.value!!.state)
    }

    @Test fun modeSwitchRejectsLateDualCallbacksAndPreservesSnapshotUntilNewBegin() {
        dualStart(); val stale = coordinator.token!!
        coordinator.stop("a"); coordinator.failed("a", RecordingErrorCode.SOURCE_UNAVAILABLE)
        assertTrue(coordinator.select(null))
        assertTrue(coordinator.begin("new", single, "b"))
        coordinator.available(stale, "f", false)
        coordinator.dualStats(stale, true, 99, true)
        assertEquals("new", coordinator.session.value!!.id)
        assertEquals(RecordingState.PREPARING, coordinator.session.value!!.state)
        assertNull(coordinator.descriptor)
    }

    @Test fun restoredDualMetadataNeverSelectsOrRecoversCapture() {
        val previous = RecordingSession("old", RecordingRequest(spec.requiredSources), RecordingState.PREPARING)
        coordinator.restoreInterrupted(previous, spec)
        assertEquals(RecordingState.FAILED, coordinator.session.value!!.state)
        assertNull(coordinator.selected)
        assertNull(coordinator.token)
        assertEquals(spec, coordinator.descriptor)
        assertFalse(coordinator.recover("b"))
    }

    @Test fun changedDualSettingsCannotRecoverOldAttempt() {
        dualStart()
        coordinator.interrupt("a", RecordingErrorCode.SOURCE_UNAVAILABLE)
        coordinator.stop("a"); coordinator.finalizeReceived("a")
        coordinator.saved("a", SavedSegment("first.mp4", 1_000), null)
        coordinator.select(spec.copy(layout = DualLayout.SPLIT))
        assertFalse(coordinator.recover("b"))
    }
}
