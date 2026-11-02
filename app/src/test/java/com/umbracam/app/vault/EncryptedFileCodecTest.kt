package com.umbracam.app.vault

import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedFileCodecTest {
    @get:Rule val temporary = TemporaryFolder()
    private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val codec = EncryptedFileCodec(VaultKeySource { key })

    @Test fun encryptedFilesRoundTripAndHidePlaintext() {
        val clear = temporary.newFile("source.mp4").apply { writeBytes(ByteArray(100_000) { (it % 251).toByte() }) }
        val encrypted = File(temporary.root, "recording.mp4")
        val restored = File(temporary.root, "restored.mp4")
        codec.encrypt(clear, encrypted)
        assertTrue(codec.isEncrypted(encrypted))
        assertFalse(encrypted.readBytes().copyOfRange(21,37).contentEquals(clear.readBytes().copyOfRange(0,16)))
        codec.decrypt(encrypted, restored)
        assertArrayEquals(clear.readBytes(), restored.readBytes())
    }

    @Test fun tamperedCiphertextCannotBeDecrypted() {
        val clear = temporary.newFile("source.mp4").apply { writeText("sensitive recording bytes") }
        val encrypted = File(temporary.root, "recording.mp4")
        codec.encrypt(clear, encrypted)
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex - 2] = (bytes[bytes.lastIndex - 2].toInt() xor 1).toByte()
        encrypted.writeBytes(bytes)
        assertThrows(Exception::class.java) { codec.decrypt(encrypted, File(temporary.root, "restored.mp4")) }
    }

    @Test fun encryptedMetadataRoundTripsWithoutClearIndex() {
        val dir = File(temporary.root, "transient").apply { mkdirs() }
        val store = VaultMetadataStore(File(temporary.root, "metadata.enc"), dir, codec)
        val id = java.util.UUID.randomUUID().toString()
        store.set(id, VaultRecordingLabels("Important meeting", "work"))
        assertEquals(VaultRecordingLabels("Important meeting", "work"), store.readAll()[id])
        assertTrue(codec.isEncrypted(File(temporary.root, "metadata.enc")))
        assertFalse(dir.listFiles().orEmpty().any { it.length() > 0 })
    }
}

