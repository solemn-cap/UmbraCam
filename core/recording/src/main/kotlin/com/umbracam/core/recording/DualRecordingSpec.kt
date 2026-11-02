package com.umbracam.core.recording

/** A selected logical front/rear pair, not proof of a supported recording configuration. */
data class DualCameraPair(val front: CameraDescriptor, val rear: CameraDescriptor) {
    init {
        require(front.facing == CameraFacing.FRONT && rear.facing == CameraFacing.REAR)
        require(front.id != rear.id)
        require(listOf(front.id, rear.id).all { it.length <= 256 && it.none(Char::isISOControl) })
    }

    /** Recheck current independent inventories before binding; never derive pairs from larger groups. */
    fun isAdvertisedBy(metadata: ConcurrentCameraCapabilities, cameraXGroups: Set<Set<String>>): Boolean {
        val report = metadata as? ConcurrentCameraCapabilities.Report ?: return false
        val inventory = report.cameras.associateBy { it.id }
        if (inventory.size != report.cameras.size || inventory[front.id] != front || inventory[rear.id] != rear) return false
        val ids = setOf(front.id, rear.id)
        return ids in cameraXGroups && report.groups.any { group ->
            group.cameras.size == 2 && group.cameras.toSet() == setOf(front, rear)
        }
    }
}

enum class DualLayout { PICTURE_IN_PICTURE, SPLIT }
enum class DualOutputShape { PORTRAIT, LANDSCAPE }
enum class DualOutputRotation { DEGREES_0, DEGREES_90, DEGREES_180, DEGREES_270 }
enum class DualVideoProfile { HD_SDR_30 }

/** Frozen per logical session. Shape is explicit: target rotation alone does not specify sensor geometry.
 * The initial profile is a requested candidate, not a device performance promise.
 */
data class DualRecordingSpec(
    val pair: DualCameraPair,
    val layout: DualLayout = DualLayout.PICTURE_IN_PICTURE,
    val shape: DualOutputShape = DualOutputShape.PORTRAIT,
    val rotation: DualOutputRotation = DualOutputRotation.DEGREES_0,
    val microphone: Boolean = false,
    val profile: DualVideoProfile = DualVideoProfile.HD_SDR_30,
) {
    /** The currently shipped dual encoder contract is intentionally narrow and explicit. */
    fun isSupportedByInitialBackend(apiLevel: Int): Boolean =
        apiLevel >= 30 && profile == DualVideoProfile.HD_SDR_30 &&
            shape == DualOutputShape.PORTRAIT && rotation == DualOutputRotation.DEGREES_0

    val requiredSources: Set<CaptureSource> get() = if (microphone)
        setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA, CaptureSource.MICROPHONE)
    else setOf(CaptureSource.FRONT_CAMERA, CaptureSource.REAR_CAMERA)
    val outputWidth: Int get() = if (shape == DualOutputShape.PORTRAIT) 720 else 1280
    val outputHeight: Int get() = if (shape == DualOutputShape.PORTRAIT) 1280 else 720
    val geometry: DualCompositionGeometry get() = DualCompositionGeometry.forLayout(layout, shape)
}

/** Final output coordinates, origin top-left. CameraX rotation/mirror mapping belongs in its adapter. */
data class NormalizedRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 })
        require(left < right && top < bottom)
    }
}

data class DualCompositionGeometry(val rear: NormalizedRect, val front: NormalizedRect) {
    companion object {
        fun forLayout(layout: DualLayout, shape: DualOutputShape): DualCompositionGeometry = when {
            layout == DualLayout.PICTURE_IN_PICTURE -> DualCompositionGeometry(
                NormalizedRect(0.0, 0.0, 1.0, 1.0), NormalizedRect(.64, .04, .96, .36))
            shape == DualOutputShape.PORTRAIT -> DualCompositionGeometry(
                NormalizedRect(0.0, 0.0, 1.0, .5), NormalizedRect(0.0, .5, 1.0, 1.0))
            else -> DualCompositionGeometry(
                NormalizedRect(0.0, 0.0, .5, 1.0), NormalizedRect(.5, 0.0, 1.0, 1.0))
        }
    }
}
