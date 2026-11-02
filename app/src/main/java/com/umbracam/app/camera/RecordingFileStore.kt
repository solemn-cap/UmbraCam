package com.umbracam.app.camera

import com.umbracam.core.recording.SavedSegment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class InspectedVideo(val durationMillis: Long, val hasAudio: Boolean)
fun interface VideoInspector { fun inspect(file: File): InspectedVideo }
data class PublishedVideo(val segment: SavedSegment, val hasAudio: Boolean)

/** App-private, same-filesystem staging. Caller runs disk/metadata operations off the UI thread. */
class RecordingFileStore(
    private val root: File,
    private val inspector: VideoInspector,
    private val availableBytes: () -> Long = { root.usableSpace },
) {
    private val pending = File(root, "pending")
    private val completed = File(root, "recordings")
    private val quarantine = File(root, "quarantine")

    fun reserve(): File {
        ensureDirectory(pending)
        if (availableBytes() < MINIMUM_FREE_BYTES) throw InsufficientRecordingSpace()
        return File.createTempFile("capture-", ".mp4", pending)
    }

    fun publish(file: File): PublishedVideo {
        requirePending(file)
        if (!file.isFile || file.length() == 0L) throw IOException("Empty recording")
        val video = inspector.inspect(file)
        if (video.durationMillis <= 0) throw IOException("No usable video duration")
        ensureDirectory(completed)
        val destination = File(completed, "${UUID.randomUUID()}.mp4")
        // No non-atomic copy fallback: a failed move must never expose partial completed output.
        Files.move(file.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return PublishedVideo(SavedSegment(destination.name, video.durationMillis), video.hasAudio)
    }

    fun quarantine(file: File) {
        requirePending(file)
        if (!file.exists()) return
        ensureDirectory(quarantine)
        Files.move(file.toPath(), File(quarantine, "${UUID.randomUUID()}.mp4").toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    fun quarantineAbandonedPending(): Int {
        val abandoned = pending.listFiles()?.filter { it.isFile }.orEmpty()
        abandoned.forEach { quarantine(it) }
        return abandoned.size
    }

    fun validateCompleted(segment: SavedSegment): SavedSegment? {
        if (File(segment.storageId).name != segment.storageId) return null
        val file = File(completed, segment.storageId)
        if (file.canonicalFile.parentFile != completed.canonicalFile || !file.isFile || file.length() == 0L) return null
        return runCatching {
            inspector.inspect(file).takeIf { it.durationMillis > 0 }?.let { segment.copy(durationMillis = it.durationMillis) }
        }.getOrNull()
    }

    private fun requirePending(file: File) {
        require(file.canonicalFile.parentFile == pending.canonicalFile) { "Output is outside the pending directory" }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Recording directory unavailable")
    }

    companion object { const val MINIMUM_FREE_BYTES = 32L * 1024 * 1024 }
}

class InsufficientRecordingSpace : IOException("Insufficient recording space")
