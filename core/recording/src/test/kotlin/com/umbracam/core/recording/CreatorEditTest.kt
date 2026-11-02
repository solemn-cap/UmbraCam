package com.umbracam.core.recording

import org.junit.Assert.*
import org.junit.Test

class CreatorEditTest {
    @Test fun normalizesBrandingAndKeepsSupportedValues() {
        val edit = CreatorEdit(1_000, 9_000, CreatorFraming.VERTICAL, "  Umbra  ", 1.5f,
            CreatorOverlayPosition.TOP, listOf(0.5f, 1.5f)).validated(10_000)
        assertEquals("Umbra", edit.branding)
        assertEquals(CreatorFraming.VERTICAL, edit.framing)
        assertEquals(1.5f, edit.audioGain)
        assertEquals(CreatorOverlayPosition.TOP, edit.overlayPosition)
        assertEquals(listOf(0.5f, 1.5f), edit.perTrackAudioGains)
        assertEquals(CreatorExportProfile.BALANCED, edit.exportProfile)
    }

    @Test fun rejectsInvalidTrimAndGain() {
        assertThrows(IllegalArgumentException::class.java) { CreatorEdit(startMs = 10, endMs = 10).validated(100) }
        assertThrows(IllegalArgumentException::class.java) { CreatorEdit(audioGain = Float.NaN).validated(100) }
        assertThrows(IllegalArgumentException::class.java) { CreatorEdit(perTrackAudioGains = listOf(1f, Float.POSITIVE_INFINITY)).validated(100) }
        assertThrows(IllegalArgumentException::class.java) { CreatorEdit(startMs = 100).validated(100) }
    }

    @Test fun exportProfilesHaveDistinctBoundedBitrateTargets() {
        assertTrue(CreatorExportProfile.COMPACT.targetVideoBitrate < CreatorExportProfile.BALANCED.targetVideoBitrate)
        assertTrue(CreatorExportProfile.BALANCED.targetVideoBitrate < CreatorExportProfile.HIGH_DETAIL.targetVideoBitrate)
    }
}
