package com.umbracam.app.library

import com.umbracam.app.camera.InspectedVideo
import com.umbracam.app.camera.VideoInspector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class RecordingCatalogTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun video(root: File, directory: String = "recordings", bytes: String = "video"): File =
        File(File(root, directory).apply { mkdirs() }, "${UUID.randomUUID()}.mp4").apply { writeText(bytes) }
    private fun catalog(camera: File, screen: File = camera) = RecordingCatalog(mapOf("camera" to camera, "screen" to screen), VideoInspector { InspectedVideo(1234, false) })
    @Test fun listsBothNamespacesLatestFirstAndExcludesUnpublishedFiles() {
        val camera = temporary.newFolder("camera"); val screen = temporary.newFolder("screen")
        val old = video(camera).apply { setLastModified(1_000) }
        val fresh = video(screen).apply { setLastModified(2_000) }
        video(camera, "pending"); video(screen, "quarantine"); video(camera, bytes = "")
        File(File(camera, "recordings"), "other.mp4").writeText("not a published id")
        val entries = catalog(camera, screen).list()
        assertEquals(listOf("screen/${fresh.name}", "camera/${old.name}"), entries.map { it.key })
        assertTrue(entries.all { it.readable && it.durationMillis == 1234L })
    }
    @Test fun invalidOrEscapingKeysNeverResolve() {
        val root = temporary.newFolder(); val good = video(root)
        val repository = catalog(root)
        for (key in listOf("../${good.name}", "camera/../${good.name}", "pending/${good.name}", "camera/not-a-uuid.mp4", "camera/${good.absolutePath}")) {
            assertThrows(Exception::class.java) { repository.resolve(key) }
        }
    }
    @Test fun unreadableCompletedVideoStillAllowsUserDirectedRecoveryExport() {
        val root = temporary.newFolder(); val file = video(root)
        val repository = RecordingCatalog(mapOf("camera" to root), VideoInspector { error("cannot decode") })
        val entry = repository.list().single()
        assertFalse(entry.readable); assertNull(entry.durationMillis)
        assertEquals(file.readText(), repository.input(entry.key).bufferedReader().use { it.readText() })
    }
    @Test fun catalogLabelsJournaledSosRecordings() {
        val root = temporary.newFolder(); val file = video(root)
        val repository = RecordingCatalog(mapOf("camera" to root), VideoInspector { InspectedVideo(1234, false) }, { setOf(file.name) })
        assertEquals("sos", repository.list().single().kind)
    }
    @Test fun catalogExposesTheInspectedAudioTrackCount() {
        val root = temporary.newFolder(); video(root)
        val repository = RecordingCatalog(mapOf("camera" to root), VideoInspector { InspectedVideo(1234, true, audioTrackCount = 2) })
        assertEquals(2, repository.list().single().audioTrackCount)
    }
    @Test fun deletionRemovesOnlySelectedCompletedFile() {
        val root = temporary.newFolder(); val selected = video(root); val preserved = video(root)
        val pending = video(root, "pending")
        catalog(root).delete("camera/${selected.name}")
        assertFalse(selected.exists()); assertEquals("video", preserved.readText()); assertEquals("video", pending.readText())
    }
    @Test fun missingEmptyAndNonFileEntriesCannotBePlayedOrDeletedAsRecordings() {
        val root = temporary.newFolder(); val empty = video(root, bytes = "")
        val name = "${UUID.randomUUID()}.mp4"; File(root, "recordings/$name").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { catalog(root).resolve("camera/${empty.name}") }
        assertThrows(IllegalArgumentException::class.java) { catalog(root).resolve("camera/$name") }
        assertThrows(IllegalArgumentException::class.java) { catalog(root).resolve("camera/${UUID.randomUUID()}.mp4") }
    }
}
