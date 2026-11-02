package com.umbracam.app.camera

import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Test

class DualOptionsStateTest {
    private val front = CameraDescriptor("1", CameraFacing.FRONT)
    private val rear = CameraDescriptor("0", CameraFacing.REAR)
    private val other = CameraDescriptor("2", CameraFacing.REAR)
    private fun options(cameras: List<CameraDescriptor>, vararg groups: List<CameraDescriptor>) =
        DualOptionsState(result = ConcurrentCameraCapabilities.Report(cameras, groups.map(::ConcurrentCameraGroup)))

    @Test fun exactAdvertisedPairsAreDistinctAndOrdered() {
        val state = options(listOf(front, rear, other), listOf(front, other), listOf(rear, front), listOf(front, rear))
        assertEquals(listOf(DualCameraPair(front, rear), DualCameraPair(front, other)), state.pairs)
    }
    @Test fun largerGroupsNeverYieldInferredPairs() {
        assertTrue(options(listOf(front, rear, other), listOf(front, rear, other)).pairs.isEmpty())
    }
    @Test fun staleOrChangedInventoryIsRejected() {
        assertTrue(options(listOf(front), listOf(front, rear)).pairs.isEmpty())
        assertTrue(options(listOf(front, rear.copy(facing = CameraFacing.UNKNOWN)), listOf(front, rear)).pairs.isEmpty())
        assertTrue(options(listOf(front, rear, rear), listOf(front, rear)).pairs.isEmpty())
    }
    @Test fun unknownAndSameFacingGroupsAreNotChoices() {
        assertTrue(options(listOf(rear, other), listOf(rear, other)).pairs.isEmpty())
        val unknown = front.copy(facing = CameraFacing.UNKNOWN)
        assertTrue(options(listOf(unknown, rear), listOf(unknown, rear)).pairs.isEmpty())
    }
    @Test fun unavailableStatesHaveNoPairs() {
        listOf(null, ConcurrentCameraCapabilities.UnsupportedPlatform,
            ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.ACCESS_DENIED)).forEach {
            assertTrue(DualOptionsState(result = it).pairs.isEmpty())
        }
    }
}
