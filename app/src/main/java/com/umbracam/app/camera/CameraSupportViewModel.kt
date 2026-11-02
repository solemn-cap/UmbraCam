package com.umbracam.app.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.umbracam.core.recording.ConcurrentCameraCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraSupportState(val checking: Boolean = false, val result: ConcurrentCameraCapabilities? = null)

class CameraSupportViewModel(application: Application) : AndroidViewModel(application) {
    private val detector = ConcurrentCameraDetector(AndroidCameraInventorySource(application))
    private val mutableState = MutableStateFlow(CameraSupportState())
    val state = mutableState.asStateFlow()

    fun checkSupport() {
        if (mutableState.value.checking) return
        mutableState.value = CameraSupportState(checking = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { detector.read() }
            mutableState.value = CameraSupportState(result = result)
        }
    }
}
