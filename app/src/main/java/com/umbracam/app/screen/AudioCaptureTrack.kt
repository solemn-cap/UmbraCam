package com.umbracam.app.screen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import com.umbracam.core.recording.CaptureSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Captures one explicitly selected audio source and emits AAC samples on the monotonic clock. */
internal class AudioCaptureTrack(
    private val projection: MediaProjection,
    private val source: CaptureSource,
    private val onFormat: (MediaFormat) -> Unit,
    private val onSample: (ByteArray, Long, Int) -> Unit,
    private val onFinished: (Throwable?) -> Unit,
) {
    private val stopping = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    init { require(source == CaptureSource.MICROPHONE || source == CaptureSource.INTERNAL_AUDIO) }

    fun start() {
        check(worker == null)
        worker = Thread(::capture, "UmbraCam-audio-${source.name.lowercase()}").apply { start() }
    }

    fun stop() { stopping.set(true) }

    private fun capture() {
        var recorder: AudioRecord? = null
        var codec: MediaCodec? = null
        var failure: Throwable? = null
        try {
            recorder = createRecorder().also {
                check(it.state == AudioRecord.STATE_INITIALIZED) { "Audio source could not be initialized" }
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(audioFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Audio source did not start" }

            val startedNs = System.nanoTime()
            var baseNs: Long? = null
            var framesRead = 0L
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(inputIndex)).apply { clear() }
                        if (stopping.get()) {
                            codec.queueInputBuffer(inputIndex, 0, 0, timestampUs(baseNs ?: startedNs, framesRead), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val bytes = recorder.read(input, input.remaining(), AudioRecord.READ_BLOCKING)
                            if (bytes < 0) throw IOException("Audio source stopped with code $bytes")
                            if (bytes == 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, timestampUs(baseNs ?: startedNs, framesRead), 0)
                            } else {
                                if (baseNs == null) {
                                    val timestamp = android.media.AudioTimestamp()
                                    if (recorder.getTimestamp(timestamp, android.media.AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                                        val timestampBase = timestamp.nanoTime - timestamp.framePosition * 1_000_000_000L / SAMPLE_RATE
                                        baseNs = timestampBase.takeIf { it > 0L }
                                    }
                                    if (baseNs == null) baseNs = startedNs
                                }
                                val ptsUs = timestampUs(checkNotNull(baseNs), framesRead)
                                codec.queueInputBuffer(inputIndex, 0, bytes, ptsUs, 0)
                                framesRead += bytes / BYTES_PER_FRAME
                            }
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(info, 0)
                while (outputIndex >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val output = checkNotNull(codec.getOutputBuffer(outputIndex)).duplicate().apply {
                            position(info.offset); limit(info.offset + info.size)
                        }
                        onSample(ByteArray(info.size).also(output::get), info.presentationTimeUs, info.flags)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (outputEnded) break
                    outputIndex = codec.dequeueOutputBuffer(info, 0)
                }
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) onFormat(codec.outputFormat)
                if (!outputEnded && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) Thread.sleep(2)
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            try { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            onFinished(failure)
        }
    }

    private fun createRecorder(): AudioRecord {
        val audioFormat = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Audio source format is unsupported" }
        val builder = AudioRecord.Builder().setAudioFormat(audioFormat)
            .setBufferSizeInBytes(maxOf(minimum * 2, SAMPLE_RATE / 5 * BYTES_PER_FRAME))
        when (source) {
            CaptureSource.MICROPHONE -> builder.setAudioSource(MediaRecorder.AudioSource.MIC)
            CaptureSource.INTERNAL_AUDIO -> {
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Playback capture requires Android 10" }
                builder.setAudioPlaybackCaptureConfig(
                    AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build(),
                )
            }
            else -> error("Unsupported audio source")
        }
        return try {
            builder.build()
        } catch (error: SecurityException) {
            throw SecurityException("The selected audio capture permission is unavailable", error)
        }
    }

    private fun audioFormat() = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BUFFER_BYTES)
    }

    private fun timestampUs(baseNs: Long, frames: Long): Long =
        (baseNs / 1_000L + frames * 1_000_000L / SAMPLE_RATE).coerceAtLeast(0L)

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_FRAME = 2
        private const val INPUT_BUFFER_BYTES = 16_384
        private const val BIT_RATE = 96_000
    }
}
