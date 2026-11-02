package com.umbracam.app.camera

import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Test

class ConcurrentCameraDetectorTest {
    private class Source(override val apiLevel: Int = 30) : CameraInventorySource {
        var reads = 0
        var error: Exception? = null
        var groups: Set<Set<String>> = emptySet()
        override fun cameraIds(): List<String> { reads++; error?.let { throw it }; return listOf("0", "1") }
        override fun facing(id: String) = if (id == "0") CameraFacing.REAR else CameraFacing.FRONT
        override fun concurrentGroups() = groups
    }

    @Test fun android10DoesNotCallUnavailableMetadataApis() {
        val source = Source(29)
        assertEquals(ConcurrentCameraCapabilities.UnsupportedPlatform, ConcurrentCameraDetector(source).read())
        assertEquals(0, source.reads)
    }

    @Test fun noAdvertisedGroupsProducesSingleCameraFallback() {
        assertFalse((ConcurrentCameraDetector(Source()).read() as ConcurrentCameraCapabilities.Report).advertisesFrontAndRear)
    }

    @Test fun standardMetadataGroupsArePassedThrough() {
        val source = Source().apply { groups = setOf(setOf("0", "1")) }
        assertTrue((ConcurrentCameraDetector(source).read() as ConcurrentCameraCapabilities.Report).advertisesFrontAndRear)
    }

    @Test fun permissionDenialIsDifferentFromUnsupportedHardware() {
        val source = Source().apply { error = SecurityException() }
        assertEquals(ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.ACCESS_DENIED), ConcurrentCameraDetector(source).read())
    }

    @Test fun serviceFailureIsRetryableReadFailure() {
        val source = Source().apply { error = IllegalStateException() }
        assertEquals(ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.CAMERA_SERVICE), ConcurrentCameraDetector(source).read())
    }

    @Test fun changingCameraInventoryIsNotReportedAsSupported() {
        val source = Source().apply { groups = setOf(setOf("0", "gone")) }
        assertEquals(ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.INCONSISTENT_SNAPSHOT), ConcurrentCameraDetector(source).read())
    }
}
