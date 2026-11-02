package com.umbracam.core.recording

/** Android severity codes, not temperature thresholds. Unknown readings fail closed. */
object CaptureThermalPolicy {
    fun canStart(status: Int?): Boolean = status != null && status in 0..1
    fun mustStop(status: Int?): Boolean = status == null || status !in 0..2
}
