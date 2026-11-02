package com.umbracam.core.recording

/** Capability and layout contracts for Reaction Studio. Capture adapters must prove support before start. */
enum class ReactionLayout { PICTURE_IN_PICTURE, SPLIT }
enum class ReactionMode { SCREEN_ONLY, SCREEN_AND_FRONT_CAMERA }

data class ReactionCapability(
    val screen: Boolean,
    val frontCamera: Boolean,
    val microphone: Boolean,
    val internalAudio: Boolean,
    /** Layouts the adapter can actually composite into the projection output. */
    val composedLayouts: Set<ReactionLayout> = emptySet(),
    /** True only when the adapter can capture and publish a microphone track. */
    val microphoneCapture: Boolean = false,
    /** True only when the adapter has a permitted playback-audio capture path. */
    val internalAudioCapture: Boolean = false,
) {
    fun supports(mode: ReactionMode): Boolean = screen &&
        (mode == ReactionMode.SCREEN_ONLY || frontCamera && composedLayouts.isNotEmpty())

    fun supports(request: ReactionRecordingRequest): Boolean = supports(request.mode) &&
        (request.mode == ReactionMode.SCREEN_ONLY || request.layout in composedLayouts)
}

data class ReactionRecordingRequest(
    val mode: ReactionMode,
    val layout: ReactionLayout = ReactionLayout.PICTURE_IN_PICTURE,
    val microphone: Boolean = false,
    val internalAudio: Boolean = false,
) {
    fun sources(): Set<CaptureSource> = buildSet {
        add(CaptureSource.SCREEN)
        if (mode == ReactionMode.SCREEN_AND_FRONT_CAMERA) add(CaptureSource.FRONT_CAMERA)
        if (microphone) add(CaptureSource.MICROPHONE)
        if (internalAudio) add(CaptureSource.INTERNAL_AUDIO)
    }
}

/** Product truth for the current implementation: screen-only is shipped; composition is capability-gated. */
fun ReactionRecordingRequest.validate(capability: ReactionCapability): Result<Unit> {
    if (!capability.supports(this)) return Result.failure(IllegalStateException("Requested reaction sources or layout are unavailable"))
    if (microphone && (!capability.microphone || !capability.microphoneCapture))
        return Result.failure(IllegalStateException("Microphone capture is unavailable"))
    if (internalAudio && (!capability.internalAudio || !capability.internalAudioCapture))
        return Result.failure(IllegalStateException("Internal audio capture is unavailable"))
    return Result.success(Unit)
}
