package com.umbracam.core.recording

/** Public camera metadata only; a report is not permission or a successful capture configuration. */
enum class CameraFacing { FRONT, REAR, EXTERNAL, UNKNOWN }

data class CameraDescriptor(val id: String, val facing: CameraFacing) {
    init { require(id.isNotBlank()) }
}

data class ConcurrentCameraGroup(val cameras: List<CameraDescriptor>) {
    init {
        require(cameras.size >= 2)
        require(cameras.map { it.id }.toSet().size == cameras.size)
    }
    val includesFrontAndRear: Boolean get() = cameras.any { it.facing == CameraFacing.FRONT } && cameras.any { it.facing == CameraFacing.REAR }
}

enum class CapabilityReadFailure { ACCESS_DENIED, CAMERA_SERVICE, INCONSISTENT_SNAPSHOT }

sealed interface ConcurrentCameraCapabilities {
    data object UnsupportedPlatform : ConcurrentCameraCapabilities
    data class Unavailable(val reason: CapabilityReadFailure) : ConcurrentCameraCapabilities
    data class Report(val cameras: List<CameraDescriptor>, val groups: List<ConcurrentCameraGroup>) : ConcurrentCameraCapabilities {
        val advertisesFrontAndRear: Boolean get() = groups.any { it.includesFrontAndRear }
    }
}

/** Does not infer concurrency from lens count or fabricate pairs from larger advertised groups. */
object ConcurrentCameraAnalyzer {
    fun analyze(cameras: List<CameraDescriptor>, advertisedGroups: Set<Set<String>>): ConcurrentCameraCapabilities {
        val byId = cameras.associateBy { it.id }
        if (byId.size != cameras.size || advertisedGroups.any { ids -> ids.size < 2 || ids.any { it !in byId } }) {
            return ConcurrentCameraCapabilities.Unavailable(CapabilityReadFailure.INCONSISTENT_SNAPSHOT)
        }
        val ordered = cameras.sortedBy { it.id }.toList()
        val groups = advertisedGroups.map { ids -> ConcurrentCameraGroup(ids.sorted().map(byId::getValue)) }
            .sortedBy { it.cameras.joinToString("\u0000") { camera -> camera.id } }
        return ConcurrentCameraCapabilities.Report(ordered, groups)
    }
}
