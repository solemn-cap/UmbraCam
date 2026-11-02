package com.umbracam.app.camera

import android.media.MediaMetadataRetriever
import java.io.File
import java.io.IOException
import android.media.MediaExtractor
import android.media.MediaFormat

/** Structural container/metadata check, not a claim of end-to-end decoder/playback validation. */
class AndroidVideoInspector : VideoInspector {
    override fun inspect(file: File): InspectedVideo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val video = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (!video || width <= 0 || height <= 0 || duration <= 0) throw IOException("Invalid finalized video")
            val extractor = MediaExtractor()
            val (frameRate, audioTrackCount) = try {
                extractor.setDataSource(file.absolutePath)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val frameRate = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                    ?.let { format -> if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else null }
                frameRate to formats.count { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            } finally { extractor.release() }
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            return InspectedVideo(duration, hasAudio, width, height, frameRate, audioTrackCount)
        } finally {
            retriever.release()
        }
    }
}
