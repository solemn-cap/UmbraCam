package com.umbracam.app.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.umbracam.app.UmbraCamApplication
import com.umbracam.app.camera.AndroidVideoInspector
import com.umbracam.app.camera.RecordingSessionJournal
import com.umbracam.core.recording.SingleCameraSessionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LibraryMessage { EXPORTED, DELETED, FAILED, EXPORT_FAILED, CAPTURE_BUSY, CANCELLED, PLAYBACK_FAILED }
data class LibraryState(val loading: Boolean = true, val working: Boolean = false,
    val recordings: List<CatalogRecording> = emptyList(), val playing: String? = null, val message: LibraryMessage? = null)

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as UmbraCamApplication
    private val cameraJournal = RecordingSessionJournal(File(app.noBackupFilesDir, "camera/sessions"))
    private val catalog = RecordingCatalog(mapOf("camera" to File(app.noBackupFilesDir, "camera"),
        "screen" to File(app.noBackupFilesDir, "screen")), AndroidVideoInspector(), cameraJournal::sosRecordingIds)
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()
    private var exportKey: String? = null
    fun refresh() {
        if (state.value.working) return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try { val entries = withContext(Dispatchers.IO) { catalog.list() }; mutableState.update { it.copy(recordings = entries, loading = false) } }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, message = LibraryMessage.FAILED) } }
        }
    }
    fun playbackFile(key: String): File? = runCatching { catalog.resolve(key) }.getOrNull()
    fun play(key: String) { mutableState.update { it.copy(playing = key, message = null) } }
    fun closePlayer() { mutableState.update { it.copy(playing = null) } }
    fun playbackFailed() { mutableState.update { it.copy(playing = null, message = LibraryMessage.PLAYBACK_FAILED) } }
    fun prepareExport(key: String): Boolean {
        if (state.value.working || exportKey != null) return false
        exportKey = key; return true
    }
    fun exportTo(uri: Uri?) {
        val key = exportKey ?: return
        exportKey = null
        if (uri == null) { mutableState.update { it.copy(message = LibraryMessage.CANCELLED) }; return }
        operation(LibraryMessage.EXPORTED) {
            catalog.input(key).use { input ->
                checkNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { output -> input.copyTo(output); output.flush() }
            }
        }
    }
    fun delete(key: String) { closePlayer(); operation(LibraryMessage.DELETED, key) { catalog.delete(key) } }
    private fun operation(success: LibraryMessage, deleted: String? = null, action: () -> Unit) {
        if (state.value.working || app.libraryOperation || app.recording.session.value?.state in SingleCameraSessionCoordinator.busyStates || app.screenRecording.isBusy) {
            mutableState.update { it.copy(message = LibraryMessage.CAPTURE_BUSY) }; return
        }
        app.libraryOperation = true
        mutableState.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                deleted?.let { key ->
                    val name = key.substringAfter('/')
                    if (key.startsWith("camera/")) app.recording.recordingDeleted(name) else app.screenRecording.recordingDeleted(name)
                }
                mutableState.update { it.copy(message = success) }
            } catch (_: Exception) { mutableState.update { it.copy(message = if (success == LibraryMessage.EXPORTED) LibraryMessage.EXPORT_FAILED else LibraryMessage.FAILED) } }
            finally {
                app.libraryOperation = false; mutableState.update { it.copy(working = false) }; refresh()
            }
        }
    }
}
