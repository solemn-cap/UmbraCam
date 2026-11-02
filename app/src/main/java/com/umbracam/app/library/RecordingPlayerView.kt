package com.umbracam.app.library

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.MediaController
import com.umbracam.app.R

/** Texture-backed playback stays inside Compose's scrolling bounds on Android 10/11 too. */
internal class RecordingPlayerView(context: Context, private val path: String, private val onFailure: () -> Unit) :
    FrameLayout(context), MediaController.MediaPlayerControl, TextureView.SurfaceTextureListener {
    private val texture = TextureView(context)
    private val controls = MediaController(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var closed = false
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true; isFocusable = true
        contentDescription = context.getString(R.string.library_player)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        texture.surfaceTextureListener = this
        controls.setMediaPlayer(this); controls.setAnchorView(this)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        try {
            surface = Surface(texture)
            val next = MediaPlayer(); player = next
            next.setOnPreparedListener { ready ->
                if (player === ready && !closed) {
                    prepared = true; videoWidth = ready.videoWidth; videoHeight = ready.videoHeight
                    fitVideo(); start(); controls.isEnabled = true; controls.show(3_000)
                }
            }
            next.setOnCompletionListener { keepScreenOn = false; controls.show(3_000) }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed && !closed) { releaseMedia(); onFailure() }
                true
            }
            next.setDataSource(path); next.setSurface(surface); next.prepareAsync()
        } catch (_: Exception) { releaseMedia(); onFailure() }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fitVideo()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { releaseMedia(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); fitVideo() }
    private fun fitVideo() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        val fittedWidth = videoWidth * scale; val fittedHeight = videoHeight * scale
        texture.setTransform(Matrix().apply {
            setScale(fittedWidth / width, fittedHeight / height)
            postTranslate((width - fittedWidth) / 2, (height - fittedHeight) / 2)
        })
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); if (prepared) controls.show(3_000); return true }
    override fun start() { if (prepared) { player?.start(); keepScreenOn = true } }
    override fun pause() { if (prepared) { player?.pause(); keepScreenOn = false } }
    override fun getDuration(): Int = if (prepared) player?.duration ?: 0 else 0
    override fun getCurrentPosition(): Int = if (prepared) player?.currentPosition ?: 0 else 0
    override fun seekTo(position: Int) { if (prepared) player?.seekTo(position) }
    override fun isPlaying(): Boolean = prepared && player?.isPlaying == true
    override fun getBufferPercentage(): Int = 100
    override fun canPause(): Boolean = prepared
    override fun canSeekBackward(): Boolean = prepared
    override fun canSeekForward(): Boolean = prepared
    override fun getAudioSessionId(): Int = player?.audioSessionId ?: 0
    private fun releaseMedia() {
        prepared = false; keepScreenOn = false; controls.hide()
        val old = player; player = null; old?.release()
        surface?.release(); surface = null
    }
    fun close() { closed = true; releaseMedia() }
}
