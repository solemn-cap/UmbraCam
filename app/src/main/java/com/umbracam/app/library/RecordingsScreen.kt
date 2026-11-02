package com.umbracam.app.library

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.*
import androidx.lifecycle.*
import com.umbracam.app.*
import com.umbracam.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingsScreen(model: RecordingsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<CatalogRecording?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"),model::exportTo)
    val owner=LocalLifecycleOwner.current
    val keyboard=LocalSoftwareKeyboardController.current
    DisposableEffect(owner,model) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) model.closePlayer() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.closePlayer() }
    }
    LaunchedEffect(model) { model.refresh() }
    val cameraLabel=stringResource(R.string.library_camera)
    val screenLabel=stringResource(R.string.library_screen)
    val dateFormat=remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT) }
    val visible=filterRecordings(state.recordings,filter,query) { recording ->
        (if(recording.source=="camera") cameraLabel else screenLabel)+" "+dateFormat.format(Date(recording.modifiedMillis))
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=16.dp)) {
        item {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(24.dp),
                label={Text(stringResource(R.string.suite_search))},leadingIcon={SuiteIcon(R.drawable.ic_suite_search)},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("all" to R.string.suite_all,"camera" to R.string.suite_camera_permission,"screen" to R.string.suite_screen_filter).forEach { (key,label) ->
                    FilterChip(selected=filter==key,onClick={filter=key},label={Text(stringResource(label))})
                }
            }
            Text(stringResource(R.string.library_privacy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=model::refresh,enabled=!state.loading && !state.working) { Text(stringResource(R.string.library_refresh)) }
        }
        state.message?.let { message -> item {
            Text(stringResource(when(message) {
                LibraryMessage.EXPORTED -> R.string.library_exported
                LibraryMessage.DELETED -> R.string.library_deleted
                LibraryMessage.FAILED -> R.string.library_failed
                LibraryMessage.EXPORT_FAILED -> R.string.library_export_failed
                LibraryMessage.CAPTURE_BUSY -> R.string.library_capture_busy
                LibraryMessage.CANCELLED -> R.string.library_cancelled
                LibraryMessage.PLAYBACK_FAILED -> R.string.library_playback_failed
            }),Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        } }
        if(state.working) item { Text(stringResource(R.string.library_working)) }
        if(state.loading) item { Text(stringResource(R.string.library_loading)) }
        else if(state.recordings.isEmpty()) item { Text(stringResource(R.string.library_empty)) }
        else if(visible.isEmpty()) item { Text(stringResource(R.string.suite_no_matches)) }
        items(visible,key={it.key}) { recording ->
            var menu by remember { mutableStateOf(false) }
            val kind=stringResource(if(recording.source=="screen") R.string.library_screen else R.string.library_camera)
            val date=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(recording.modifiedMillis))
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    RecordingThumbnail(model,recording)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(kind,style=MaterialTheme.typography.titleSmall)
                        Text(date,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.library_details,(recording.durationMillis?:0)/1000,recording.bytes/1024),style=MaterialTheme.typography.bodySmall)
                        if(!recording.readable) Text(stringResource(R.string.library_unreadable),style=MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        IconButton(onClick={menu=true},enabled=!state.working,modifier=Modifier.testTag("recording-actions")) {
                            SuiteIcon(R.drawable.ic_suite_more,stringResource(R.string.suite_actions)+", "+kind+", "+date)
                        }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text(stringResource(R.string.library_play))},enabled=recording.readable,onClick={menu=false;model.play(recording.key)})
                            DropdownMenuItem(text={Text(stringResource(R.string.library_export))},onClick={menu=false;if(model.prepareExport(recording.key)) export.launch("UmbraCam_${recording.source}_${recording.name}")})
                            DropdownMenuItem(text={Text(stringResource(R.string.library_delete))},onClick={menu=false;deleting=recording})
                        }
                    }
                }
                if(state.playing==recording.key) {
                    val file=model.playbackFile(recording.key)
                    if(file!=null) {
                        DisposableEffect(recording.key) { onDispose { model.closePlayer() } }
                        AndroidView(factory={context -> RecordingPlayerView(context,file.path,model::playbackFailed)},
                            modifier=Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),onRelease={it.close()})
                        OutlinedButton(onClick=model::closePlayer) { Text(stringResource(R.string.library_close_player)) }
                    } else Text(stringResource(R.string.library_playback_failed))
                }
                HorizontalDivider(color=NeonOutline.copy(alpha=.45f))
            }
        }
    }
    deleting?.let { recording -> AlertDialog(onDismissRequest={deleting=null},
        title={Text(stringResource(R.string.library_delete_title))},
        text={Text(stringResource(R.string.library_delete_detail,DateFormat.getDateTimeInstance().format(Date(recording.modifiedMillis))))},
        confirmButton={TextButton(onClick={deleting=null;model.delete(recording.key)}) { Text(stringResource(R.string.library_delete)) }},
        dismissButton={TextButton(onClick={deleting=null}) { Text(stringResource(R.string.library_keep)) }}) }
}

@Composable
private fun RecordingThumbnail(model: RecordingsViewModel, recording: CatalogRecording) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,recording.key) {
        value=withContext(Dispatchers.IO) {
            val reader=MediaMetadataRetriever()
            try { reader.setDataSource(model.playbackFile(recording.key)?.path ?: return@withContext null)
                reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,192,128)
            } catch (_: Exception) { null } finally { reader.release() }
        }
    }
    Box(Modifier.size(width=88.dp,height=66.dp).clip(RoundedCornerShape(12.dp)).background(NeonPanelColor),contentAlignment=Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            ?: SuiteIcon(if(recording.source=="camera") R.drawable.ic_suite_camera else R.drawable.ic_suite_screen)
    }
}
