package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class ScreenOutputSizeTest {
    @Test fun smallerCodecCandidatesPreserveAspectAndNeverUpscale() {
        assertEquals(ScreenOutputSize(540, 960), ScreenOutputSize.fromSource(720, 1280, 960))
        assertEquals(ScreenOutputSize(960, 540), ScreenOutputSize.fromSource(1280, 720, 960))
        assertEquals(ScreenOutputSize(270, 480), ScreenOutputSize.fromSource(720, 1280, 480))
        assertEquals(ScreenOutputSize(200, 200), ScreenOutputSize.fromSource(200, 200, 480))
    }
    @Test fun candidateLimitCannotExceedTheProductCeiling() {
        for (limit in listOf(0, 1, 1281, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { ScreenOutputSize.fromSource(720, 1280, limit) }
    }
    @Test fun portraitAndLandscapeKeepTheirAspectWithoutUpscaling() {
        assertEquals(ScreenOutputSize(576, 1280), ScreenOutputSize.fromSource(1080, 2400))
        assertEquals(ScreenOutputSize(1280, 576), ScreenOutputSize.fromSource(2400, 1080))
        assertEquals(ScreenOutputSize(720, 1280), ScreenOutputSize.fromSource(720, 1280))
        assertEquals(ScreenOutputSize(200, 200), ScreenOutputSize.fromSource(200, 200))
    }
    @Test fun tabletAndOddDimensionsStayEvenAndBounded() {
        assertEquals(ScreenOutputSize(800, 1280), ScreenOutputSize.fromSource(1600, 2560))
        assertEquals(ScreenOutputSize(720, 1280), ScreenOutputSize.fromSource(721, 1281))
        assertEquals(ScreenOutputSize(100, 202), ScreenOutputSize.fromSource(101, 203))
    }
    @Test fun tinyAndVeryLargeSourcesRemainValidCandidates() {
        assertEquals(ScreenOutputSize(2, 2), ScreenOutputSize.fromSource(2, 2))
        assertEquals(ScreenOutputSize(1280, 1280), ScreenOutputSize.fromSource(16384, 16384))
    }
    @Test fun invalidOrUnboundedSourceSizesAreRejected() {
        for ((w, h) in listOf(0 to 720, 720 to -1, 1 to 1, 16385 to 1280, Int.MAX_VALUE to 720)) {
            assertThrows(IllegalArgumentException::class.java) { ScreenOutputSize.fromSource(w, h) }
        }
    }
    @Test fun unalignedOrOversizeOutputCannotBeConstructed() {
        for ((w, h) in listOf(0 to 2, 3 to 4, 4 to 3, 1282 to 720)) {
            assertThrows(IllegalArgumentException::class.java) { ScreenOutputSize(w, h) }
        }
    }
    @Test fun extremeAspectRatioIsRejectedRatherThanDistortedByMinimumSize() {
        assertThrows(IllegalArgumentException::class.java) { ScreenOutputSize.fromSource(2, 16384) }
        assertThrows(IllegalArgumentException::class.java) { ScreenOutputSize.fromSource(16384, 2) }
    }
}
