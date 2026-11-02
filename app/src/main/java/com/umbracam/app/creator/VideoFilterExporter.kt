package com.umbracam.app.creator

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import com.umbracam.core.recording.CreatorEdit
import com.umbracam.core.recording.CreatorFraming
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import com.umbracam.app.camera.AndroidVideoInspector
import com.umbracam.app.camera.RecordingFileStore
import com.umbracam.app.vault.EncryptedFileCodec
import com.umbracam.core.recording.FilterConfig
import com.umbracam.core.recording.FilterId
import com.umbracam.core.recording.tone
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Creates a new encrypted vault entry and never modifies the selected original. Call on main. */
@AndroidxOptIn(markerClass = [UnstableApi::class])
class VideoFilterExporter(
    private val context: Context,
    private val codec: EncryptedFileCodec,
    private val transient: File,
    private val recordingRoot: File,
    private val continueAllowed: () -> Boolean = { true },
) {
    suspend fun export(source: File, config: FilterConfig, onProgress: (Int) -> Unit): String =
        export(source, config, CreatorEdit(), onProgress)

    suspend fun export(source: File, config: FilterConfig, edit: CreatorEdit, onProgress: (Int) -> Unit): String {
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
        val splitAudioFiles = mutableListOf<File>()
        var transformer: Transformer? = null
        var completed = false
        val result = CompletableDeferred<Result<Unit>>()
        try {
            val tone = config.tone()
            val intensity = if (config.intensity.isFinite()) config.intensity.coerceIn(0f,1f) else 1f
            val safeEdit = edit.validated(null)
            val audioTracks = withContext(Dispatchers.IO) { inspectAudioTracks(clearInput) }
            if (safeEdit.perTrackAudioGains.isNotEmpty()) require(safeEdit.perTrackAudioGains.size == audioTracks.size) {
                "The selected audio-track controls no longer match this recording"
            }
            val effects = buildList<androidx.media3.common.Effect> {
                if (tone.monochrome) add(HslAdjustment.Builder().adjustSaturation(-100f * intensity).build())
                else if (config.id != FilterId.NATURAL) add(RgbAdjustment.Builder()
                    .setRedScale(tone.red).setGreenScale(tone.green).setBlueScale(tone.blue).build())
                when (safeEdit.framing) {
                    CreatorFraming.ORIGINAL -> Unit
                    CreatorFraming.SQUARE -> add(Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                    CreatorFraming.VERTICAL -> add(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                    CreatorFraming.LANDSCAPE -> add(Presentation.createForAspectRatio(16f / 9f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                }
                if (safeEdit.branding.isNotBlank()) {
                    val text = SpannableString(safeEdit.branding).apply {
                        setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), 0, length, 0)
                        setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, length, 0)
                    }
                    val anchorY = when (safeEdit.overlayPosition) {
                        com.umbracam.core.recording.CreatorOverlayPosition.TOP -> 0.85f
                        com.umbracam.core.recording.CreatorOverlayPosition.CENTER -> 0f
                        com.umbracam.core.recording.CreatorOverlayPosition.BOTTOM -> -0.85f
                    }
                    val overlaySettings = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, anchorY).build()
                    add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text, overlaySettings))))
                }
            }
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { completed = true; result.complete(Result.success(Unit)) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    completed = true; result.complete(Result.failure(exportException))
                }
            }
            val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder()
                    .setBitrate(safeEdit.exportProfile.targetVideoBitrate).build())
                .build()
            transformer = Transformer.Builder(context.applicationContext).setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(encoderFactory)
                .addListener(listener).build()
            check(output.delete()) { "Could not prepare a staging file for filter export" }
            val media = clippedMediaItem(clearInput, safeEdit)
            val useTrackMix = audioTracks.size > 1
            val audioProcessorsForTrack: (Int) -> List<androidx.media3.common.audio.AudioProcessor> = { index ->
                val gain = safeEdit.perTrackAudioGains.getOrNull(index) ?: safeEdit.audioGain
                val channels = audioTracks.getOrNull(index)?.channelCount ?: 2
                if (gain == 1f && !useTrackMix) emptyList() else listOf(
                    ChannelMixingAudioProcessor().apply {
                        require(channels in 1..6) { "This audio channel layout is not supported for mixing" }
                        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 2).scaleBy(gain))
                    })
            }
            if (useTrackMix) {
                check(transient.usableSpace >= clearInput.length() + RecordingFileStore.MINIMUM_FREE_BYTES) {
                    "Not enough temporary storage to mix separate audio tracks safely"
                }
                val audioFiles = withContext(Dispatchers.IO) { extractAudioTracks(clearInput, transient) }
                splitAudioFiles += audioFiles
                check(audioFiles.size == audioTracks.size && audioFiles.isNotEmpty()) { "Could not extract the recording's audio tracks" }
                val videoItem = EditedMediaItem.Builder(media).setRemoveAudio(true).setEffects(Effects(emptyList(), effects)).build()
                val sequences = mutableListOf(EditedMediaItemSequence.withVideoFrom(listOf(videoItem)))
                audioFiles.forEachIndexed { index, audioFile ->
                    val audioItem = EditedMediaItem.Builder(clippedMediaItem(audioFile, safeEdit))
                        .setRemoveVideo(true).setEffects(Effects(audioProcessorsForTrack(index), emptyList())).build()
                    sequences += EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
                }
                transformer.start(Composition.Builder(sequences).build(), output.absolutePath)
            } else {
                val audioProcessors = if (audioTracks.isEmpty()) emptyList() else audioProcessorsForTrack(0)
                val item = EditedMediaItem.Builder(media).setEffects(Effects(audioProcessors, effects)).build()
                transformer.start(item, output.absolutePath)
            }
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
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                clearInput.delete(); splitAudioFiles.forEach(File::delete); output.delete()
            }
        }
    }

    private fun clippedMediaItem(file: File, edit: CreatorEdit): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(file)).apply {
            if (edit.startMs > 0 || edit.endMs != null) {
                val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(edit.startMs)
                edit.endMs?.let(clipping::setEndPositionMs)
                setClippingConfiguration(clipping.build())
            }
        }.build()

    private data class AudioTrackInfo(val index: Int, val channelCount: Int)

    private fun inspectAudioTracks(file: File): List<AudioTrackInfo> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") != true) null
                else AudioTrackInfo(index, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            }
        } finally { extractor.release() }
    }

    /** Copies one compressed audio track into a temporary MP4, preserving its original timestamps. */
    private fun extractAudioTracks(source: File, directory: File): List<File> {
        val extractor = MediaExtractor()
        val outputs = mutableListOf<File>()
        try {
            extractor.setDataSource(source.path)
            val indices = (0 until extractor.trackCount).filter {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            indices.forEachIndexed { ordinal, index ->
                val file = File.createTempFile("creator-audio-$ordinal-", ".mp4", directory)
                outputs += file
                val format = extractor.getTrackFormat(index)
                val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var started = false
                try {
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    extractor.selectTrack(index)
                    val track = muxer.addTrack(format)
                    muxer.start(); started = true
                    val capacity = (if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1 shl 20)
                        .coerceIn(64 * 1024, 8 * 1024 * 1024)
                    val buffer = ByteBuffer.allocateDirect(capacity)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val sampleFlags = extractor.sampleFlags
                        require(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                            "Encrypted audio tracks cannot be mixed"
                        }
                        val muxerFlags = (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) or
                            (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0)
                        info.set(0, size, extractor.sampleTime, muxerFlags)
                        muxer.writeSampleData(track, buffer, info)
                        if (!extractor.advance()) break
                    }
                    extractor.unselectTrack(index)
                    check(file.length() > 0L) { "Audio track $ordinal contains no samples" }
                } finally {
                    if (started) runCatching { muxer.stop() }
                    muxer.release()
                }
            }
            return outputs
        } catch (error: Throwable) {
            outputs.forEach(File::delete)
            throw error
        } finally { extractor.release() }
    }
}

class CreatorProcessingThermalLimit : java.io.IOException("Filter processing stopped because the device is too warm")
