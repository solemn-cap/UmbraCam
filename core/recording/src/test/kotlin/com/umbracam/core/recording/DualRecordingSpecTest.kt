package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class DualRecordingSpecTest {
    @Test fun initialBackendOnlyAcceptsPortraitApi30HdProfile() {
        val spec = DualRecordingSpec(pair)
        assertTrue(spec.isSupportedByInitialBackend(30))
        assertFalse(spec.isSupportedByInitialBackend(29))
        assertFalse(spec.copy(rotation = DualOutputRotation.DEGREES_90).isSupportedByInitialBackend(30))
        assertFalse(spec.copy(shape = DualOutputShape.LANDSCAPE).isSupportedByInitialBackend(30))
    }
    private val front = CameraDescriptor("front-1", CameraFacing.FRONT)
    private val rear = CameraDescriptor("rear-0", CameraFacing.REAR)
    private val pair = DualCameraPair(front, rear)
    private fun report(cameras: List<CameraDescriptor> = listOf(front, rear), groups: List<ConcurrentCameraGroup> = listOf(ConcurrentCameraGroup(listOf(front, rear)))) =
        ConcurrentCameraCapabilities.Report(cameras, groups)
    private val ids = setOf(front.id, rear.id)

    @Test fun exactPairMustBeIndependentlyAdvertised() {
        assertTrue(pair.isAdvertisedBy(report(), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(report(), emptySet()))
        assertFalse(pair.isAdvertisedBy(report(groups = emptyList()), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(ConcurrentCameraCapabilities.UnsupportedPlatform, setOf(ids)))
        assertFalse(pair.isAdvertisedBy(ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.CAMERA_SERVICE), setOf(ids)))
    }

    @Test fun largerGroupsNeverCreateAnEligiblePair() {
        val third = CameraDescriptor("other", CameraFacing.REAR)
        assertFalse(pair.isAdvertisedBy(report(listOf(front, rear, third), listOf(ConcurrentCameraGroup(listOf(front, rear, third)))), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(report(), setOf(ids + third.id)))
    }

    @Test fun staleFacingMissingAndAmbiguousInventoryAreRejected() {
        assertFalse(pair.isAdvertisedBy(report(cameras = listOf(front)), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(report(cameras = listOf(front, rear.copy(facing = CameraFacing.UNKNOWN))), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(report(cameras = listOf(front, rear, front)), setOf(ids)))
        assertFalse(pair.isAdvertisedBy(report(groups = listOf(ConcurrentCameraGroup(listOf(front, rear.copy(facing = CameraFacing.EXTERNAL))))), setOf(ids)))
    }

    @Test fun invalidPairIdentityOrFacingIsRejected() {
        for ((a, b) in listOf(front to rear.copy(id = front.id), front.copy(facing = CameraFacing.UNKNOWN) to rear,
            front to rear.copy(facing = CameraFacing.FRONT), front.copy(id = "x".repeat(257)) to rear,
            front.copy(id = "bad\nID") to rear)) {
            assertThrows(IllegalArgumentException::class.java) { DualCameraPair(a, b) }
        }
    }

    @Test fun requirementsAreFixedByAudioChoiceWithoutMutableBackingCollections() {
        val silent = DualRecordingSpec(pair)
        assertEquals(setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA), silent.requiredSources)
        assertEquals(silent.requiredSources + CaptureSource.MICROPHONE, silent.copy(microphone = true).requiredSources)
        assertFalse(silent.requiredSources.any { it == CaptureSource.SCREEN || it == CaptureSource.INTERNAL_AUDIO })
        assertEquals(DualVideoProfile.HD_SDR_30, silent.profile)
    }

    @Test fun pictureInPictureUsesFinalFrameMarginsInEveryShapeAndRotation() {
        for (shape in DualOutputShape.entries) for (rotation in DualOutputRotation.entries) {
            val spec = DualRecordingSpec(pair, shape = shape, rotation = rotation)
            assertEquals(NormalizedRect(0.0, 0.0, 1.0, 1.0), spec.geometry.rear)
            assertEquals(NormalizedRect(.64, .04, .96, .36), spec.geometry.front)
            assertEquals(if (shape == DualOutputShape.PORTRAIT) 720 else 1280, spec.outputWidth)
            assertEquals(if (shape == DualOutputShape.PORTRAIT) 1280 else 720, spec.outputHeight)
        }
    }

    @Test fun splitRegionsCoverOutputWithoutGapOrOverlap() {
        val portrait = DualRecordingSpec(pair, layout = DualLayout.SPLIT).geometry
        assertEquals(NormalizedRect(0.0, 0.0, 1.0, .5), portrait.rear)
        assertEquals(NormalizedRect(0.0, .5, 1.0, 1.0), portrait.front)
        val landscape = DualRecordingSpec(pair, layout = DualLayout.SPLIT, shape = DualOutputShape.LANDSCAPE).geometry
        assertEquals(NormalizedRect(0.0, 0.0, .5, 1.0), landscape.rear)
        assertEquals(NormalizedRect(.5, 0.0, 1.0, 1.0), landscape.front)
    }

    @Test fun rectanglesRejectNaNInfinityOutOfFrameAndEmptyAreas() {
        for (values in listOf(listOf(Double.NaN, 0.0, 1.0, 1.0), listOf(0.0, 0.0, Double.POSITIVE_INFINITY, 1.0),
            listOf(-.01, 0.0, 1.0, 1.0), listOf(0.0, 0.0, 1.01, 1.0), listOf(.5, 0.0, .5, 1.0), listOf(0.0, .9, 1.0, .2))) {
            assertThrows(IllegalArgumentException::class.java) { NormalizedRect(values[0], values[1], values[2], values[3]) }
        }
    }
}
