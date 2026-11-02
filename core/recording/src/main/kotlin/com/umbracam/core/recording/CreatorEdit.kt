package com.umbracam.core.recording

/** Serializable, validated edit choices for a non-destructive creator export. */
enum class CreatorFraming { ORIGINAL, SQUARE, VERTICAL, LANDSCAPE }
enum class CreatorOverlayPosition { TOP, CENTER, BOTTOM }
enum class CreatorExportProfile(val targetVideoBitrate: Int) {
    COMPACT(2_500_000), BALANCED(5_000_000), HIGH_DETAIL(8_000_000)
}

data class CreatorEdit(
    val startMs: Long = 0,
    val endMs: Long? = null,
    val framing: CreatorFraming = CreatorFraming.ORIGINAL,
    val branding: String = "",
    val audioGain: Float = 1f,
    val overlayPosition: CreatorOverlayPosition = CreatorOverlayPosition.BOTTOM,
    val perTrackAudioGains: List<Float> = emptyList(),
    val exportProfile: CreatorExportProfile = CreatorExportProfile.BALANCED,
) {
    fun validated(durationMs: Long?): CreatorEdit {
        require(startMs >= 0)
        require(endMs == null || endMs > startMs)
        require(durationMs == null || durationMs > 0)
        require(durationMs == null || startMs < durationMs)
        require(durationMs == null || endMs == null || endMs <= durationMs)
        require(audioGain.isFinite() && audioGain in 0f..2f)
        require(perTrackAudioGains.size <= 8 && perTrackAudioGains.all { it.isFinite() && it in 0f..2f })
        return copy(branding = branding.trim().take(48))
    }
}
