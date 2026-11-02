package com.umbracam.app.library

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.umbracam.app.UmbraCamApplication
import com.umbracam.app.camera.AndroidVideoInspector
import com.umbracam.app.camera.RecordingSessionJournal
import com.umbracam.app.vault.VaultRecordingLabels
import androidx.fragment.app.FragmentActivity
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
    val recordings: List<CatalogRecording> = emptyList(), val playing: String? = null, val message: LibraryMessage? = null,
    val vaultWarning: Boolean = false, val locked: Boolean = false, val biometricLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = true)

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as UmbraCamApplication
    private val cameraJournal = RecordingSessionJournal(File(app.noBackupFilesDir, "camera/sessions"))
    private val vaultAccess = app.vaultAccess
    private val catalog = RecordingCatalog(mapOf("camera" to File(app.noBackupFilesDir, "camera"),
        "screen" to File(app.noBackupFilesDir, "screen")), AndroidVideoInspector(), cameraJournal::sosRecordingIds,
        app.vaultCodec, File(app.cacheDir, "vault-transient"), app.vaultMetadata)
    private val mutableState = MutableStateFlow(LibraryState(locked = vaultAccess.enabled,
        biometricLockEnabled = vaultAccess.enabled, biometricAvailable = vaultAccess.canAuthenticate))
    val state = mutableState.asStateFlow()
    private var exportKey: String? = null
    private var playbackTemporary: File? = null
    fun refresh() {
        if (state.value.working) return
        if (vaultAccess.enabled && state.value.locked) { mutableState.update { it.copy(loading = false) }; return }
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { app.vaultMigration.ensureMigrated() to catalog.list() }
                mutableState.update { it.copy(recordings = result.second, loading = false, vaultWarning = result.first.failed > 0) }
            }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, message = LibraryMessage.FAILED) } }
        }
    }
    fun playbackFile(key: String): File? = playbackTemporary?.takeIf { state.value.playing == key && it.isFile }
    fun thumbnailFile(key: String): File? = runCatching { catalog.resolve(key).takeUnless { app.vaultCodec.isEncrypted(it) } }.getOrNull()
    fun play(key: String) {
        if (state.value.working || state.value.locked || app.libraryOperation) return
        app.libraryOperation = true
        mutableState.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val temporary = File.createTempFile("play-", ".mp4", File(app.cacheDir, "vault-transient").apply { mkdirs() })
            try {
                val playable = withContext(Dispatchers.IO) { catalog.materializeForPlayback(key, temporary) }
                playbackTemporary = playable
                mutableState.update { it.copy(playing = key, working = false) }
            } catch (_: Exception) {
                temporary.delete()
                mutableState.update { it.copy(working = false, message = LibraryMessage.PLAYBACK_FAILED) }
            } finally { app.libraryOperation = false }
        }
    }
    fun closePlayer() {
        playbackTemporary?.delete(); playbackTemporary = null
        mutableState.update { it.copy(playing = null) }
    }
    fun playbackFailed() { closePlayer(); mutableState.update { it.copy(message = LibraryMessage.PLAYBACK_FAILED) } }
    fun lockIfNeeded() {
        closePlayer()
        if (vaultAccess.enabled) mutableState.update { it.copy(locked = true, recordings = emptyList()) }
    }
    fun requestUnlock(activity: FragmentActivity) {
        if (!vaultAccess.enabled || !state.value.locked) return
        vaultAccess.authenticate(activity) { accepted ->
            if (accepted) { mutableState.update { it.copy(locked = false) }; refresh() }
        }
    }
    fun requestBiometricLock(activity: FragmentActivity, enabled: Boolean) {
        if (!state.value.biometricAvailable || state.value.working) return
        vaultAccess.authenticate(activity) { accepted ->
            if (accepted) {
                vaultAccess.setEnabled(enabled)
                mutableState.update { it.copy(biometricLockEnabled = enabled, locked = false, message = null) }
                refresh()
            }
        }
    }
    fun rename(key: String, title: String) = metadataOperation { catalog.rename(key, title) }
    fun setCategory(key: String, category: String) = metadataOperation { catalog.setCategory(key, category) }
    private fun metadataOperation(action: () -> Unit) {
        if (state.value.working || state.value.locked || app.libraryOperation) return
        app.libraryOperation = true; mutableState.update { it.copy(working = true) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (_: Exception) { mutableState.update { it.copy(message = LibraryMessage.FAILED) } }
            finally { app.libraryOperation = false; mutableState.update { it.copy(working = false) }; refresh() }
        }
    }
    fun prepareExport(key: String): Boolean {
        if (state.value.working || state.value.locked || exportKey != null) return false
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
    fun share(key: String) {
        if (state.value.working || state.value.locked || app.libraryOperation) return
        app.libraryOperation = true; mutableState.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                val shared = withContext(Dispatchers.IO) {
                    val temp = File.createTempFile("share-", ".mp4", File(app.cacheDir, "vault-transient").apply { mkdirs() })
                    runCatching { catalog.materializeForPlayback(key, temp) }.getOrElse { temp.delete(); throw it }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.files", shared)
                val intent = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                    .setClipData(android.content.ClipData.newUri(app.contentResolver, "UmbraCam recording", uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { mutableState.update { it.copy(message = LibraryMessage.FAILED) } }
            finally { app.libraryOperation = false; mutableState.update { it.copy(working = false) } }
        }
    }
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
