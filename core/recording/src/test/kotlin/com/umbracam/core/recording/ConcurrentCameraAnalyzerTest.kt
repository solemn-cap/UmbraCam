package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class ConcurrentCameraAnalyzerTest {
    private val front = CameraDescriptor("1", CameraFacing.FRONT)
    private val rear = CameraDescriptor("0", CameraFacing.REAR)

    @Test fun cameraCountAloneDoesNotAdvertiseConcurrency() {
        val result = ConcurrentCameraAnalyzer.analyze(listOf(front, rear), emptySet()) as ConcurrentCameraCapabilities.Report
        assertFalse(result.advertisesFrontAndRear)
        assertTrue(result.groups.isEmpty())
    }

    @Test fun advertisedFrontRearGroupIsRecognized() {
        val result = ConcurrentCameraAnalyzer.analyze(listOf(front, rear), setOf(setOf("1", "0"))) as ConcurrentCameraCapabilities.Report
        assertTrue(result.advertisesFrontAndRear)
        assertEquals(listOf("0", "1"), result.groups.single().cameras.map { it.id })
    }

    @Test fun sameFacingConcurrencyIsNotFrontRearSupport() {
        val otherRear = CameraDescriptor("2", CameraFacing.REAR)
        val result = ConcurrentCameraAnalyzer.analyze(listOf(front, rear, otherRear), setOf(setOf("0", "2"))) as ConcurrentCameraCapabilities.Report
        assertFalse(result.advertisesFrontAndRear)
        assertEquals(1, result.groups.size)
    }

    @Test fun largerGroupsAreKeptWithoutInventingPairs() {
        val external = CameraDescriptor("usb", CameraFacing.EXTERNAL)
        val result = ConcurrentCameraAnalyzer.analyze(listOf(front, rear, external), setOf(setOf("1", "0", "usb"))) as ConcurrentCameraCapabilities.Report
        assertTrue(result.advertisesFrontAndRear)
        assertEquals(3, result.groups.single().cameras.size)
    }

    @Test fun unknownFacingDoesNotBecomeAFrontCamera() {
        val unknown = CameraDescriptor("1", CameraFacing.UNKNOWN)
        val result = ConcurrentCameraAnalyzer.analyze(listOf(unknown, rear), setOf(setOf("0", "1"))) as ConcurrentCameraCapabilities.Report
        assertFalse(result.advertisesFrontAndRear)
    }

    @Test fun disconnectedIdsMakeTheSnapshotUnavailable() {
        assertEquals(ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.INCONSISTENT_SNAPSHOT),
            ConcurrentCameraAnalyzer.analyze(listOf(rear), setOf(setOf("0", "1"))))
    }

    @Test fun duplicateCameraIdsDoNotProduceAReport() {
        assertTrue(ConcurrentCameraAnalyzer.analyze(listOf(rear, rear), emptySet()) is ConcurrentCameraCapabilities.Unavailable)
    }

    @Test fun malformedSingletonGroupsDoNotProduceAReport() {
        assertTrue(ConcurrentCameraAnalyzer.analyze(listOf(rear), setOf(setOf("0"))) is ConcurrentCameraCapabilities.Unavailable)
    }

    @Test fun emptyDeviceInventoryIsRepresentedHonestly() {
        val result = ConcurrentCameraAnalyzer.analyze(emptyList(), emptySet()) as ConcurrentCameraCapabilities.Report
        assertTrue(result.cameras.isEmpty())
        assertFalse(result.advertisesFrontAndRear)
    }

    @Test fun inputCollectionMutationCannotChangeTheSnapshot() {
        val cameras = mutableListOf(rear, front)
        val ids = mutableSetOf("0", "1")
        val groups = mutableSetOf<Set<String>>(ids)
        val result = ConcurrentCameraAnalyzer.analyze(cameras, groups) as ConcurrentCameraCapabilities.Report
        cameras.clear(); ids.clear(); groups.clear()
        assertEquals(2, result.cameras.size)
        assertTrue(result.advertisesFrontAndRear)
    }
}
