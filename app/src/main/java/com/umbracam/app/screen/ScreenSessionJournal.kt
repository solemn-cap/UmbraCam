package com.umbracam.app.screen

import com.umbracam.app.camera.RecordingFileStore
import com.umbracam.core.recording.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/** Screen namespace only. No Intent, projection or consent correlation is serialized. */
internal class ScreenSessionJournal(private val directory: File) {
    var warning = false
        private set
    fun write(session: RecordingSession) {
        UUID.fromString(session.id)
        require(isSupportedRequest(session.request.requiredSources))
        require(session.completedSegments.size <= 100)
        check(directory.isDirectory || directory.mkdirs())
        val data = Properties().apply {
            setProperty("version", "screen-3"); setProperty("id", session.id)
            setProperty("sources", session.request.requiredSources.sortedBy { it.ordinal }.joinToString(",") { it.name })
            setProperty("state", session.state.name)
            setProperty("count", session.completedSegments.size.toString())
            session.error?.let { setProperty("error", it.code.name) }
            session.completedSegments.forEachIndexed { i, segment ->
                require(segment.storageId.matches(Regex("[a-fA-F0-9-]{36}\\.mp4")))
                UUID.fromString(segment.storageId.removeSuffix(".mp4"))
                setProperty("file.$i", segment.storageId); setProperty("duration.$i", segment.durationMillis.toString())
            }
        }
        val temporary = File.createTempFile("screen-", ".tmp", directory)
        FileOutputStream(temporary).use { data.store(it, null); it.fd.sync() }
        Files.move(temporary.toPath(), File(directory, "${session.id}.properties").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun reconcile(files: RecordingFileStore): RecordingSession? {
        warning = false
        var latest: RecordingSession? = null
        directory.listFiles().orEmpty().filter { it.extension == "properties" && it.isFile }
            .sortedByDescending { it.lastModified() }.forEach { file ->
                val old = read(file)
                if (old == null) warning = true
                else if (old.state in SingleCameraSessionCoordinator.busyStates) {
                    val segments = old.completedSegments.mapNotNull(files::validateCompleted)
                    val terminal = old.copy(state = if (segments.isEmpty()) RecordingState.FAILED else RecordingState.SAVED,
                        availableSources = emptySet(), completedSegments = segments,
                        error = RecordingError(RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED, false))
                    write(terminal)
                    if (latest == null) latest = terminal
                }
            }
        if (files.quarantineAbandonedPending() > 0) warning = true
        return latest
    }

    internal fun read(file: File): RecordingSession? = runCatching {
        require(file.length() <= 65536)
        val data = Properties().also { file.inputStream().use(it::load) }
        val version = data.getProperty("version")
        require(version == "screen-1" || version == "screen-2" || version == "screen-3")
        val id = data.getProperty("id").also { UUID.fromString(it); require(file.name == "$it.properties") }
        val sources = if (version == "screen-1") setOf(CaptureSource.SCREEN) else
            data.getProperty("sources").split(',').map(CaptureSource::valueOf).toSet()
        require(isSupportedRequest(sources))
        val count = data.getProperty("count").toInt().also { require(it in 0..100) }
        val segments = (0 until count).map { i ->
            val name = data.getProperty("file.$i")
            require(name == "${UUID.fromString(name.removeSuffix(".mp4"))}.mp4")
            SavedSegment(name, data.getProperty("duration.$i").toLong())
        }
        require(segments.distinctBy { it.storageId }.size == segments.size)
        val state = RecordingState.valueOf(data.getProperty("state"))
        RecordingSession(id, RecordingRequest(sources), state,
            availableSources = if (state == RecordingState.RECORDING) sources else emptySet(),
            completedSegments = segments,
            error = data.getProperty("error")?.let { RecordingError(RecordingErrorCode.valueOf(it), false) })
    }.getOrNull()

    private fun isSupportedRequest(sources: Set<CaptureSource>) = CaptureSource.SCREEN in sources &&
        sources.all { it in setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA, CaptureSource.MICROPHONE, CaptureSource.INTERNAL_AUDIO) }
}
