package com.umbracam.app

import android.app.Application
import com.umbracam.app.camera.CameraRecordingController

class UmbraCamApplication : Application() {
    val captureThermals by lazy { CaptureThermals(this) }
    var libraryOperation: Boolean = false
        internal set
    val recording: CameraRecordingController by lazy { CameraRecordingController(this) }
    val screenRecording: com.umbracam.app.screen.ScreenRecordingController by lazy { com.umbracam.app.screen.ScreenRecordingController(this) }
}
