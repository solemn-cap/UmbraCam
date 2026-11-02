package com.umbracam.app.screen

import com.umbracam.app.camera.*
import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.UUID

class ScreenSessionJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun session(state: RecordingState = RecordingState.PREPARING, segments: List<SavedSegment> = emptyList()) =
        RecordingSession(UUID.randomUUID().toString(), RecordingRequest(setOf(CaptureSource.SCREEN)), state,
            if (state == RecordingState.RECORDING) setOf(CaptureSource.SCREEN) else emptySet(), segments)
    private fun store(root: File) = RecordingFileStore(root, VideoInspector { InspectedVideo(1234, false) })
    @Test fun roundTripContainsNoReusableAuthorization() {
        val root = temporary.newFolder(); val journal = ScreenSessionJournal(root); val original = session(RecordingState.RECORDING)
        journal.write(original)
        val file = File(root, "${original.id}.properties")
        assertEquals(original, journal.read(file))
        val properties = Properties().also { file.inputStream().use(it::load) }
        assertTrue(properties.stringPropertyNames().none { it in setOf("intent", "projection", "consent", "token", "attempt") })
    }
    @Test fun combinedSourcesRoundTripAndLegacyScreenJournalRemainsReadable() {
        val root = temporary.newFolder(); val journal = ScreenSessionJournal(root)
        val sources = setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA)
        val combined = RecordingSession(UUID.randomUUID().toString(), RecordingRequest(sources), RecordingState.RECORDING,
            sources, listOf(SavedSegment("${UUID.randomUUID()}.mp4", 900)))
        journal.write(combined)
        assertEquals(combined, journal.read(File(root, "${combined.id}.properties")))

        val legacy = UUID.randomUUID().toString()
        File(root, "$legacy.properties").writeText("version=screen-1\nid=$legacy\nstate=PREPARING\ncount=0\n")
        assertEquals(setOf(CaptureSource.SCREEN), journal.read(File(root, "$legacy.properties"))!!.request.requiredSources)
    }

    @Test fun audioSourceJournalRoundTripsAndKeepsScreenTwoReadable() {
        val root = temporary.newFolder(); val journal = ScreenSessionJournal(root)
        val sources = setOf(CaptureSource.SCREEN, CaptureSource.MICROPHONE, CaptureSource.INTERNAL_AUDIO)
        val audio = RecordingSession(UUID.randomUUID().toString(), RecordingRequest(sources), RecordingState.RECORDING,
            sources, listOf(SavedSegment("${UUID.randomUUID()}.mp4", 900)))
        journal.write(audio)
        assertEquals(audio, journal.read(File(root, "${audio.id}.properties")))

        val old = UUID.randomUUID().toString()
        File(root, "$old.properties").writeText("version=screen-2\nid=$old\nsources=SCREEN,FRONT_CAMERA\nstate=RECORDING\ncount=0\n")
        assertEquals(setOf(CaptureSource.SCREEN, CaptureSource.FRONT_CAMERA), journal.read(File(root, "$old.properties"))?.request?.requiredSources)
    }
    @Test fun reconciliationQuarantinesPendingAndNeverRestoresRecording() {
        val root = temporary.newFolder(); val files = store(root); val pending = files.reserve().apply { writeText("unverified") }
        val journal = ScreenSessionJournal(File(root, "sessions")); journal.write(session(RecordingState.RECORDING))
        val restored = journal.reconcile(files)!!
        assertEquals(RecordingState.FAILED, restored.state)
        assertEquals(RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED, restored.error!!.code)
        assertTrue(restored.availableSources.isEmpty()); assertFalse(restored.error!!.recoverable)
        assertFalse(pending.exists()); assertEquals(1, File(root, "quarantine").listFiles()!!.size)
    }
    @Test fun preservedCompletedSegmentsAreValidatedWithoutTouchingBytes() {
        val root = temporary.newFolder(); val name = "${UUID.randomUUID()}.mp4"
        val video = File(File(root, "recordings").apply { mkdirs() }, name).apply { writeText("previous video") }
        val journal = ScreenSessionJournal(File(root, "sessions"))
        journal.write(session(RecordingState.RECORDING, listOf(SavedSegment(name, 100))))
        val restored = journal.reconcile(store(root))!!
        assertEquals(RecordingState.SAVED, restored.state); assertEquals(1234L, restored.completedSegments.single().durationMillis)
        assertEquals("previous video", video.readText())
    }
    @Test fun corruptOversizeAndWrongNamespaceRecordsWarnRatherThanExecute() {
        val root = temporary.newFolder(); val dir = File(root, "sessions").apply { mkdirs() }
        File(dir, "bad.properties").writeText("version=1\nstate=RECORDING\n")
        File(dir, "huge.properties").writeText("x".repeat(65537))
        val journal = ScreenSessionJournal(dir)
        assertNull(journal.reconcile(store(root))); assertTrue(journal.warning)
    }
    @Test fun pathTraversalAndFilenameMismatchAreRejected() {
        val root = temporary.newFolder(); val original = session(); val journal = ScreenSessionJournal(root)
        journal.write(original)
        val file = File(root, "${original.id}.properties")
        val properties = Properties().also { file.inputStream().use(it::load) }
        properties.setProperty("count", "1"); properties.setProperty("file.0", "../escape.mp4"); properties.setProperty("duration.0", "10")
        file.outputStream().use { properties.store(it, null) }; assertNull(journal.read(file))
        journal.write(original)
        val other = File(root, "${UUID.randomUUID()}.properties"); file.copyTo(other)
        assertNull(journal.read(other))
    }
    @Test fun terminalSavedJournalDoesNotBecomeAnInterruptedSessionOnNormalLaunch() {
        val root = temporary.newFolder(); val journal = ScreenSessionJournal(File(root, "sessions"))
        journal.write(session(RecordingState.SAVED, listOf(SavedSegment("${UUID.randomUUID()}.mp4", 100))))
        assertNull(journal.reconcile(store(root))); assertFalse(journal.warning)
    }
}
