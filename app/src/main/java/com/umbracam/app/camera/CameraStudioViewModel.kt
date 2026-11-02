package com.umbracam.app.camera

import android.app.Application
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.umbracam.core.recording.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.umbracam.app.UmbraCamApplication

/** UI facade. Its destruction never owns or restarts an accepted service recording. */
class CameraStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as UmbraCamApplication).recording
    val ui = controller.ui
    val session = controller.session
    val thermalStatus = controller.thermalStatus
    private val mutableDualOptions = MutableStateFlow(DualOptionsState())
    val dualOptions = mutableDualOptions.asStateFlow()
    fun checkDualOptions() {
        if (mutableDualOptions.value.checking || session.value?.state in SingleCameraSessionCoordinator.busyStates) return
        mutableDualOptions.value = mutableDualOptions.value.copy(checking = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ConcurrentCameraDetector(AndroidCameraInventorySource(getApplication())).read() }
            mutableDualOptions.value = DualOptionsState(result = result)
        }
    }
    fun chooseDualMode() {
        if (ui.value.dualSpecification != null) return
        mutableDualOptions.value.pairs.firstOrNull()?.let { controller.selectDual(DualRecordingSpec(it, microphone = ui.value.audioEnabled)) }
    }
    fun chooseSingleMode() { if (ui.value.dualSpecification != null) controller.selectDual(null) }
    fun chooseDualPair(pair: DualCameraPair) {
        if (pair in mutableDualOptions.value.pairs) ui.value.dualSpecification?.let { controller.selectDual(it.copy(pair = pair)) }
    }
    fun chooseDualLayout(layout: DualLayout) { ui.value.dualSpecification?.let { controller.selectDual(it.copy(layout = layout)) } }
    fun hasPermission(permission: String) = controller.hasPermission(permission)
    fun attach(owner: LifecycleOwner, view: PreviewView) = controller.attach(owner, view)
    fun detach(owner: LifecycleOwner) = controller.detach(owner)
    fun onUiResumed(owner: LifecycleOwner) = controller.onUiResumed(owner)
    fun onForegroundLost() = controller.onForegroundLost()
    fun permissionsChanged() = controller.permissionsChanged()
    fun cameraPermissionDenied() = controller.cameraPermissionDenied()
    fun notificationPermissionDenied() = controller.notificationPermissionDenied()
    fun setMicrophone(enabled: Boolean) = controller.setMicrophone(enabled)
    fun chooseLens(lens: CameraLens) = controller.chooseLens(lens)
    fun chooseQuality(quality: Quality) = controller.chooseQuality(quality)
    fun retryPreview() = controller.retryPreview()
    fun prepareSosMode() = controller.prepareSosMode()
    fun startSos() = controller.startSos()
    fun canStartSos() = controller.canStartSos()
    fun quickStartUnavailable() = controller.quickStartUnavailable()
    fun start() = controller.start()
    fun recover() = controller.recover()
    fun stop() = controller.stop()
    fun selectDual(spec: com.umbracam.core.recording.DualRecordingSpec?) = controller.selectDual(spec)
}
