package com.umbracam.app.screen

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import com.umbracam.core.recording.ScreenOutputSize
import java.io.File

/** One worker owns codec/muxer and releases them before handing the file to publication. */
internal class ScreenEncoder(
    private val file: File, private val size: ScreenOutputSize, private val codecName: String,
    private val onSurface: (Surface) -> Unit,
    private val onSample: (Long, Int) -> Unit,
    private val onFinished: (Throwable?) -> Unit,
) {
    private val thread = HandlerThread("UmbraCam-screen-encoder").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var muxerStarted = false
    private var closed = false
    private var stopping = false
    private var firstPts: Long? = null

    fun start() { worker.post {
        try {
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val encoder = MediaCodec.createByCodecName(codecName)
            codec = encoder
            encoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit
                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) { finish(error) }
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (closed) return
                    try {
                        check(!muxerStarted)
                        track = muxer!!.addTrack(format); muxer!!.start(); muxerStarted = true
                    } catch (error: Exception) { finish(error) }
                }
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (closed) return
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            if (firstPts == null) firstPts = info.presentationTimeUs
                            val pts = (info.presentationTimeUs - firstPts!!).coerceAtLeast(0)
                            val sample = MediaCodec.BufferInfo().apply { set(info.offset, info.size, pts, info.flags) }
                            muxer!!.writeSampleData(track, checkNotNull(codec.getOutputBuffer(index)), sample)
                            val bytes = info.size
                            main.post { onSample(pts, bytes) }
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        if (eos) finish(null)
                    } catch (error: Exception) { finish(error) }
                }
            }, worker)
            encoder.configure(format(size), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface().also { surface = it }
            encoder.start()
            main.post { onSurface(input) }
        } catch (error: Exception) { finish(error) }
    } }

    fun stop() { worker.post {
        if (!closed && !stopping) {
            stopping = true
            try { codec?.signalEndOfInputStream() ?: finish(null) }
            catch (error: Exception) { finish(error) }
        }
    } }
    fun abort() { worker.post { finish(IllegalStateException("Screen encoder finalization timed out")) } }

    private fun finish(error: Throwable?) {
        if (closed) return
        closed = true
        var failure = error
        try { codec?.stop() } catch (next: Exception) { if (failure == null) failure = next }
        try { codec?.release() } catch (next: Exception) { if (failure == null) failure = next }
        try { surface?.release() } catch (_: Exception) { }
        if (muxerStarted) try { muxer?.stop() } catch (next: Exception) { if (failure == null) failure = next }
        try { muxer?.release() } catch (next: Exception) { if (failure == null) failure = next }
        main.post { onFinished(failure) }
        thread.quitSafely()
    }

    companion object {
        private fun format(size: ScreenOutputSize) = MediaFormat.createVideoFormat("video/avc", size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 33_333L)
        }
        fun codecFor(size: ScreenOutputSize): String? = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format(size))
    }
}
