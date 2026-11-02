package com.umbracam.app.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

data class VaultRecordingLabels(val title: String? = null, val category: String = "")

/** Small title/category index, encrypted with the same Keystore key as the media. */
class VaultMetadataStore(private val file: File, private val temporaryDirectory: File, private val codec: EncryptedFileCodec) {
    @Synchronized fun readAll(): Map<String, VaultRecordingLabels> {
        if (!file.isFile) return emptyMap()
        val clear = File.createTempFile("vault-index-", ".tmp", temporaryDirectory.apply { mkdirs() })
        return try {
            codec.decrypt(file, clear)
            Properties().also { properties -> FileInputStream(clear).use(properties::load) }
                .stringPropertyNames().mapNotNull { key ->
                    val id = key.substringBefore('.')
                    if (runCatching { UUID.fromString(id) }.isFailure) null else {
                        val prefix = "$id."
                        val title = properties.getProperty(prefix + "title")
                        val category = properties.getProperty(prefix + "category", "")
                        id to VaultRecordingLabels(title, category)
                    }
                }.toMap()
        } finally { clear.delete() }
    }

    @Synchronized fun set(id: String, labels: VaultRecordingLabels) {
        UUID.fromString(id)
        require(labels.title == null || (labels.title.length in 1..80 && labels.title.none { it.isISOControl() }))
        require(labels.category in CATEGORIES)
        val entries = readAll().toMutableMap()
        if (labels.title == null && labels.category.isEmpty()) entries.remove(id) else entries[id] = labels
        write(entries)
    }

    @Synchronized fun remove(id: String) { val entries = readAll().toMutableMap(); if (entries.remove(id) != null) write(entries) }

    private fun write(entries: Map<String, VaultRecordingLabels>) {
        check(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs())
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        val clear = File.createTempFile("vault-index-clear-", ".tmp", temporaryDirectory)
        val encrypted = File.createTempFile("vault-index-", ".tmp", file.parentFile)
        try {
            val properties = Properties()
            entries.forEach { (id, labels) ->
                UUID.fromString(id)
                labels.title?.let { properties.setProperty("$id.title", it) }
                if (labels.category.isNotEmpty()) properties.setProperty("$id.category", labels.category)
            }
            FileOutputStream(clear).use { properties.store(it, null); it.fd.sync() }
            codec.encrypt(clear, encrypted)
            Files.move(encrypted.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { clear.delete(); encrypted.delete() }
    }

    companion object { val CATEGORIES = setOf("", "personal", "work", "travel", "important", "sos") }
}
