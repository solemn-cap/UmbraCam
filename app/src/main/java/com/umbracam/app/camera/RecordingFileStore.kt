package com.umbracam.app.camera

import com.umbracam.core.recording.SavedSegment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import com.umbracam.app.vault.EncryptedFileCodec

data class InspectedVideo(val durationMillis: Long, val hasAudio: Boolean, val width: Int = 0, val height: Int = 0, val frameRate: Int? = null)
fun interface VideoInspector { fun inspect(file: File): InspectedVideo }
data class PublishedVideo(val segment: SavedSegment, val hasAudio: Boolean)
data class VaultMigrationReport(val migrated: Int, val failed: Int)

/** App-private, same-filesystem staging. Caller runs disk/metadata operations off the UI thread. */
class RecordingFileStore(
    private val root: File,
    private val inspector: VideoInspector,
    private val availableBytes: () -> Long = { root.usableSpace },
    private val vaultCodec: EncryptedFileCodec? = null,
) {
    private val pending = File(root, "pending")
    private val completed = File(root, "recordings")
    private val quarantine = File(root, "quarantine")

    fun reserve(): File {
        ensureDirectory(pending)
        if (availableBytes() < MINIMUM_FREE_BYTES) throw InsufficientRecordingSpace()
        vaultCodec?.ensureKeyAvailable()
        return File.createTempFile("capture-", ".mp4", pending)
    }

    fun publish(file: File): PublishedVideo {
        requirePending(file)
        if (!file.isFile || file.length() == 0L) throw IOException("Empty recording")
        val video = inspector.inspect(file)
        if (video.durationMillis <= 0) throw IOException("No usable video duration")
        ensureDirectory(completed)
        val destination = File(completed, "${UUID.randomUUID()}.mp4")
        val codec = vaultCodec
        if (codec == null) {
            Files.move(file.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } else {
            val encrypted = File.createTempFile(".vault-", ".tmp", completed)
            val verified = File.createTempFile("verify-", ".mp4", pending)
            try {
                codec.encrypt(file, encrypted)
                codec.decrypt(encrypted, verified)
                val decryptedVideo = inspector.inspect(verified)
                if (decryptedVideo != video) throw IOException("Encrypted recording verification changed its media metadata")
                Files.move(encrypted.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!file.delete()) {
                    destination.delete()
                    throw IOException("Plaintext staging file could not be removed")
                }
            } finally {
                encrypted.delete()
                verified.delete()
            }
        }
        return PublishedVideo(SavedSegment(destination.name, video.durationMillis), video.hasAudio)
    }

    fun quarantine(file: File) {
        requirePending(file)
        if (!file.exists()) return
        ensureDirectory(quarantine)
        val codec = vaultCodec
        if (codec == null || codec.isEncrypted(file)) {
            Files.move(file.toPath(), File(quarantine, "${UUID.randomUUID()}.mp4").toPath(), StandardCopyOption.ATOMIC_MOVE)
        } else {
            val encrypted = File.createTempFile(".vault-", ".tmp", quarantine)
            try {
                codec.encrypt(file, encrypted)
                val destination = File(quarantine, "${UUID.randomUUID()}.mp4")
                Files.move(encrypted.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!file.delete()) { destination.delete(); throw IOException("Plaintext quarantine source could not be removed") }
            } finally { encrypted.delete() }
        }
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
        val codec = vaultCodec
        if (codec == null || !codec.isEncrypted(file)) return runCatching {
            inspector.inspect(file).takeIf { it.durationMillis > 0 }?.let { segment.copy(durationMillis = it.durationMillis) }
        }.getOrNull()
        val decrypted = runCatching { File.createTempFile("verify-", ".mp4", pending) }.getOrNull() ?: return null
        return try {
            codec.decrypt(file, decrypted)
            inspector.inspect(decrypted).takeIf { it.durationMillis > 0 }?.let { segment.copy(durationMillis = it.durationMillis) }
        } catch (_: Exception) { null } finally { decrypted.delete() }
    }

    /** Encrypts legacy completed MP4s in place only after a decrypt-and-inspect round trip succeeds. */
    fun migratePlaintextRecordings(): VaultMigrationReport {
        val codec = vaultCodec ?: return VaultMigrationReport(0, 0)
        ensureDirectory(completed); ensureDirectory(pending)
        var migrated = 0
        var failed = 0
        completed.listFiles().orEmpty().filter { it.isFile && it.extension == "mp4" && !codec.isEncrypted(it) }.forEach { original ->
            val encrypted = File.createTempFile(".vault-migrate-", ".tmp", completed)
            val verified = File.createTempFile("verify-", ".mp4", pending)
            try {
                val before = inspector.inspect(original)
                codec.encrypt(original, encrypted)
                codec.decrypt(encrypted, verified)
                check(inspector.inspect(verified) == before) { "Vault migration verification failed" }
                Files.move(encrypted.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                migrated++
            } catch (_: Exception) { failed++ }
            finally { encrypted.delete(); verified.delete() }
        }
        return VaultMigrationReport(migrated, failed)
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
