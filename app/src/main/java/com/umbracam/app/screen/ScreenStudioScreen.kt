package com.umbracam.app.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.umbracam.app.NeonPanel
import com.umbracam.app.NeonButton
import com.umbracam.app.RecordAction
import com.umbracam.app.SuiteIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbracam.app.R
import com.umbracam.core.recording.*
import java.util.concurrent.atomic.AtomicReference

private enum class AudioPermissionPurpose { MICROPHONE_TOGGLE, INTERNAL_AUDIO_TOGGLE, START }

@Composable
fun ScreenStudioScreen(model: ScreenStudioViewModel, onStart: (ReactionRecordingRequest) -> Unit, onRecordings: () -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val thermal by model.thermalStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reactionCapability = remember(context) { ReactionCapabilityDetector(context).read() }
    var modeName by rememberSaveable { mutableStateOf(ReactionMode.SCREEN_ONLY.name) }
    var layoutName by rememberSaveable { mutableStateOf(ReactionLayout.PICTURE_IN_PICTURE.name) }
    var microphoneEnabled by rememberSaveable { mutableStateOf(false) }
    var internalAudioEnabled by rememberSaveable { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<ReactionRecordingRequest?>(null) }
    var audioPermissionPurpose by remember { mutableStateOf<AudioPermissionPurpose?>(null) }
    val continueStart = remember { AtomicReference<((ReactionRecordingRequest) -> Unit)?>(null) }
    val mode = ReactionMode.valueOf(modeName)
    val layout = ReactionLayout.valueOf(layoutName)
    val request = ReactionRecordingRequest(mode = mode, layout = layout, microphone = microphoneEnabled, internalAudio = internalAudioEnabled)
    val requestAvailable = request.validate(reactionCapability).isSuccess
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingStart
        if (granted && pending != null) continueStart.get()?.invoke(pending)
        else { pendingStart = null; if (!granted) model.notificationDenied() }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val purpose = audioPermissionPurpose; audioPermissionPurpose = null
        when (purpose) {
            AudioPermissionPurpose.MICROPHONE_TOGGLE -> microphoneEnabled = granted
            AudioPermissionPurpose.INTERNAL_AUDIO_TOGGLE -> internalAudioEnabled = granted
            AudioPermissionPurpose.START -> {
                val pending = pendingStart
                if (granted && pending != null) continueStart.get()?.invoke(pending)
                else { pendingStart = null; if (!granted) model.microphonePermissionDenied() }
            }
            null -> Unit
        }
        if (!granted && purpose != AudioPermissionPurpose.START) model.microphonePermissionDenied()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingStart
        if (granted && pending != null) continueStart.get()?.invoke(pending)
        else { pendingStart = null; model.cameraPermissionDenied() }
    }
    continueStart.set { pending ->
        pendingStart = pending
        when {
            pending.mode == ReactionMode.SCREEN_AND_FRONT_CAMERA && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
                cameraPermission.launch(Manifest.permission.CAMERA)
            (pending.microphone || pending.internalAudio) && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                audioPermissionPurpose = AudioPermissionPurpose.START
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> { pendingStart = null; onStart(pending) }
        }
    }
    val busy = session?.state in SingleCameraSessionCoordinator.busyStates
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NeonPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                SuiteIcon(R.drawable.ic_suite_screen,modifier=Modifier.size(64.dp))
                Text(stringResource(when {
                    mode == ReactionMode.SCREEN_ONLY -> R.string.suite_screen_only
                    layout == ReactionLayout.PICTURE_IN_PICTURE -> R.string.reaction_pip_detail
                    else -> R.string.reaction_split_detail
                }),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.screen_intro),style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.reaction_mode_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == ReactionMode.SCREEN_ONLY,
                onClick = { modeName = ReactionMode.SCREEN_ONLY.name },
                enabled = !busy && !ui.awaitingConsent,
                label = { Text(stringResource(R.string.reaction_screen_only)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            FilterChip(selected = mode == ReactionMode.SCREEN_AND_FRONT_CAMERA,
                onClick = { modeName = ReactionMode.SCREEN_AND_FRONT_CAMERA.name },
                enabled = !busy && !ui.awaitingConsent && reactionCapability.frontCamera,
                label = { Text(stringResource(R.string.reaction_screen_camera)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
        }
        if (mode == ReactionMode.SCREEN_AND_FRONT_CAMERA) {
            val compositionText = when {
                !reactionCapability.frontCamera -> R.string.reaction_front_camera_unavailable
                !reactionCapability.supports(mode) -> R.string.reaction_compositor_unavailable
                layout == ReactionLayout.PICTURE_IN_PICTURE -> R.string.reaction_pip_detail
                else -> R.string.reaction_split_detail
            }
            Text(stringResource(compositionText),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.reaction_layout_title), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = layout == ReactionLayout.PICTURE_IN_PICTURE,
                    onClick = { layoutName = ReactionLayout.PICTURE_IN_PICTURE.name }, enabled = reactionCapability.supports(ReactionMode.SCREEN_AND_FRONT_CAMERA) && !busy,
                    label = { Text(stringResource(R.string.reaction_layout_pip)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                FilterChip(selected = layout == ReactionLayout.SPLIT,
                    onClick = { layoutName = ReactionLayout.SPLIT.name }, enabled = reactionCapability.supports(ReactionMode.SCREEN_AND_FRONT_CAMERA) && !busy,
                    label = { Text(stringResource(R.string.reaction_layout_split)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
        } else if (!reactionCapability.frontCamera) {
            Text(stringResource(R.string.reaction_front_camera_unavailable), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuiteIcon(R.drawable.ic_suite_mic)
            Text(stringResource(when {
                !reactionCapability.microphone -> R.string.camera_no_microphone
                !reactionCapability.microphoneCapture -> R.string.reaction_microphone_unavailable
                else -> R.string.reaction_microphone_enable
            }),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = microphoneEnabled, onCheckedChange = { enabled ->
                if (enabled && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    audioPermissionPurpose = AudioPermissionPurpose.MICROPHONE_TOGGLE
                    audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                else microphoneEnabled = enabled
            }, enabled = reactionCapability.microphone && reactionCapability.microphoneCapture && !busy,
                modifier = Modifier.testTag("reaction-microphone"))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(if (reactionCapability.internalAudioCapture) R.string.reaction_internal_audio_enable else R.string.reaction_internal_audio_unavailable),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = internalAudioEnabled, onCheckedChange = { enabled ->
                if (enabled && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    audioPermissionPurpose = AudioPermissionPurpose.INTERNAL_AUDIO_TOGGLE
                    audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                } else internalAudioEnabled = enabled
            }, enabled = reactionCapability.internalAudioCapture && !busy,
                modifier = Modifier.testTag("reaction-internal-audio"))
        }
        if (reactionCapability.internalAudioCapture) Text(stringResource(R.string.reaction_internal_audio_limits), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (microphoneEnabled && internalAudioEnabled) Text(stringResource(R.string.reaction_audio_separate_tracks),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(when {
            ui.initializing -> R.string.checking_previous_recording
            ui.awaitingConsent -> R.string.screen_awaiting
            session?.state == RecordingState.PREPARING -> R.string.recording_preparing
            session?.state == RecordingState.RECORDING -> R.string.recording_active
            session?.state == RecordingState.STOPPING -> R.string.recording_stopping
            session?.state == RecordingState.SAVED -> R.string.recording_saved
            session?.state == RecordingState.FAILED -> R.string.recording_failed
            else -> R.string.recording_idle
        }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (session?.state == RecordingState.RECORDING) Text(stringResource(R.string.suite_timer, ui.elapsedMillis/3_600_000, ui.elapsedMillis/60_000%60, ui.elapsedMillis/1000%60),style=MaterialTheme.typography.displayMedium)
        ui.issue?.let { issue -> Text(stringResource(when (issue) {
            ScreenIssue.THERMAL -> if(CaptureThermalPolicy.canStart(thermal)) R.string.error_thermal_previous else R.string.error_thermal
            ScreenIssue.CONSENT -> R.string.screen_cancelled
            ScreenIssue.CAMERA_PERMISSION -> R.string.error_camera_permission
            ScreenIssue.FRONT_CAMERA -> R.string.reaction_camera_failed
            ScreenIssue.CAMERA_BUSY -> R.string.screen_camera_busy
            ScreenIssue.MICROPHONE_PERMISSION -> R.string.error_microphone_permission
            ScreenIssue.AUDIO_CAPTURE -> R.string.reaction_audio_capture_failed
            ScreenIssue.NOTIFICATIONS -> R.string.error_notification_permission
            ScreenIssue.UNSUPPORTED -> R.string.screen_unsupported
            ScreenIssue.STORAGE -> R.string.error_storage
            ScreenIssue.CAPTURE -> R.string.screen_error
            ScreenIssue.REVOKED -> R.string.screen_revoked
            ScreenIssue.SIZE_CHANGED -> R.string.screen_size_changed
            ScreenIssue.FINALIZATION -> R.string.error_finalize
            ScreenIssue.PREVIOUS -> R.string.error_previous_session
            ScreenIssue.METADATA -> R.string.error_metadata
        }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (session?.state == RecordingState.SAVED && session?.error != null) Text(stringResource(R.string.error_saved_partial))
        if (busy) Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            RecordAction(stringResource(R.string.camera_stop),true,session?.state in setOf(RecordingState.PREPARING, RecordingState.RECORDING),model::stop)
        } else NeonButton(stringResource(if (mode == ReactionMode.SCREEN_ONLY) R.string.screen_start else R.string.reaction_start),onClick = {
            continueStart.get()?.invoke(request)
        }, enabled = requestAvailable && !ui.initializing && !ui.awaitingConsent && !ui.serviceRunning,modifier=Modifier.fillMaxWidth())
        if (ui.issue == ScreenIssue.NOTIFICATIONS) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.open_app_settings)) }
        OutlinedButton(onClick = onRecordings) { Text(stringResource(R.string.library_open)) }
        Text(stringResource(R.string.screen_limits), style = MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.library_privacy), style = MaterialTheme.typography.bodySmall)
    }
}
