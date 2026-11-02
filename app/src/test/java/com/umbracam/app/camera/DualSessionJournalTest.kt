package com.umbracam.app.camera

import com.umbracam.core.recording.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.UUID

class DualSessionJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private val spec = DualRecordingSpec(DualCameraPair(CameraDescriptor("front:1", CameraFacing.FRONT), CameraDescriptor("rear:0", CameraFacing.REAR)), microphone = true)
    private val directory get() = File(temporary.root, "camera/sessions")
    private fun journal() = RecordingSessionJournal(directory)
    private fun store() = RecordingFileStore(File(temporary.root, "camera"), VideoInspector { InspectedVideo(1_000, true) }) { Long.MAX_VALUE }
    private fun session() = RecordingSession(UUID.randomUUID().toString(), RecordingRequest(spec.requiredSources), RecordingState.PREPARING)
    private fun file(session: RecordingSession) = File(directory, "${session.id}.properties")
    private fun mutate(file: File, key: String, value: String) {
        val data = Properties().also { file.inputStream().use(it::load) }
        data.setProperty(key, value)
        file.outputStream().use { data.store(it, null) }
    }

    @Test fun dualDescriptorAndMultipleDiagnosticsRoundTrip() {
        val journal = journal(); val session = session()
        val diagnostics = listOf(RecordingErrorCode.SOURCE_UNAVAILABLE, RecordingErrorCode.PERSISTENCE_FAILED)
        journal.write(session, spec, diagnostics)
        val read = journal.readEntry(file(session))!!
        assertEquals(session, read.session)
        assertEquals(spec, read.dual)
        assertEquals(diagnostics, read.diagnostics)
        assertTrue(file(session).readText().contains("version=2"))
    }

    @Test fun singleWriterRetainsVersionOneAndCannotSmuggleDualSources() {
        val journal = journal(); val dual = session()
        assertThrows(IllegalArgumentException::class.java) { journal.write(dual) }
        val single = dual.copy(request = RecordingRequest(setOf(CaptureSource.REAR_CAMERA)))
        journal.write(single)
        assertTrue(file(single).readText().contains("version=1"))
        assertNull(journal.readEntry(file(single))!!.dual)
    }

    @Test fun descriptorCannotDisagreeWithRequiredMicrophone() {
        val journal = journal()
        assertThrows(IllegalArgumentException::class.java) { journal.write(session(), spec.copy(microphone = false)) }
    }

    @Test fun dualReconciliationPreservesDescriptorMediaAndDiagnosticsWithoutAuthorization() {
        val store = store(); val journal = journal()
        val completed = store.publish(store.reserve().apply { writeText("prior completed bytes") }).segment
        val active = session().copy(state = RecordingState.RECORDING, availableSources = spec.requiredSources, completedSegments = listOf(completed))
        journal.write(active, spec, listOf(RecordingErrorCode.SOURCE_UNAVAILABLE))
        val restored = journal.reconcile(store)!!
        assertEquals(RecordingState.SAVED, restored.state)
        assertFalse(restored.error!!.recoverable)
        assertTrue(restored.availableSources.isEmpty())
        assertEquals(spec, journal.reconciledDual)
        assertEquals(listOf(RecordingErrorCode.SOURCE_UNAVAILABLE, RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED), journal.reconciledDiagnostics)
        assertEquals(spec, journal.readEntry(file(restored))!!.dual)
        assertEquals("prior completed bytes", File(temporary.root, "camera/recordings/${completed.storageId}").readText())
        assertNull(journal.reconcile(store))
    }

    @Test fun malformedDualSettingsWarnWithoutDeletingFiles() {
        for ((key, value) in listOf("version" to "999", "mode" to "single", "front" to spec.pair.rear.id,
            "layout" to "unknown", "shape" to "unknown", "rotation" to "unknown", "profile" to "UHD",
            "width" to "999999", "height" to "0", "microphone" to "perhaps", "mirror" to "independent",
            "sources" to "REAR_CAMERA", "diagnostics" to "unknown")) {
            val journal = journal(); val active = session()
            journal.write(active, spec)
            mutate(file(active), key, value)
            assertNull("Must reject $key=$value", journal.readEntry(file(active)))
            assertNull(journal.reconcile(store()))
            assertTrue(journal.hadUnverifiedOutput)
            assertTrue(file(active).exists())
        }
    }

    @Test fun legacyV1DualSourceSetIsNotInferredAsDualMode() {
        val journal = journal(); val active = session()
        journal.write(active, spec)
        mutate(file(active), "version", "1")
        assertNull(journal.readEntry(file(active)))
    }
}
