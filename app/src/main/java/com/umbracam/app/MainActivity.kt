package com.umbracam.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.umbracam.app.screen.ScreenStudioViewModel
import com.umbracam.app.camera.CameraStudioViewModel
import com.umbracam.core.recording.ReactionRecordingRequest

class MainActivity : ComponentActivity() {
    private var cameraOpenRequest by mutableIntStateOf(0)
    private var screenOpenRequest by mutableIntStateOf(0)
    private lateinit var screenModel: ScreenStudioViewModel
    private val screenConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        screenModel.result(result.resultCode, result.data)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        consumeCameraOpen(intent)
        val cameraViewModel = ViewModelProvider(this)[CameraStudioViewModel::class.java]
        screenModel = ViewModelProvider(this)[ScreenStudioViewModel::class.java]
        setContent { UmbraCamTheme { UmbraCamApp(cameraViewModel, cameraOpenRequest,
            screenViewModel = screenModel, screenOpenRequest = screenOpenRequest, onScreenStart = ::startScreenConsent) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeCameraOpen(intent)
    }

    private fun consumeCameraOpen(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_CAMERA, false)) {
            cameraOpenRequest++
            intent.removeExtra(EXTRA_OPEN_CAMERA)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SCREEN, false)) {
            screenOpenRequest++; intent.removeExtra(EXTRA_OPEN_SCREEN)
        }
    }

    private fun startScreenConsent(request: ReactionRecordingRequest) { lifecycleScope.launch {
        val visible = withTimeoutOrNull(2_000) { lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) } }
        if (visible != null) screenModel.consentIntent(request)?.let(screenConsent::launch)
    } }
    override fun onResume() { super.onResume(); if (::screenModel.isInitialized) screenModel.resumed() }
    override fun onPause() { if (::screenModel.isInitialized) screenModel.paused(); super.onPause() }

    companion object { const val EXTRA_OPEN_CAMERA = "open_camera"; const val EXTRA_OPEN_SCREEN = "open_screen" }
}
