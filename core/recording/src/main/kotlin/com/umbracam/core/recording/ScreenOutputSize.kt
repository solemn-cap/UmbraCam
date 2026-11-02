package com.umbracam.core.recording

/** Encoder candidate only: the Android adapter must independently check codec capabilities. */
data class ScreenOutputSize(val width: Int, val height: Int) {
    init {
        require(width in 2..MAX_EDGE && height in 2..MAX_EDGE)
        require(width % 2 == 0 && height % 2 == 0)
    }
    companion object {
        const val MAX_EDGE = 1280
        fun fromSource(width: Int, height: Int, maxEdge: Int = MAX_EDGE): ScreenOutputSize {
            require(width in 2..16384 && height in 2..16384)
            require(maxEdge in 2..MAX_EDGE)
            val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
            require(width * scale >= 2 && height * scale >= 2) { "Source aspect ratio cannot fit the output bounds" }
            fun aligned(value: Int) = (value * scale).toInt() / 2 * 2
            return ScreenOutputSize(aligned(width), aligned(height))
        }
    }
}
