package com.umbracam.app.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umbracam.app.R
import com.umbracam.core.recording.*

@Composable
internal fun CameraModeControls(spec: DualRecordingSpec?, options: DualOptionsState, enabled: Boolean,
    onSingle: () -> Unit, onDual: () -> Unit, onCheck: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.camera_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = spec == null, enabled = enabled, onClick = onSingle,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                label = { Text(stringResource(R.string.camera_mode_single)) })
            FilterChip(selected = spec != null, enabled = enabled && !options.checking && options.pairs.isNotEmpty(), onClick = onDual,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                label = { Text(stringResource(R.string.camera_mode_dual)) })
        }
        if (options.checking || options.pairs.isEmpty()) {
            Text(stringResource(when {
                options.checking || options.result == null -> R.string.camera_dual_checking
                options.result == ConcurrentCameraCapabilities.UnsupportedPlatform -> R.string.camera_dual_old
                options.result is ConcurrentCameraCapabilities.Unavailable -> R.string.camera_dual_error
                else -> R.string.camera_dual_none
            }), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            if (spec == null && !options.checking && options.result != null && options.result != ConcurrentCameraCapabilities.UnsupportedPlatform) {
                TextButton(onClick = onCheck, enabled = enabled) { Text(stringResource(R.string.camera_support_again)) }
            }
        }
        if (spec != null) Text(stringResource(R.string.camera_dual_limit), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DualSettings(spec: DualRecordingSpec, options: DualOptionsState, enabled: Boolean,
    onPair: (DualCameraPair) -> Unit, onLayout: (DualLayout) -> Unit, onCheck: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.camera_pair), style = MaterialTheme.typography.titleSmall)
        val index = options.pairs.indexOf(spec.pair)
        Text(if (options.pairs.size <= 1 || index < 0) stringResource(R.string.camera_pair_front_rear)
            else stringResource(R.string.camera_pair_option, index + 1))
        if (options.pairs.size > 1) Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(stringResource(R.string.camera_pair_change)) }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                options.pairs.forEachIndexed { i, pair -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.camera_pair_option, i + 1)) },
                    onClick = { expanded = false; onPair(pair) }) }
            }
        }
        if (options.result != null && !options.checking && index < 0) {
            Text(stringResource(R.string.camera_pair_changed), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onCheck, enabled = enabled && !options.checking) { Text(stringResource(R.string.camera_pair_refresh)) }
        Text(stringResource(R.string.camera_layout), style = MaterialTheme.typography.titleSmall)
        // Vertical options keep complete labels and touch targets at large system font sizes.
        DualLayout.entries.forEach { layout ->
            FilterChip(selected = spec.layout == layout, enabled = enabled, onClick = { onLayout(layout) }, modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(stringResource(if (layout == DualLayout.PICTURE_IN_PICTURE) R.string.camera_layout_pip else R.string.camera_layout_split)) })
        }
        Text(stringResource(if (spec.layout == DualLayout.PICTURE_IN_PICTURE) R.string.camera_pip_detail else R.string.camera_split_detail),
            style = MaterialTheme.typography.bodyMedium)
    }
}
