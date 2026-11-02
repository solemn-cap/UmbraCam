package com.umbracam.app.camera

import java.io.File
import java.io.IOException
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import com.umbracam.app.vault.EncryptedFileCodec
import com.umbracam.app.vault.VaultKeySource
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val inspector = VideoInspector { InspectedVideo(1000, true) }
    private fun root() = File(temporary.root, "camera")
    private fun store(inspector: VideoInspector = this.inspector, space: Long = Long.MAX_VALUE) =
        RecordingFileStore(root(), inspector) { space }

    @Test fun publishesOnlyAfterInspectionAndMovesEntireOutput() {
        var inspected = false
        val store = store(VideoInspector { file ->
            assertTrue(file.parentFile?.name == "pending")
            inspected = true
            InspectedVideo(1000, true)
        })
        // Fake bytes test filesystem publication only; the injected inspector is not a real decoder.
        val pending = store.reserve().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(File(root(), "recordings").exists())
        val result = store.publish(pending)
        assertTrue(inspected)
        assertFalse(pending.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(root(), "recordings/${result.segment.storageId}").readBytes())
    }

    @Test(expected = InsufficientRecordingSpace::class)
    fun rejectsReservationBelowFreeSpaceThreshold() {
        store(space = RecordingFileStore.MINIMUM_FREE_BYTES - 1).reserve()
    }

    @Test fun inspectionFailureLeavesNoCompletedOutput() {
        val store = store(VideoInspector { throw IOException("corrupt container") })
        val pending = store.reserve().apply { writeText("invalid") }
        assertThrows(IOException::class.java) { store.publish(pending) }
        assertTrue(pending.exists())
        assertFalse(File(root(), "recordings").exists())
    }

    @Test fun emptyOutputCannotBePublished() {
        val store = store()
        assertThrows(IOException::class.java) { store.publish(store.reserve()) }
        assertFalse(File(root(), "recordings").exists())
    }

    @Test fun quarantineDoesNotTouchEarlierCompletedVideo() {
        val store = store()
        val first = store.publish(store.reserve().apply { writeText("first") })
        val failed = store.reserve().apply { writeText("failed") }
        store.quarantine(failed)
        assertFalse(failed.exists())
        assertEquals("first", File(root(), "recordings/${first.segment.storageId}").readText())
        assertEquals("failed", File(root(), "quarantine").listFiles()!!.single().readText())
    }

    @Test fun filesOutsideStagingCannotBePublishedOrMoved() {
        val external = temporary.newFile("not-recording.mp4").apply { writeText("keep") }
        assertThrows(IllegalArgumentException::class.java) { store().publish(external) }
        assertThrows(IllegalArgumentException::class.java) { store().quarantine(external) }
        assertEquals("keep", external.readText())
    }

    @Test fun encryptedPublicationAndLegacyMigrationPreserveCanonicalNames() {
        val codec=EncryptedFileCodec(VaultKeySource { SecretKeySpec(ByteArray(32) { 7 },"AES") })
        val secure=RecordingFileStore(root(),inspector,{ Long.MAX_VALUE },codec)
        val pending=secure.reserve().apply { writeText("new capture") }
        val published=secure.publish(pending)
        val encrypted=File(root(),"recordings/${published.segment.storageId}")
        assertTrue(codec.isEncrypted(encrypted))
        assertEquals("new capture",codec.openDecrypted(encrypted).bufferedReader().use { it.readText() })
        val legacyId=UUID.randomUUID().toString()+".mp4"
        val legacy=File(root(),"recordings/$legacyId").apply { writeText("old capture") }
        val report=secure.migratePlaintextRecordings()
        assertEquals(1,report.migrated); assertEquals(0,report.failed)
        assertEquals(legacyId,legacy.name); assertTrue(codec.isEncrypted(legacy))
        assertEquals("old capture",codec.openDecrypted(legacy).bufferedReader().use { it.readText() })
    }

    @Test fun failedLegacyVerificationKeepsOriginalPlaintextUntouched() {
        val codec=EncryptedFileCodec(VaultKeySource { SecretKeySpec(ByteArray(32) { 9 },"AES") })
        var calls=0
        val changingInspector=VideoInspector { calls++; if(calls==1) InspectedVideo(1000,true) else InspectedVideo(2000,true) }
        val store=RecordingFileStore(root(),changingInspector,{ Long.MAX_VALUE },codec)
        val original=File(root(),"recordings/${UUID.randomUUID()}.mp4").apply { parentFile!!.mkdirs();writeText("keep me") }
        val report=store.migratePlaintextRecordings()
        assertEquals(0,report.migrated); assertEquals(1,report.failed)
        assertEquals("keep me",original.readText()); assertFalse(codec.isEncrypted(original))
    }
    @Test fun unusableDurationIsNotSaved() {
        val store = store(VideoInspector { InspectedVideo(0, false) })
        val pending = store.reserve().apply { writeText("bad duration") }
        assertThrows(IOException::class.java) { store.publish(pending) }
        assertTrue(pending.exists())
        assertFalse(File(root(), "recordings").exists())
    }
}
