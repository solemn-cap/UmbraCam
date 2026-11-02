package com.umbracam.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.umbracam.core.recording.*

/** Injectable metadata boundary. No method can open a camera or create a capture session. */
interface CameraInventorySource {
    val apiLevel: Int
    fun cameraIds(): List<String>
    fun facing(id: String): CameraFacing
    fun concurrentGroups(): Set<Set<String>>
}

class AndroidCameraInventorySource(context: Context) : CameraInventorySource {
    private val app = context.applicationContext
    private val manager get() = app.getSystemService(CameraManager::class.java) ?: error("Camera service unavailable")
    override val apiLevel get() = Build.VERSION.SDK_INT
    override fun cameraIds(): List<String> = manager.cameraIdList.toList()
    override fun facing(id: String): CameraFacing = when (manager.getCameraCharacteristics(id)[CameraCharacteristics.LENS_FACING]) {
        CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
        CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.REAR
        CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
        else -> CameraFacing.UNKNOWN
    }
    override fun concurrentGroups(): Set<Set<String>> = if (Build.VERSION.SDK_INT >= 30) {
        manager.concurrentCameraIds.map { it.toSet() }.toSet()
    } else emptySet()
}

class ConcurrentCameraDetector(private val source: CameraInventorySource) {
    fun read(): ConcurrentCameraCapabilities {
        if (source.apiLevel < 30) return ConcurrentCameraCapabilities.UnsupportedPlatform
        return try {
            val cameras = source.cameraIds().map { CameraDescriptor(it, source.facing(it)) }
            ConcurrentCameraAnalyzer.analyze(cameras, source.concurrentGroups())
        } catch (_: SecurityException) {
            ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.ACCESS_DENIED)
        } catch (_: Exception) {
            ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.CAMERA_SERVICE)
        }
    }
}
