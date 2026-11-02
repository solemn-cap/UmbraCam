package com.umbracam.app.screen

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.umbracam.app.UmbraCamApplication
import com.umbracam.core.recording.ReactionMode
import com.umbracam.core.recording.ReactionRecordingRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Stable activity-owned result correlation. Neither returned data nor authorization is saved. */
class ScreenStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as UmbraCamApplication).screenRecording
    val ui = controller.ui
    val session = controller.session
    val thermalStatus = controller.thermalStatus
    private var resumed = false
    private var reply: Pair<Int, Intent?>? = null
    private var expiry: Job? = null
    fun consentIntent(request: ReactionRecordingRequest = ReactionRecordingRequest(ReactionMode.SCREEN_ONLY)): Intent? =
        if (resumed) controller.requestConsent(request) else null
    fun result(code: Int, data: Intent?) {
        if (resumed) controller.consentResult(code, data, true)
        else {
            reply = code to data
            expiry?.cancel()
            expiry = viewModelScope.launch { delay(2_000); reply = null; controller.cancelConsent() }
        }
    }
    fun resumed() {
        resumed = true
        reply?.let { reply = null; expiry?.cancel(); controller.consentResult(it.first, it.second, true) }
    }
    fun paused() { resumed = false; if (reply != null) { reply = null; expiry?.cancel(); controller.cancelConsent() } }
    fun stop() = controller.stop()
    fun cameraPermissionDenied() = controller.cameraPermissionDenied()
    fun microphonePermissionDenied() = controller.microphonePermissionDenied()
    fun notificationDenied() = controller.notificationDenied()
}
