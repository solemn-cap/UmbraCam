package com.umbracam.app.library

import com.umbracam.app.camera.VideoInspector
import java.io.File
import java.io.InputStream
import java.util.UUID

data class CatalogRecording(val key: String, val source: String, val name: String,
    val modifiedMillis: Long, val bytes: Long, val durationMillis: Long?, val readable: Boolean)

/** Filter completed catalog entries without changing their newest-first order. */
fun filterRecordings(entries: List<CatalogRecording>, source: String, query: String,
    displayLabel: (CatalogRecording) -> String): List<CatalogRecording> {
    val term = query.trim()
    return entries.filter { (source == "all" || it.source == source) &&
        (term.isEmpty() || (it.name + " " + displayLabel(it)).contains(term, ignoreCase = true)) }
}

/** Only completed UUID MP4s in the two explicit roots. Never exposes pending/quarantined files. */
class RecordingCatalog(private val roots: Map<String, File>, private val inspector: VideoInspector) {
    fun list(): List<CatalogRecording> = roots.flatMap { (source, root) ->
        File(root, "recordings").listFiles().orEmpty().mapNotNull { file ->
            val key = "$source/${file.name}"
            val resolved = runCatching { resolve(key) }.getOrNull() ?: return@mapNotNull null
            val video = runCatching { inspector.inspect(resolved) }.getOrNull()
            CatalogRecording(key, source, file.name, resolved.lastModified(), resolved.length(), video?.durationMillis, video != null)
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
    fun input(key: String): InputStream = resolve(key).inputStream()
    fun delete(key: String) { check(resolve(key).delete()) { "Could not delete recording" } }
}
