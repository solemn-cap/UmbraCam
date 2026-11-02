package com.umbracam.core.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionRecordingTest {
    private val screenOnly = ReactionCapability(screen = true, frontCamera = false, microphone = true, internalAudio = false)
    private val cameraHardwareOnly = ReactionCapability(screen = true, frontCamera = true, microphone = true, internalAudio = false)
    private val composed = cameraHardwareOnly.copy(composedLayouts = ReactionLayout.entries.toSet())

    @Test fun screenOnlyRequiresOnlyScreenAndOptionalMic() {
        val request = ReactionRecordingRequest(ReactionMode.SCREEN_ONLY)
        assertTrue(request.validate(screenOnly).isSuccess)
        assertEquals(setOf(CaptureSource.SCREEN), request.sources())
    }

    @Test fun combinedModeFailsWhenFrontCameraIsUnavailable() {
        val request = ReactionRecordingRequest(ReactionMode.SCREEN_AND_FRONT_CAMERA)
        assertTrue(request.validate(screenOnly).isFailure)
        assertEquals(setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA), request.sources())
    }

    @Test fun frontCameraHardwareDoesNotImplyCompositeCaptureSupport() {
        val request = ReactionRecordingRequest(ReactionMode.SCREEN_AND_FRONT_CAMERA)
        assertTrue(cameraHardwareOnly.frontCamera)
        assertTrue(request.validate(cameraHardwareOnly).isFailure)
        assertTrue(request.validate(composed).isSuccess)
        assertTrue(request.copy(layout = ReactionLayout.SPLIT).validate(composed).isSuccess)
        assertTrue(request.copy(layout = ReactionLayout.SPLIT).validate(
            composed.copy(composedLayouts = setOf(ReactionLayout.PICTURE_IN_PICTURE))).isFailure)
    }

    @Test fun microphoneHardwareDoesNotImplyMicrophoneTrackSupport() {
        val request = ReactionRecordingRequest(ReactionMode.SCREEN_ONLY, microphone = true)
        assertTrue(screenOnly.microphone)
        assertTrue(request.validate(screenOnly).isFailure)
        assertTrue(request.validate(screenOnly.copy(microphoneCapture = true)).isSuccess)
    }

    @Test fun internalAudioRequiresAnExplicitSupportedTrack() {
        val request = ReactionRecordingRequest(ReactionMode.SCREEN_ONLY, internalAudio = true)
        assertTrue(request.validate(screenOnly).isFailure)
        assertTrue(request.validate(screenOnly.copy(internalAudio = true)).isFailure)
        assertTrue(request.validate(screenOnly.copy(internalAudio = true, internalAudioCapture = true)).isSuccess)
    }
}
