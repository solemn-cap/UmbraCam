package com.umbracam.app.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.umbracam.app.R
import com.umbracam.core.recording.*

@Composable
fun CameraSupportScreen(state: CameraSupportState, onCheck: () -> Unit, onOpenCamera: () -> Unit) {
    Text(stringResource(R.string.camera_support_intro), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onCheck, enabled = !state.checking) {
        Text(stringResource(if (state.checking) R.string.camera_support_checking else if (state.result == null) R.string.camera_support_check else R.string.camera_support_again))
    }
    val result = state.result
    if (!state.checking && result != null) {
        val heading = when (result) {
            ConcurrentCameraCapabilities.UnsupportedPlatform -> R.string.camera_support_old
            is ConcurrentCameraCapabilities.Unavailable -> R.string.camera_support_error
            is ConcurrentCameraCapabilities.Report -> when {
                result.cameras.isEmpty() -> R.string.camera_support_empty
                result.advertisesFrontAndRear -> R.string.camera_support_front_rear
                result.groups.isNotEmpty() -> R.string.camera_support_other
                else -> R.string.camera_support_none
            }
        }
        Text(stringResource(heading), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        when (result) {
            ConcurrentCameraCapabilities.UnsupportedPlatform -> Text(stringResource(R.string.camera_support_old_detail))
            is ConcurrentCameraCapabilities.Unavailable -> Text(stringResource(when (result.reason) {
                CapabilityReadFailure.ACCESS_DENIED -> R.string.camera_support_access
                CapabilityReadFailure.CAMERA_SERVICE -> R.string.camera_support_service
                CapabilityReadFailure.INCONSISTENT_SNAPSHOT -> R.string.camera_support_changed
            }))
            is ConcurrentCameraCapabilities.Report -> {
                result.groups.forEachIndexed { index, group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.camera_support_group, index + 1), style = MaterialTheme.typography.titleMedium)
                        group.cameras.forEach { camera ->
                            val label = stringResource(when (camera.facing) {
                                CameraFacing.FRONT -> R.string.camera_front
                                CameraFacing.REAR -> R.string.camera_rear
                                CameraFacing.EXTERNAL -> R.string.camera_external
                                CameraFacing.UNKNOWN -> R.string.camera_unknown
                            })
                            Text(stringResource(R.string.camera_support_camera, label, result.cameras.indexOf(camera) + 1))
                        }
                    }
                    HorizontalDivider()
                }
                Text(stringResource(R.string.camera_support_limit))
            }
        }
        if (result !is ConcurrentCameraCapabilities.Report || result.cameras.isNotEmpty()) {
            Text(stringResource(R.string.camera_support_fallback))
        }
    }
    TextButton(onClick = onOpenCamera) { Text(stringResource(R.string.camera_support_open)) }
}
