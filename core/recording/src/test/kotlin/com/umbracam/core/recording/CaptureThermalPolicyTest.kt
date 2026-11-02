package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class CaptureThermalPolicyTest {
    @Test fun coolOrLightAllowsExplicitStart() {
        for (status in 0..1) { assertTrue(CaptureThermalPolicy.canStart(status)); assertFalse(CaptureThermalPolicy.mustStop(status)) }
    }
    @Test fun moderateBlocksNewCaptureButDoesNotAbruptlyStopAnExistingAttempt() {
        assertFalse(CaptureThermalPolicy.canStart(2)); assertFalse(CaptureThermalPolicy.mustStop(2))
    }
    @Test fun severeThroughShutdownStopsAndBlocksCapture() {
        for (status in 3..6) { assertFalse(CaptureThermalPolicy.canStart(status)); assertTrue(CaptureThermalPolicy.mustStop(status)) }
    }
    @Test fun unavailableAndInvalidStatusFailClosed() {
        for (status in listOf(null, -1, 7, Int.MAX_VALUE)) {
            assertFalse(CaptureThermalPolicy.canStart(status)); assertTrue(CaptureThermalPolicy.mustStop(status))
        }
    }
}
