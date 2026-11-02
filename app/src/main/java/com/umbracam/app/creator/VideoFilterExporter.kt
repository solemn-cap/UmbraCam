package com.umbracam.app.creator

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.HslAdjustment
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import com.umbracam.app.camera.AndroidVideoInspector
import com.umbracam.app.camera.RecordingFileStore
import com.umbracam.app.vault.EncryptedFileCodec
import com.umbracam.core.recording.FilterConfig
import com.umbracam.core.recording.FilterId
import com.umbracam.core.recording.tone
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Creates a new encrypted vault entry and never modifies the selected original. Call on main. */
@OptIn(UnstableApi::class)
class VideoFilterExporter(
    private val context: Context,
    private val codec: EncryptedFileCodec,
    private val transient: File,
    private val recordingRoot: File,
    private val continueAllowed: () -> Boolean = { true },
) {
    suspend fun export(source: File, config: FilterConfig, onProgress: (Int) -> Unit): String {
        val store = RecordingFileStore(recordingRoot, AndroidVideoInspector(), vaultCodec = codec)
        val clearInput = withContext(Dispatchers.IO) {
            val length=source.length()
            val reserve=RecordingFileStore.MINIMUM_FREE_BYTES
            val required=if(length>(Long.MAX_VALUE-reserve)/2) Long.MAX_VALUE else length*2+reserve
            if (recordingRoot.usableSpace < required) throw java.io.IOException("Not enough free space for a safe filtered copy")
            check(transient.isDirectory || transient.mkdirs())
            File.createTempFile("filter-source-", ".mp4", transient).also { temp ->
                try {
                    if (codec.isEncrypted(source)) codec.decrypt(source, temp) else source.copyTo(temp, overwrite = true)
                } catch (error: Throwable) { temp.delete(); throw error }
            }
        }
        val output = try { withContext(Dispatchers.IO) { store.reserve() } }
            catch (error: Throwable) { clearInput.delete(); throw error }
        var transformer: Transformer? = null
        var completed = false
        val result = CompletableDeferred<Result<Unit>>()
        try {
            val tone = config.tone()
            val intensity = if (config.intensity.isFinite()) config.intensity.coerceIn(0f,1f) else 1f
            val effects = buildList<androidx.media3.common.Effect> {
                if (tone.monochrome) add(HslAdjustment.Builder().adjustSaturation(-100f * intensity).build())
                else if (config.id != FilterId.NATURAL) add(RgbAdjustment.Builder()
                    .setRedScale(tone.red).setGreenScale(tone.green).setBlueScale(tone.blue).build())
            }
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { completed = true; result.complete(Result.success(Unit)) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    completed = true; result.complete(Result.failure(exportException))
                }
            }
            transformer = Transformer.Builder(context.applicationContext).setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener).build()
            check(output.delete()) { "Could not prepare a staging file for filter export" }
            val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(clearInput)))
                .setEffects(Effects(emptyList(), effects)).build()
            transformer.start(item, output.absolutePath)
            while (!result.isCompleted) {
                if (!continueAllowed()) { transformer.cancel(); throw CreatorProcessingThermalLimit() }
                val progress = ProgressHolder()
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress)
                delay(300)
            }
            result.await().getOrThrow()
            if (!continueAllowed()) throw CreatorProcessingThermalLimit()
            check(output.isFile && output.length() > 0L) { "Filter export produced no output" }
            val saved = withContext(Dispatchers.IO) { store.publish(output) }
            return saved.segment.storageId
        } finally {
            if (!completed) runCatching { transformer?.cancel() }
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { clearInput.delete(); output.delete() }
        }
    }
}

class CreatorProcessingThermalLimit : java.io.IOException("Filter processing stopped because the device is too warm")
