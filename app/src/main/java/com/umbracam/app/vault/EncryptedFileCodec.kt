package com.umbracam.app.vault

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface VaultKeySource { fun key(): SecretKey }

/** Versioned streaming AES-GCM file envelope. The key is never serialized with the file. */
class EncryptedFileCodec(private val keys: VaultKeySource, private val random: SecureRandom = SecureRandom()) {
    fun ensureKeyAvailable() { keys.key() }
    fun isEncrypted(file: File): Boolean = runCatching {
        FileInputStream(file).use { input -> ByteArray(MAGIC.size).also { readFully(input, it) }.contentEquals(MAGIC) }
    }.getOrDefault(false)

    fun encrypt(source: File, destination: File) {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, iv)) }
        BufferedInputStream(FileInputStream(source), BUFFER_SIZE).use { input ->
            BufferedOutputStream(FileOutputStream(destination), BUFFER_SIZE).use { raw ->
                raw.write(MAGIC); raw.write(iv.size); raw.write(iv)
                CipherOutputStream(raw, cipher).use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
        }
        FileOutputStream(destination, true).use { it.fd.sync() }
    }

    fun openDecrypted(source: File): InputStream {
        val raw = BufferedInputStream(FileInputStream(source), BUFFER_SIZE)
        try {
            val magic = ByteArray(MAGIC.size).also { readFully(raw, it) }
            if (!magic.contentEquals(MAGIC)) throw IOException("Recording is not in the encrypted vault format")
            val ivLength = raw.read()
            if (ivLength != IV_BYTES) throw IOException("Invalid vault file header")
            val iv = ByteArray(ivLength).also { readFully(raw, it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, iv)) }
            return CipherInputStream(raw, cipher)
        } catch (error: Throwable) {
            runCatching { raw.close() }
            throw error
        }
    }

    /** Reads through EOF so GCM authentication is checked before a file can be published. */
    fun decrypt(source: File, destination: File) {
        openDecrypted(source).use { input ->
            BufferedOutputStream(FileOutputStream(destination), BUFFER_SIZE).use { output -> input.copyTo(output, BUFFER_SIZE) }
        }
        FileOutputStream(destination, true).use { it.fd.sync() }
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read < 0) throw IOException("Truncated vault file")
            offset += read
        }
    }

    companion object {
        private val MAGIC = byteArrayOf(0x55, 0x4d, 0x42, 0x52, 0x41, 0x56, 0x4c, 0x54) // UMBRAVLT
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val BUFFER_SIZE = 64 * 1024
    }
}
