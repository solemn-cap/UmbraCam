package com.umbracam.app.camera

import com.umbracam.core.recording.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingSessionJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = RecordingRequest(setOf(CaptureSource.REAR_CAMERA))
    private fun store() = RecordingFileStore(File(temporary.root, "camera"), VideoInspector { InspectedVideo(1000, false) }) { Long.MAX_VALUE }
    private fun journal() = RecordingSessionJournal(File(temporary.root, "camera/sessions"))
    private fun active(segments: List<SavedSegment> = emptyList()) = RecordingSession(UUID.randomUUID().toString(), request, RecordingState.RECORDING, request.requiredSources, segments)

    @Test fun processReconciliationKeepsVerifiedSegmentsAndNeverClaimsActive() {
        val store = store()
        val segment = store.publish(store.reserve().apply { writeText("fixture") }).segment
        val original = active(listOf(segment))
        val journal = journal()
        journal.write(original)
        val restored = journal.reconcile(store)!!
        assertEquals(RecordingState.SAVED, restored.state)
        assertEquals(listOf(segment), restored.completedSegments)
        assertFalse(restored.error!!.recoverable)
        assertTrue(restored.availableSources.isEmpty())
        assertNull(journal.reconcile(store)) // The interruption is reconciled only once.
    }

    @Test fun abandonedOutputMovesToQuarantineWithoutTouchingCompletedVideo() {
        val store = store()
        val completed = store.publish(store.reserve().apply { writeText("completed") }).segment
        val pending = store.reserve().apply { writeText("unfinished") }
        val journal = journal()
        journal.write(active(listOf(completed)))
        journal.reconcile(store)
        assertFalse(pending.exists())
        assertTrue(journal.hadUnverifiedOutput)
        assertEquals("unfinished", File(temporary.root, "camera/quarantine").listFiles()!!.single().readText())
        assertEquals("completed", File(temporary.root, "camera/recordings/${completed.storageId}").readText())
    }

    @Test fun missingCompletedOutputIsNotFabricatedAsSaved() {
        val journal = journal()
        journal.write(active(listOf(SavedSegment("missing.mp4", 1000))))
        val restored = journal.reconcile(store())!!
        assertEquals(RecordingState.FAILED, restored.state)
        assertTrue(restored.completedSegments.isEmpty())
    }

    @Test fun corruptMetadataCannotRestoreActiveCapture() {
        val folder = File(temporary.root, "camera/sessions").apply { mkdirs() }
        File(folder, "${UUID.randomUUID()}.properties").writeText("version=1\nid=not-a-session\nstate=RECORDING")
        val journal = journal()
        assertNull(journal.reconcile(store()))
        assertTrue(journal.hadUnverifiedOutput)
    }

    @Test fun normalCompletedSessionIsNotReportedAsProcessInterruption() {
        val journal = journal()
        val completed = active().copy(state = RecordingState.FAILED, availableSources = emptySet(), error = RecordingError(RecordingErrorCode.FINALIZATION_FAILED, false))
        journal.write(completed)
        assertNull(journal.reconcile(store()))
    }

    @Test fun journalAtomicReplacementRetainsLatestState() {
        val journal = journal()
        val first = active()
        journal.write(first)
        journal.write(first.copy(state = RecordingState.STOPPING, availableSources = emptySet()))
        assertEquals(RecordingState.FAILED, journal.reconcile(store())?.state)
        assertEquals(1, File(temporary.root, "camera/sessions").listFiles()!!.count { it.extension == "properties" })
    }

    @Test fun sosKindAndCompletedIdsSurviveJournalRoundTripAndReconciliation() {
        val store = store()
        val segment = store.publish(store.reserve().apply { writeText("sos-video") }).segment
        val original = active(listOf(segment)).copy(kind = RecordingKind.SOS)
        val journal = journal()
        journal.write(original)
        assertEquals(setOf(segment.storageId), journal.sosRecordingIds())
        val restored = journal.reconcile(store)!!
        assertEquals(RecordingKind.SOS, restored.kind)
        assertEquals(setOf(segment.storageId), journal.sosRecordingIds())
    }

    @Test(expected = IllegalArgumentException::class)
    fun sessionIdentifierCannotEscapeJournalDirectory() {
        journal().write(RecordingSession("../outside", request, RecordingState.PREPARING))
    }
}
