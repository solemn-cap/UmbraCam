package com.umbracam.app.screen

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Display
import com.umbracam.core.recording.ReactionLayout
import com.umbracam.core.recording.ScreenOutputSize
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal data class FrontCameraConfiguration(
    val id: String,
    val width: Int,
    val height: Int,
    val sensorOrientation: Int,
    val aeFpsRange: android.util.Range<Int>?,
)

/** Read-only camera metadata selection; callers must hold the point-of-use CAMERA grant. */
internal object FrontCameraConfigurationReader {
    fun read(context: Context): FrontCameraConfiguration? {
        val manager = context.getSystemService(CameraManager::class.java)
        return manager.cameraIdList.firstNotNullOfOrNull { id ->
            runCatching {
                val characteristics = manager.getCameraCharacteristics(id)
                if (characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) !=
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) return@runCatching null
                val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: return@runCatching null
                val size = map.getOutputSizes(SurfaceTexture::class.java).orEmpty()
                    .filter { it.width >= 320 && it.height >= 240 && it.width <= 1920 && it.height <= 1920 }
                    .minWithOrNull(compareBy<Size> {
                        kotlin.math.abs(it.width.toFloat() / it.height - 16f / 9f)
                    }.thenByDescending { it.width.toLong() * it.height }) ?: return@runCatching null
                FrontCameraConfiguration(id, size.width, size.height,
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                    characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                        ?.filter { it.lower >= 15 && it.upper <= 60 }
                        ?.maxByOrNull { it.upper })
            }.getOrNull()
        }
    }
}

/**
 * Composites the authorized display stream and one front Camera2 stream into the MediaCodec
 * input Surface. One GL thread owns EGL, both external textures, Camera2 callbacks, and teardown.
 */
internal class ScreenCameraCompositor(
    context: Context,
    private val projection: MediaProjection,
    private val displayWidth: Int,
    private val displayHeight: Int,
    private val densityDpi: Int,
    private val outputSize: ScreenOutputSize,
    private val layout: ReactionLayout,
    private val cameraConfig: FrontCameraConfiguration,
    private val encoderSurface: Surface,
    private val onProjectionReady: () -> Unit,
    private val onCameraFrameComposed: (Long) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(CameraManager::class.java)
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val thread = HandlerThread("UmbraCam-screen-compositor").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(context.mainLooper)
    private val closing = AtomicBoolean(false)
    private var released = false
    private var errorSent = false
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var positionLocation = -1
    private var textureLocation = -1
    private var matrixLocation = -1
    private var screenTextureId = 0
    private var cameraTextureId = 0
    private var screenTexture: SurfaceTexture? = null
    private var cameraTexture: SurfaceTexture? = null
    private var screenSurface: Surface? = null
    private var cameraSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var camera: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var screenFramePending = false
    private var cameraFramePending = false
    private var hasScreenFrame = false
    private var hasCameraFrame = false
    private var lastCameraTimestamp = 0L
    private val screenTransform = FloatArray(16)
    private val cameraTransform = FloatArray(16)
    private val composedTransform = FloatArray(16)
    private val adjustmentTransform = FloatArray(16)
    private val cropTransform = FloatArray(16)
    private val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val textureCoordinates = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(textureCoordinates); position(0) }

    fun start() { handler.post {
        if (closing.get()) return@post
        try {
            initializeGl()
            setupInputs()
            virtualDisplay = projection.createVirtualDisplay(
                "UmbraCam screen and front camera", displayWidth, displayHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, checkNotNull(screenSurface), null, handler,
            ) ?: error("Android could not create the screen input display")
            main.post(onProjectionReady)
            openCamera()
        } catch (error: Exception) { fail(error) }
    } }

    fun stop(onStopped: () -> Unit = {}) {
        if (!closing.compareAndSet(false, true)) { main.post(onStopped); return }
        handler.post {
            releaseResources()
            main.post(onStopped)
        }
    }

    private fun initializeGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 0)) { "EGL initialization failed" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No recordable EGL config" }
        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Encoder EGL surface creation failed" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "EGL make-current failed" }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        textureLocation = GLES20.glGetAttribLocation(program, "aTexture")
        matrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        check(positionLocation >= 0 && textureLocation >= 0 && matrixLocation >= 0) { "Compositor shader bindings unavailable" }
        GLES20.glViewport(0, 0, outputSize.width, outputSize.height)
    }

    private fun setupInputs() {
        val ids = IntArray(2)
        GLES20.glGenTextures(2, ids, 0)
        screenTextureId = ids[0]; cameraTextureId = ids[1]
        listOf(screenTextureId, cameraTextureId).forEach { id ->
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        screenTexture = SurfaceTexture(screenTextureId).apply {
            setDefaultBufferSize(displayWidth, displayHeight)
            setOnFrameAvailableListener({ screenFramePending = true; drawAvailableFrames() }, handler)
        }
        cameraTexture = SurfaceTexture(cameraTextureId).apply {
            setDefaultBufferSize(cameraConfig.width, cameraConfig.height)
            setOnFrameAvailableListener({ cameraFramePending = true; drawAvailableFrames() }, handler)
        }
        screenSurface = Surface(checkNotNull(screenTexture))
        cameraSurface = Surface(checkNotNull(cameraTexture))
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail(SecurityException("Camera permission was revoked before opening the front camera"))
            return
        }
        try { manager.openCamera(cameraConfig.id, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (closing.get()) { device.close(); return }
                camera = device
                try {
                    device.createCaptureSession(listOf(checkNotNull(cameraSurface)), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (closing.get()) { session.close(); return }
                            cameraSession = session
                            try {
                                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(checkNotNull(cameraSurface))
                                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    cameraConfig.aeFpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                                }
                                session.setRepeatingRequest(request.build(), null, handler)
                            } catch (error: Exception) { fail(error) }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) = fail(IllegalStateException("Front camera preview session failed"))
                        override fun onClosed(session: CameraCaptureSession) {
                            if (!closing.get()) fail(IllegalStateException("Front camera preview session closed"))
                        }
                    }, handler)
                } catch (error: Exception) { fail(error) }
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); fail(IllegalStateException("Front camera disconnected")) }
            override fun onError(device: CameraDevice, error: Int) { device.close(); fail(IllegalStateException("Front camera failed: $error")) }
            override fun onClosed(device: CameraDevice) {
                if (!closing.get()) fail(IllegalStateException("Front camera closed"))
            }
        }, handler) } catch (error: SecurityException) { fail(error) }
    }

    private fun drawAvailableFrames() {
        if (closing.get() || released || eglDisplay == EGL14.EGL_NO_DISPLAY || !hasScreenFrame && !screenFramePending) return
        try {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            if (screenFramePending) {
                checkNotNull(screenTexture).updateTexImage()
                checkNotNull(screenTexture).getTransformMatrix(screenTransform)
                screenFramePending = false; hasScreenFrame = true
            }
            if (cameraFramePending) {
                checkNotNull(cameraTexture).updateTexImage()
                checkNotNull(cameraTexture).getTransformMatrix(cameraTransform)
                cameraFramePending = false; hasCameraFrame = true
            }
            if (!hasScreenFrame || !hasCameraFrame) return
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (layout == ReactionLayout.SPLIT) {
                draw(screenTextureId, screenTransform, 0, outputSize.height / 2, outputSize.width, outputSize.height - outputSize.height / 2)
                draw(cameraTextureId, cameraSamplingTransform(), 0, 0, outputSize.width, outputSize.height / 2)
            } else {
                draw(screenTextureId, screenTransform, 0, 0, outputSize.width, outputSize.height)
                val insetWidth = (outputSize.width * 0.34f).toInt()
                val insetHeight = (outputSize.height * 0.27f).toInt()
                val margin = (outputSize.width * 0.035f).toInt()
                draw(cameraTextureId, cameraSamplingTransform(), outputSize.width - insetWidth - margin,
                    outputSize.height - insetHeight - margin, insetWidth, insetHeight)
            }
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
            check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "Compositor could not submit an encoder frame" }
            val timestamp = checkNotNull(cameraTexture).timestamp
            if (timestamp > lastCameraTimestamp) {
                lastCameraTimestamp = timestamp
                main.post { if (!closing.get()) onCameraFrameComposed(SystemClock.elapsedRealtime()) }
            }
        } catch (error: Exception) { fail(error) }
    }

    private fun cameraSamplingTransform(): FloatArray {
        val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val rotationDegrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val orientation = (cameraConfig.sensorOrientation + rotationDegrees) % 360
        Matrix.setIdentityM(adjustmentTransform, 0)
        Matrix.translateM(adjustmentTransform, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(adjustmentTransform, 0, orientation.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(adjustmentTransform, 0, -1f, 1f, 1f)
        Matrix.translateM(adjustmentTransform, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(composedTransform, 0, cameraTransform, 0, adjustmentTransform, 0)
        val sourceAspect = if (orientation % 180 == 0) cameraConfig.width.toFloat() / cameraConfig.height
            else cameraConfig.height.toFloat() / cameraConfig.width
        val targetAspect = when (layout) {
            ReactionLayout.PICTURE_IN_PICTURE -> (outputSize.width * 0.34f) / (outputSize.height * 0.27f)
            ReactionLayout.SPLIT -> outputSize.width.toFloat() / (outputSize.height / 2f)
        }
        val scaleX = if (sourceAspect > targetAspect) targetAspect / sourceAspect else 1f
        val scaleY = if (sourceAspect < targetAspect) sourceAspect / targetAspect else 1f
        Matrix.setIdentityM(cropTransform, 0)
        Matrix.translateM(cropTransform, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(cropTransform, 0, scaleX, scaleY, 1f)
        Matrix.translateM(cropTransform, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(adjustmentTransform, 0, composedTransform, 0, cropTransform, 0)
        System.arraycopy(adjustmentTransform, 0, composedTransform, 0, 16)
        return composedTransform
    }

    private fun draw(textureId: Int, matrix: FloatArray, x: Int, y: Int, width: Int, height: Int) {
        GLES20.glViewport(x, y, width, height)
        GLES20.glUseProgram(program)
        vertexBuffer.position(0)
        textureBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(textureLocation)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glUniformMatrix4fv(matrixLocation, 1, false, matrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureLocation)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type); GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
            val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(id) }; return id
        }
        val programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, shader(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(programId, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(programId)
        val status = IntArray(1); GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(programId) }
        return programId
    }

    private fun fail(error: Throwable) {
        if (closing.get() || errorSent) return
        errorSent = true
        main.post { if (!closing.get()) onFailure(error) }
    }

    private fun releaseResources() {
        if (released) return
        released = true
        runCatching { cameraSession?.stopRepeating() }
        runCatching { cameraSession?.abortCaptures() }
        runCatching { cameraSession?.close() }; cameraSession = null
        runCatching { camera?.close() }; camera = null
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { screenTexture?.setOnFrameAvailableListener(null) }
        runCatching { cameraTexture?.setOnFrameAvailableListener(null) }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
            runCatching { EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext) }
            if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
            if (screenTextureId != 0 || cameraTextureId != 0)
                runCatching { GLES20.glDeleteTextures(2, intArrayOf(screenTextureId, cameraTextureId), 0) }
        }
        runCatching { screenSurface?.release() }; screenSurface = null
        runCatching { cameraSurface?.release() }; cameraSurface = null
        runCatching { screenTexture?.release() }; screenTexture = null
        runCatching { cameraTexture?.release() }; cameraTexture = null
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE)
                runCatching { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
            if (eglSurface != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
            if (eglContext != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            runCatching { EGL14.eglTerminate(eglDisplay) }
        }
        eglSurface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT; eglDisplay = EGL14.EGL_NO_DISPLAY
        thread.quitSafely()
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexture;
            uniform mat4 uTextureMatrix;
            varying vec2 vTexture;
            void main() { gl_Position = aPosition; vTexture = (uTextureMatrix * aTexture).xy; }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uInput;
            varying vec2 vTexture;
            void main() { gl_FragColor = texture2D(uInput, vTexture); }
        """
    }
}
