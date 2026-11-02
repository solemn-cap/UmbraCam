package com.umbracam.app.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.umbracam.app.NeonPanel
import com.umbracam.app.NeonOutline
import com.umbracam.app.RecordAction
import com.umbracam.app.SuiteIcon
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.umbracam.app.R
import com.umbracam.core.recording.RecordingState
import com.umbracam.core.recording.CaptureThermalPolicy
import com.umbracam.core.recording.RecordingErrorCode
import com.umbracam.core.recording.SingleCameraSessionCoordinator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CameraStudioScreen(viewModel: CameraStudioViewModel, onCheckCameraSupport: () -> Unit = {}, onRecordings: () -> Unit = {},
    sosOpenRequest: Int = 0, onOpenPro: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val thermal by viewModel.thermalStatus.collectAsStateWithLifecycle()
    val heatBlocked = !CaptureThermalPolicy.canStart(thermal)
    val options by viewModel.dualOptions.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (options.result == null) viewModel.checkDualOptions() }
    // Preview rebinds clear transient camera issues; durable session warnings must remain visible.
    val visibleIssue = if (heatBlocked) CameraIssue.THERMAL else ui.issue ?: when (session?.error?.code) {
        RecordingErrorCode.THERMAL_LIMIT -> CameraIssue.THERMAL
        RecordingErrorCode.PREVIOUS_SESSION_INTERRUPTED -> CameraIssue.PREVIOUS_SESSION
        RecordingErrorCode.PERSISTENCE_FAILED -> CameraIssue.METADATA
        else -> if (session?.state == RecordingState.SAVED && session?.error != null) CameraIssue.SAVED_PARTIAL else null
    }
    var cameraGranted by remember { mutableStateOf(viewModel.hasPermission(Manifest.permission.CAMERA)) }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || viewModel.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) }
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState == Lifecycle.State.RESUMED) }
    var pendingAction by remember { mutableIntStateOf(0) }
    var handledSosRequest by rememberSaveable { mutableIntStateOf(0) }
    var preparedSosRequest by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var teleprompterDialog by remember { mutableStateOf(false) }
    var teleprompterVisible by rememberSaveable { mutableStateOf(false) }
    var teleprompterRunning by rememberSaveable { mutableStateOf(false) }
    var teleprompterSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    val creatorToolsAvailable = remember(context) { (context.applicationContext as com.umbracam.app.UmbraCamApplication).entitlements.has(com.umbracam.core.recording.PremiumFeature.CREATOR_TOOLS) }
    val promptPreferences = remember(context) { context.getSharedPreferences("creator_tools", android.content.Context.MODE_PRIVATE) }
    var teleprompterText by remember(context) { mutableStateOf(promptPreferences.getString("teleprompter_script", "").orEmpty()) }
    val promptScroll = rememberScrollState()
    LaunchedEffect(teleprompterVisible,teleprompterRunning,teleprompterSpeed,teleprompterText) {
        while(isActive && teleprompterVisible && teleprompterRunning) {
            if(promptScroll.maxValue>0 && promptScroll.value>=promptScroll.maxValue) { teleprompterRunning=false; break }
            promptScroll.scrollBy(teleprompterSpeed * 1f)
            delay(50)
        }
    }
    val busy = session?.state in SingleCameraSessionCoordinator.busyStates
    val sosPending = pendingAction == 3
    val settingsEnabled = !busy && !sosPending && !ui.serviceRunning && !ui.initializing && !ui.loading
    val dualAvailable = ui.dualSpecification == null || (!options.checking && ui.dualSpecification!!.pair in options.pairs)
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setMicrophone(true) // Rechecks the actual grant; denial keeps silent video available.
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        if (!granted) { pendingAction = 0; viewModel.notificationPermissionDenied() }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) { if (pendingAction == 3) pendingAction = 0; viewModel.cameraPermissionDenied() }
        else if (pendingAction == 3 && Build.VERSION.SDK_INT >= 33 && !viewModel.hasPermission(Manifest.permission.POST_NOTIFICATIONS))
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(sosOpenRequest) {
        if (sosOpenRequest > handledSosRequest) {
            handledSosRequest = sosOpenRequest // A recreation must never replay an explicit start request.
            pendingAction = 3
        }
    }
    LaunchedEffect(sosOpenRequest, pendingAction, ui.initializing) {
        if (sosOpenRequest > 0 && pendingAction == 3 && preparedSosRequest != sosOpenRequest && !ui.initializing) {
            preparedSosRequest = sosOpenRequest
            if (!viewModel.prepareSosMode()) pendingAction = 0
            else if (!viewModel.hasPermission(Manifest.permission.CAMERA)) cameraPermission.launch(Manifest.permission.CAMERA)
            else if (Build.VERSION.SDK_INT >= 33 && !viewModel.hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(sosOpenRequest, pendingAction) {
        if (sosOpenRequest > 0 && pendingAction == 3) {
            delay(30_000)
            if (pendingAction == 3) { pendingAction = 0; viewModel.quickStartUnavailable() }
        }
    }
    LaunchedEffect(resumed, cameraGranted, notificationGranted, ui.ready, ui.loading, ui.initializing, pendingAction) {
        if (resumed && cameraGranted && notificationGranted && ui.ready && !ui.loading && !ui.initializing && pendingAction != 0) {
            val action = pendingAction
            when (action) {
                2 -> { pendingAction = 0; viewModel.recover() }
                3 -> if (viewModel.canStartSos()) { pendingAction = 0; viewModel.startSos() }
                    else viewModel.prepareSosMode()
                else -> { pendingAction = 0; viewModel.start() }
            }
        }
    }
    val requestRecording: (Boolean) -> Unit = { recovery ->
        if (Build.VERSION.SDK_INT >= 33 && !viewModel.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            pendingAction = if (recovery) 2 else 1
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (recovery) viewModel.recover() else viewModel.start()
    }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = viewModel.hasPermission(Manifest.permission.CAMERA)
                notificationGranted = Build.VERSION.SDK_INT < 33 || viewModel.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                resumed = true
                viewModel.onUiResumed(owner)
            }
            if (event == Lifecycle.Event.ON_PAUSE) { resumed = false; viewModel.onForegroundLost() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.detach(owner) }
    }

    val hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    val previewHeight = if(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700) 220.dp else 300.dp
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(visibleIssue==CameraIssue.THERMAL) NeonPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(if(heatBlocked) R.string.error_thermal else R.string.error_thermal_previous))
            OutlinedButton(onClick=viewModel::retryPreview,enabled=!busy && !heatBlocked) { Text(stringResource(R.string.camera_retry)) }
        }
        if(ui.initializing) Text(stringResource(R.string.checking_previous_recording))
        if(!cameraGranted) {
            Text(stringResource(R.string.camera_intro))
            Text(stringResource(R.string.camera_permission_reason))
            com.umbracam.app.NeonButton(stringResource(R.string.camera_enable),{cameraPermission.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",context.packageName,null)))}) {
                Text(stringResource(R.string.open_app_settings))
            }
        } else {
            if(!heatBlocked || busy) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp,NeonOutline,RoundedCornerShape(24.dp))) {
                AndroidView(factory={PreviewView(it).apply {
                    implementationMode=PreviewView.ImplementationMode.COMPATIBLE
                    scaleType=PreviewView.ScaleType.FIT_CENTER
                }},modifier=Modifier.fillMaxWidth().height(previewHeight),onReset=null,
                    onRelease={viewModel.detach(owner)},update={viewModel.attach(owner,it)})
                if(teleprompterVisible && teleprompterText.isNotBlank()) {
                    Surface(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(.78f).heightIn(max=previewHeight*.62f),
                        color=androidx.compose.ui.graphics.Color.Black.copy(alpha=.72f),shape=RoundedCornerShape(16.dp)) {
                        Text(teleprompterText,Modifier.verticalScroll(promptScroll).padding(14.dp),style=MaterialTheme.typography.titleMedium,
                            color=androidx.compose.ui.graphics.Color.White)
                    }
                }
                ui.quality?.let { quality ->
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        TextButton(onClick={expanded=true},enabled=settingsEnabled) {
                            Text(stringResource(when(quality) { Quality.UHD -> R.string.quality_uhd; Quality.FHD -> R.string.quality_fhd; Quality.HD -> R.string.quality_hd; else -> R.string.quality_sd }),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if(ui.loading) Text(stringResource(R.string.camera_loading))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(CameraLens.FRONT,CameraLens.REAR).forEach { lens ->
                    FilterChip(selected=ui.dualSpecification==null && ui.lens==lens,
                        enabled=settingsEnabled && lens in ui.lenses,
                        onClick={viewModel.chooseSingleMode();viewModel.chooseLens(lens)},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),
                        label={Text(stringResource(if(lens==CameraLens.FRONT) R.string.camera_front else R.string.camera_rear))})
                }
                FilterChip(selected=ui.dualSpecification!=null,onClick=viewModel::chooseDualMode,
                    enabled=settingsEnabled && !options.checking && options.pairs.isNotEmpty(),modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    label={Text(stringResource(R.string.suite_both_cameras))})
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=ui.audioEnabled,
                enabled=settingsEnabled && hasMicrophone,role=Role.Switch,onValueChange={enabled ->
                    if(enabled && !viewModel.hasPermission(Manifest.permission.RECORD_AUDIO)) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    else viewModel.setMicrophone(enabled)
                }),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SuiteIcon(R.drawable.ic_suite_mic)
                Text(stringResource(if(hasMicrophone) R.string.camera_microphone else R.string.camera_no_microphone),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Switch(checked=ui.audioEnabled,onCheckedChange=null,enabled=settingsEnabled && hasMicrophone)
            }
            if(!ui.ready && !ui.loading && !busy && visibleIssue!=CameraIssue.THERMAL) OutlinedButton(onClick=viewModel::retryPreview) { Text(stringResource(R.string.camera_retry)) }
        }
        Text(stringResource(when(session?.state) {
            RecordingState.PREPARING -> R.string.recording_preparing
            RecordingState.RECORDING -> if (session?.kind == com.umbracam.core.recording.RecordingKind.SOS) R.string.sos_recording_active else R.string.recording_active
            RecordingState.RECOVERING -> R.string.recording_recovering
            RecordingState.INTERRUPTED -> R.string.recording_interrupted
            RecordingState.STOPPING -> R.string.recording_stopping
            RecordingState.SAVED -> R.string.recording_saved
            RecordingState.FAILED -> R.string.recording_failed
            else -> if (sosPending) R.string.sos_preparing else R.string.recording_idle
        }),Modifier.semantics { liveRegion=LiveRegionMode.Polite },style=MaterialTheme.typography.titleMedium)
        if(session?.state==RecordingState.RECORDING) Text(stringResource(R.string.suite_timer,ui.durationMillis/3_600_000,ui.durationMillis/60_000%60,ui.durationMillis/1000%60),
            Modifier.fillMaxWidth(),style=MaterialTheme.typography.displaySmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        visibleIssue?.takeIf {it!=CameraIssue.THERMAL}?.let { Text(stringResource(it.messageResource),Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
        if(ui.issue==CameraIssue.NOTIFICATION_PERMISSION) TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",context.packageName,null)))}) { Text(stringResource(R.string.open_app_settings)) }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onRecordings) { SuiteIcon(R.drawable.ic_suite_folder,stringResource(R.string.library_open)) }
            RecordAction(stringResource(if(busy) R.string.camera_stop else R.string.camera_record),busy,
                if(busy) session?.state in setOf(RecordingState.PREPARING,RecordingState.RECOVERING,RecordingState.RECORDING)
                else !sosPending && cameraGranted && ui.ready && dualAvailable && !ui.initializing && !ui.serviceRunning) {
                if(busy) viewModel.stop() else requestRecording(false)
            }
            IconButton(onClick={viewModel.chooseLens(if(ui.lens==CameraLens.FRONT) CameraLens.REAR else CameraLens.FRONT)},
                enabled=settingsEnabled && ui.dualSpecification==null && ui.lenses.size>1) { SuiteIcon(R.drawable.ic_suite_camera,stringResource(R.string.suite_switch_camera)) }
        }
        if(!busy && session?.error?.recoverable==true) OutlinedButton(onClick={requestRecording(true)},enabled=cameraGranted && ui.ready && dualAvailable && !ui.initializing && !ui.serviceRunning) { Text(stringResource(R.string.camera_resume)) }
        TextButton(onClick={expanded=true},enabled=settingsEnabled) { Text(stringResource(R.string.suite_options)) }
        OutlinedButton(onClick={if(creatorToolsAvailable) teleprompterDialog=true else onOpenPro()},enabled=!busy && cameraGranted,modifier=Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pro_teleprompter))
        }
        if(cameraGranted) Text(stringResource(R.string.camera_intro),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        session?.completedSegments?.lastOrNull()?.let { Text(stringResource(R.string.camera_saved_file,it.storageId),style=MaterialTheme.typography.bodySmall) }
        if((session?.completedSegments?.size?:0)>1) Text(stringResource(R.string.camera_segment_count,session!!.completedSegments.size))
        TextButton(onClick=onCheckCameraSupport,enabled=!busy) { Text(stringResource(R.string.camera_support_check)) }
        Text(stringResource(R.string.camera_storage_notice),style=MaterialTheme.typography.bodySmall)
    }
    if(expanded) ModalBottomSheet(onDismissRequest={expanded=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.suite_options),style=MaterialTheme.typography.titleLarge)
            CameraModeControls(ui.dualSpecification,options,settingsEnabled,viewModel::chooseSingleMode,viewModel::chooseDualMode,viewModel::checkDualOptions)
            if(ui.dualSpecification==null) {
                Text(stringResource(R.string.camera_quality))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ui.qualities.forEach { quality ->
                    FilterChip(selected=quality==ui.quality,enabled=settingsEnabled,onClick={viewModel.chooseQuality(quality)},label={
                        Text(stringResource(when(quality) { Quality.UHD -> R.string.quality_uhd; Quality.FHD -> R.string.quality_fhd; Quality.HD -> R.string.quality_hd; else -> R.string.quality_sd }))
                    })
                } }
            } else DualSettings(ui.dualSpecification!!,options,settingsEnabled,viewModel::chooseDualPair,viewModel::chooseDualLayout,viewModel::checkDualOptions)
            TextButton(onClick={expanded=false}) { Text(stringResource(R.string.suite_close)) }
        }
    }
    if(teleprompterDialog) AlertDialog(onDismissRequest={teleprompterDialog=false},title={Text(stringResource(R.string.pro_teleprompter))},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pro_teleprompter_note),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(teleprompterText,{teleprompterText=it},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=8,
                label={Text(stringResource(R.string.pro_script))})
            Text(stringResource(R.string.pro_scroll_speed,(teleprompterSpeed*100).toInt()))
            Slider(value=teleprompterSpeed,onValueChange={teleprompterSpeed=it},valueRange=.3f..2f)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=teleprompterVisible,onClick={teleprompterVisible=!teleprompterVisible},label={Text(stringResource(R.string.pro_prompt_show))})
                FilterChip(selected=teleprompterRunning,onClick={teleprompterRunning=!teleprompterRunning},label={Text(stringResource(if(teleprompterRunning) R.string.pro_prompt_pause else R.string.pro_prompt_start))})
            }
        }},confirmButton={TextButton(onClick={promptPreferences.edit().putString("teleprompter_script",teleprompterText.take(12000)).apply();teleprompterDialog=false}) { Text(stringResource(R.string.library_save)) }},
        dismissButton={TextButton(onClick={teleprompterRunning=false;teleprompterDialog=false}) { Text(stringResource(R.string.suite_close)) }})
}
private val CameraIssue.messageResource: Int get() = when (this) {
    CameraIssue.THERMAL -> R.string.error_thermal
    CameraIssue.CAMERA_PERMISSION -> R.string.error_camera_permission
    CameraIssue.MICROPHONE_PERMISSION -> R.string.error_microphone_permission
    CameraIssue.NOTIFICATION_PERMISSION -> R.string.error_notification_permission
    CameraIssue.NO_CAMERA -> R.string.error_no_camera
    CameraIssue.CAMERA_UNAVAILABLE -> R.string.error_camera_unavailable
    CameraIssue.UNSUPPORTED_QUALITY -> R.string.error_quality
    CameraIssue.STORAGE -> R.string.error_storage
    CameraIssue.PREPARE -> R.string.error_prepare
    CameraIssue.AUDIO_UNAVAILABLE -> R.string.error_audio
    CameraIssue.FINALIZATION -> R.string.error_finalize
    CameraIssue.NO_DATA -> R.string.error_no_data
    CameraIssue.INTERRUPTED -> R.string.error_interrupted
    CameraIssue.SAVED_PARTIAL -> R.string.error_saved_partial
    CameraIssue.PREVIOUS_SESSION -> R.string.error_previous_session
    CameraIssue.METADATA -> R.string.error_metadata
    CameraIssue.RECOVERY_SETTINGS -> R.string.error_recovery_settings
}

