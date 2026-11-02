package com.umbracam.app.screen

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import com.umbracam.core.recording.CaptureSource
import com.umbracam.core.recording.ScreenOutputSize
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** Serializes video and optional AAC tracks into one MP4 using their shared monotonic time base. */
internal class ScreenEncoder(
    private val file: File,
    private val size: ScreenOutputSize,
    private val codecName: String,
    private val projection: MediaProjection,
    private val audioSources: Set<CaptureSource>,
    private val onSurface: (Surface) -> Unit,
    private val onSample: (CaptureSource, Long, Int) -> Unit,
    private val onSourceFailure: (CaptureSource, Throwable) -> Unit,
    private val onFinished: (Throwable?) -> Unit,
) {
    private val thread = HandlerThread("UmbraCam-screen-encoder").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val sources = setOf(CaptureSource.SCREEN) + audioSources
    private val tracks = mutableMapOf<CaptureSource, Int>()
    private val pending = mutableListOf<EncodedSample>()
    private val lastPtsUs = mutableMapOf<CaptureSource, Long>()
    private val ended = mutableSetOf<CaptureSource>()
    private val audio = mutableMapOf<CaptureSource, AudioCaptureTrack>()
    private var pendingBytes = 0L
    private var timelineOriginUs: Long? = null
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var closed = false
    private var stopping = false
    private var failure: Throwable? = null

    init {
        require(audioSources.all { it == CaptureSource.MICROPHONE || it == CaptureSource.INTERNAL_AUDIO })
    }

    fun start() { worker.post {
        try {
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val encoder = MediaCodec.createByCodecName(codecName)
            codec = encoder
            encoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit
                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) = fail(CaptureSource.SCREEN, error)
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = addTrack(CaptureSource.SCREEN, format)
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (closed) return
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buffer = checkNotNull(codec.getOutputBuffer(index)).duplicate().apply {
                                position(info.offset); limit(info.offset + info.size)
                            }
                            acceptSample(CaptureSource.SCREEN, ByteArray(info.size).also(buffer::get), info.presentationTimeUs, info.flags)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        if (eos) markEnded(CaptureSource.SCREEN, null)
                    } catch (error: Exception) { fail(CaptureSource.SCREEN, error) }
                }
            }, worker)
            encoder.configure(videoFormat(size), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface().also { surface = it }
            encoder.start()
            audioSources.forEach { source ->
                if (stopping) { ended += source; return@forEach }
                val track = AudioCaptureTrack(projection, source,
                    onFormat = { format -> worker.post { addTrack(source, format) } },
                    onSample = { bytes, ptsUs, flags -> worker.post { acceptSample(source, bytes, ptsUs, flags) } },
                    onFinished = { error -> worker.post { markEnded(source, error) } },
                )
                audio[source] = track
                try { track.start() } catch (error: Exception) { markEnded(source, error) }
            }
            main.post { onSurface(input) }
        } catch (error: Exception) { fail(CaptureSource.SCREEN, error) }
    } }

    fun stop() { worker.post { stopOnWorker() } }

    fun abort() { worker.post {
        if (closed) return@post
        failure = failure ?: IOException("Screen encoder finalization timed out")
        audio.values.forEach(AudioCaptureTrack::stop)
        finishNow()
    } }

    private fun addTrack(source: CaptureSource, format: MediaFormat) {
        if (closed) return
        try {
            check(!muxerStarted && source !in tracks)
            tracks[source] = checkNotNull(muxer).addTrack(format)
            startMuxerWhenReady()
        } catch (error: Exception) { fail(source, error) }
    }

    private fun acceptSample(source: CaptureSource, bytes: ByteArray, ptsUs: Long, flags: Int) {
        if (closed || bytes.isEmpty() || source !in sources) return
        try {
            check(source in tracks) { "Encoded sample arrived before its output format" }
            if (!muxerStarted) {
                pending += EncodedSample(source, bytes, ptsUs, flags)
                pendingBytes += bytes.size
                check(pendingBytes <= MAX_PENDING_BYTES) { "Audio/video encoder tracks did not become ready together" }
                startMuxerWhenReady()
            } else writeSample(EncodedSample(source, bytes, ptsUs, flags))
        } catch (error: Exception) { fail(source, error) }
    }

    private fun startMuxerWhenReady() {
        if (closed || muxerStarted || tracks.keys.containsAll(sources).not()) return
        if (sources.any { source -> pending.none { it.source == source } }) return
        timelineOriginUs = pending.minOf { it.presentationTimeUs }
        checkNotNull(muxer).start()
        muxerStarted = true
        val buffered = pending.sortedBy { it.presentationTimeUs }
        pending.clear(); pendingBytes = 0
        buffered.forEach(::writeSample)
    }

    private fun writeSample(sample: EncodedSample) {
        val origin = checkNotNull(timelineOriginUs)
        val pts = (sample.presentationTimeUs - origin).coerceAtLeast(0L)
        val monotonicPts = maxOf(pts, (lastPtsUs[sample.source] ?: -1L) + 1L)
        val info = MediaCodec.BufferInfo().apply { set(0, sample.bytes.size, monotonicPts, sample.flags) }
        checkNotNull(muxer).writeSampleData(checkNotNull(tracks[sample.source]), ByteBuffer.wrap(sample.bytes), info)
        lastPtsUs[sample.source] = monotonicPts
        main.post { onSample(sample.source, monotonicPts, sample.bytes.size) }
    }

    private fun fail(source: CaptureSource, error: Throwable) {
        if (closed) return
        if (failure == null) failure = error
        main.post { onSourceFailure(source, error) }
        stopOnWorker()
    }

    private fun stopOnWorker() {
        if (closed || stopping) return
        stopping = true
        audio.values.forEach(AudioCaptureTrack::stop)
        (audioSources - audio.keys).forEach { ended += it }
        val video = codec
        if (video == null) markEnded(CaptureSource.SCREEN, failure)
        else try { video.signalEndOfInputStream() }
        catch (error: Exception) { failure = failure ?: error; markEnded(CaptureSource.SCREEN, error) }
    }

    private fun markEnded(source: CaptureSource, error: Throwable?) {
        if (closed || !ended.add(source)) return
        if (error != null) {
            failure = failure ?: error
            main.post { onSourceFailure(source, error) }
            if (!stopping) stopOnWorker()
        }
        if (ended.containsAll(sources)) finishNow()
    }

    private fun finishNow() {
        if (closed) return
        closed = true
        var result = failure
        try { codec?.stop() } catch (error: Exception) { result = result ?: error }
        try { codec?.release() } catch (error: Exception) { result = result ?: error }
        try { surface?.release() } catch (_: Exception) { }
        if (muxerStarted) try { muxer?.stop() } catch (error: Exception) { result = result ?: error }
        else result = result ?: IOException("No complete synchronized media tracks were produced")
        try { muxer?.release() } catch (error: Exception) { result = result ?: error }
        main.post { onFinished(result) }
        thread.quitSafely()
    }

    private data class EncodedSample(val source: CaptureSource, val bytes: ByteArray, val presentationTimeUs: Long, val flags: Int)

    companion object {
        private const val MAX_PENDING_BYTES = 16L * 1024 * 1024
        private fun videoFormat(size: ScreenOutputSize) = MediaFormat.createVideoFormat("video/avc", size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 33_333L)
        }
        fun codecFor(size: ScreenOutputSize): String? = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(videoFormat(size))
    }
}
