package com.umbracam.app.camera

import com.umbracam.core.recording.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/** Private, serialized by the controller's single IO executor. No recording consent is persisted. */
class RecordingSessionJournal(private val directory: File) {
    internal data class Entry(val session: RecordingSession, val dual: DualRecordingSpec?, val diagnostics: List<RecordingErrorCode>)
    var hadUnverifiedOutput: Boolean = false
        private set
    var reconciledDual: DualRecordingSpec? = null
        private set
    var reconciledDiagnostics: List<RecordingErrorCode> = emptyList()
        private set
    // Preserve the original JVM signature as well as the version1 wire format.
    fun write(session: RecordingSession) = write(session, null, emptyList())
    fun write(session: RecordingSession, dual: DualRecordingSpec?, diagnostics: List<RecordingErrorCode> = emptyList()) {
        UUID.fromString(session.id)
        require(session.completedSegments.size <= 10_000 && diagnostics.size <= 16)
        if (dual == null) {
            require(session.request.requiredSources.count { it in setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA) } == 1)
            require(session.request.requiredSources.all { it in setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE) })
        } else require(session.request.requiredSources == dual.requiredSources)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Session metadata unavailable")
        val data = Properties().apply {
            setProperty("version", if (dual == null) "1" else "2")
            dual?.let {
                setProperty("mode", "dual")
                setProperty("front", it.pair.front.id); setProperty("rear", it.pair.rear.id)
                setProperty("layout", it.layout.name); setProperty("shape", it.shape.name)
                setProperty("rotation", it.rotation.name); setProperty("profile", it.profile.name)
                setProperty("microphone", it.microphone.toString())
                setProperty("width", it.outputWidth.toString()); setProperty("height", it.outputHeight.toString())
                setProperty("mirror", "COMPOSED_PREVIEW")
                setProperty("diagnostics", diagnostics.distinct().joinToString(",") { code -> code.name })
            }
            setProperty("id", session.id)
            setProperty("attempt", session.attemptId)
            setProperty("state", session.state.name)
            setProperty("sources", session.request.requiredSources.joinToString(",") { it.name })
            setProperty("segments", session.completedSegments.size.toString())
            session.completedSegments.forEachIndexed { index, segment ->
                setProperty("segment.$index.id", segment.storageId)
                setProperty("segment.$index.ms", segment.durationMillis.toString())
            }
            session.error?.let { setProperty("error", it.code.name) }
        }
        val encoded = ByteArrayOutputStream().also { data.store(it, null) }.toByteArray()
        require(encoded.size <= 1_048_576) { "Session metadata exceeds the bounded journal size" }
        val temporary = File.createTempFile("journal-", ".tmp", directory)
        FileOutputStream(temporary).use { stream -> stream.write(encoded); stream.fd.sync() }
        Files.move(temporary.toPath(), File(directory, "${session.id}.properties").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Never returns RECORDING as live state. Malformed metadata is ignored, never executed/deleted. */
    fun reconcile(files: RecordingFileStore): RecordingSession? {
        hadUnverifiedOutput = false
        reconciledDual = null
        reconciledDiagnostics = emptyList()
        val interrupted = directory.listFiles()?.filter { it.isFile && it.extension == "properties" }
            ?.mapNotNull { readEntry(it).also { snapshot -> if (snapshot == null) hadUnverifiedOutput = true } }?.filter { it.session.state in SingleCameraSessionCoordinator.busyStates }
            ?.sortedByDescending { File(directory, "${it.session.id}.properties").lastModified() }.orEmpty()
        var latest: RecordingSession? = null
        for (entry in interrupted) {
            val old = entry.session
            val segments = old.completedSegments.mapNotNull(files::validateCompleted)
            val restored = old.copy(
                state = if (segments.isEmpty()) RecordingState.FAILED else RecordingState.SAVED,
                completedSegments = segments, availableSources = emptySet(),
                error = RecordingError(RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED, false),
            )
            write(restored, entry.dual, (entry.diagnostics + RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED).distinct().take(16))
            if (latest == null) {
                latest = restored; reconciledDual = entry.dual
                reconciledDiagnostics = (entry.diagnostics + RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED).distinct().take(16)
            }
        }
        if (files.quarantineAbandonedPending() > 0) hadUnverifiedOutput = true
        return latest
    }

    internal fun readEntry(file: File): Entry? = runCatching {
        require(file.length() <= 1_048_576)
        val data = Properties().also { properties -> file.inputStream().use(properties::load) }
        val version = data.getProperty("version")
        require(version in setOf("1", "2"))
        val id = data.getProperty("id").also { UUID.fromString(it); require(file.name == "$it.properties") }
        val sources = data.getProperty("sources").split(',').map { CaptureSource.valueOf(it) }.toSet()
        val dual = if (version == "2") {
            require(data.getProperty("mode") == "dual" && data.getProperty("mirror") == "COMPOSED_PREVIEW")
            DualRecordingSpec(
                DualCameraPair(CameraDescriptor(data.getProperty("front"), CameraFacing.FRONT), CameraDescriptor(data.getProperty("rear"), CameraFacing.REAR)),
                layout = DualLayout.valueOf(data.getProperty("layout")), shape = DualOutputShape.valueOf(data.getProperty("shape")),
                rotation = DualOutputRotation.valueOf(data.getProperty("rotation")),
                microphone = data.getProperty("microphone").also { require(it in setOf("true", "false")) }.toBoolean(),
                profile = DualVideoProfile.valueOf(data.getProperty("profile")),
            ).also {
                require(sources == it.requiredSources)
                require(data.getProperty("width").toInt() == it.outputWidth && data.getProperty("height").toInt() == it.outputHeight)
            }
        } else {
            require(sources.count { it in setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA) } == 1)
            require(sources.all { it in setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE) })
            null
        }
        val diagnostics = if (version == "2") data.getProperty("diagnostics", "").split(',').filter { it.isNotEmpty() }
            .also { require(it.size <= 16) }.map { RecordingErrorCode.valueOf(it) }.distinct() else emptyList()
        val count = data.getProperty("segments").toInt().also { require(it in 0..10_000) }
        val segments = (0 until count).map {
            SavedSegment(data.getProperty("segment.$it.id"), data.getProperty("segment.$it.ms").toLong())
        }
        require(segments.map { it.storageId }.toSet().size == segments.size)
        val state = RecordingState.valueOf(data.getProperty("state"))
        val session = RecordingSession(id, RecordingRequest(sources), state,
            availableSources = if (state == RecordingState.RECORDING) sources else emptySet(),
            completedSegments = segments,
            error = data.getProperty("error")?.let { RecordingError(RecordingErrorCode.valueOf(it), false) },
            attemptId = data.getProperty("attempt"),
        )
        Entry(session, dual, diagnostics)
    }.getOrNull()
}
