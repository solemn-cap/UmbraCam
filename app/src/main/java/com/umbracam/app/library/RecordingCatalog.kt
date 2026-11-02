package com.umbracam.app.library

import com.umbracam.app.camera.VideoInspector
import java.io.File
import java.io.InputStream
import java.util.UUID
import com.umbracam.app.vault.EncryptedFileCodec
import com.umbracam.app.vault.VaultMetadataStore
import com.umbracam.app.vault.VaultRecordingLabels

data class CatalogRecording(val key: String, val source: String, val name: String,
    val modifiedMillis: Long, val bytes: Long, val durationMillis: Long?, val readable: Boolean,
    val kind: String = "standard", val encrypted: Boolean = false, val width: Int? = null,
    val height: Int? = null, val frameRate: Int? = null, val hasAudio: Boolean? = null,
    val title: String? = null, val category: String = "")

/** Filter completed catalog entries without changing their newest-first order. */
fun filterRecordings(entries: List<CatalogRecording>, source: String, query: String,
    displayLabel: (CatalogRecording) -> String, category: String = "all"): List<CatalogRecording> {
    val term = query.trim()
    return entries.filter { (source == "all" || it.source == source) && (category == "all" || it.category == category) &&
        (term.isEmpty() || (it.name + " " + (it.title ?: "") + " " + displayLabel(it)).contains(term, ignoreCase = true)) }
}

/** Only completed UUID MP4s in the two explicit roots. Never exposes pending/quarantined files. */
class RecordingCatalog(private val roots: Map<String, File>, private val inspector: VideoInspector,
    private val sosRecordingIds: () -> Set<String> = { emptySet() },
    private val vaultCodec: EncryptedFileCodec? = null,
    private val transientDirectory: File? = null,
    private val metadata: VaultMetadataStore? = null) {
    fun list(): List<CatalogRecording> = roots.flatMap { (source, root) ->
        val sosIds = if (source == "camera") sosRecordingIds() else emptySet()
        val labels = metadata?.readAll().orEmpty()
        File(root, "recordings").listFiles().orEmpty().mapNotNull { file ->
            val key = "$source/${file.name}"
            val resolved = runCatching { resolve(key) }.getOrNull() ?: return@mapNotNull null
            val encrypted = vaultCodec?.isEncrypted(resolved) == true
            val video = runCatching { inspect(resolved, encrypted) }.getOrNull()
            val label = labels[file.name.removeSuffix(".mp4")]
            CatalogRecording(key, source, file.name, resolved.lastModified(), resolved.length(), video?.durationMillis, video != null,
                if (file.name in sosIds) "sos" else "standard", encrypted, video?.width, video?.height, video?.frameRate, video?.hasAudio,
                label?.title, label?.category.orEmpty())
        }
    }.sortedWith(compareByDescending<CatalogRecording> { it.modifiedMillis }.thenBy { it.key })

    fun resolve(key: String): File {
        val pieces = key.split('/')
        require(pieces.size == 2)
        val root = roots[pieces[0]] ?: error("Unknown recording source")
        val name = pieces[1]
        require(name == "${UUID.fromString(name.removeSuffix(".mp4"))}.mp4")
        val directory = File(root, "recordings").canonicalFile
        val candidate = File(directory, name)
        require(candidate.canonicalFile.parentFile == directory && candidate.canonicalFile.name == name)
        require(candidate.isFile && candidate.length() > 0)
        return candidate
    }
    fun input(key: String): InputStream {
        val file = resolve(key)
        return if (vaultCodec?.isEncrypted(file) == true) vaultCodec.openDecrypted(file) else file.inputStream()
    }
    fun materializeForPlayback(key: String, destination: File): File {
        val source = resolve(key)
        if (vaultCodec?.isEncrypted(source) == true) vaultCodec.decrypt(source, destination)
        else source.copyTo(destination, overwrite = true)
        return destination
    }
    fun rename(key: String, title: String) = setLabels(key, metadata?.readAll()?.get(resolve(key).name.removeSuffix(".mp4"))?.copy(title = title) ?: VaultRecordingLabels(title = title))
    fun setCategory(key: String, category: String) = setLabels(key, metadata?.readAll()?.get(resolve(key).name.removeSuffix(".mp4"))?.copy(category = category) ?: VaultRecordingLabels(category = category))
    private fun setLabels(key: String, labels: VaultRecordingLabels) {
        val file = resolve(key)
        checkNotNull(metadata) { "Vault metadata store is unavailable" }.set(file.name.removeSuffix(".mp4"), labels)
    }
    fun delete(key: String) {
        val file = resolve(key)
        check(file.delete()) { "Could not delete recording" }
        metadata?.remove(file.name.removeSuffix(".mp4"))
    }

    private fun inspect(file: File, encrypted: Boolean): com.umbracam.app.camera.InspectedVideo {
        if (!encrypted) return inspector.inspect(file)
        val directory = transientDirectory ?: error("Vault inspection directory is unavailable")
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File.createTempFile("inspect-", ".mp4", directory)
        return try { vaultCodec!!.decrypt(file, temporary); inspector.inspect(temporary) }
        finally { temporary.delete() }
    }
}
