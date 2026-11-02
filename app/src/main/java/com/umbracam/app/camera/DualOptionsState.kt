package com.umbracam.app.camera

import com.umbracam.core.recording.*

/** Advertised choices only. The capture adapter independently validates these again at bind. */
data class DualOptionsState(
    val checking: Boolean = false,
    val result: ConcurrentCameraCapabilities? = null,
) {
    val pairs: List<DualCameraPair> get() {
        val report = result as? ConcurrentCameraCapabilities.Report ?: return emptyList()
        val inventory = report.cameras.associateBy { it.id }
        if (inventory.size != report.cameras.size) return emptyList()
        return report.groups.mapNotNull { group ->
            if (group.cameras.size != 2 || group.cameras.any { inventory[it.id] != it }) return@mapNotNull null
            val front = group.cameras.singleOrNull { it.facing == CameraFacing.FRONT } ?: return@mapNotNull null
            val rear = group.cameras.singleOrNull { it.facing == CameraFacing.REAR } ?: return@mapNotNull null
            runCatching { DualCameraPair(front, rear) }.getOrNull()
        }.distinct().sortedWith(compareBy({ it.front.id }, { it.rear.id }))
    }
}
