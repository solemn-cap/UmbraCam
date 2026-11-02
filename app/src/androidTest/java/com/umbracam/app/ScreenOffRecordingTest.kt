package com.umbracam.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umbracam.app.camera.CameraStudioViewModel
import com.umbracam.app.camera.RecordingService
import com.umbracam.core.recording.RecordingState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in because a physical device with a secure keyguard needs its owner to unlock afterward. */
@RunWith(AndroidJUnit4::class)
class ScreenOffRecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var model: CameraStudioViewModel? = null

    @After fun stopCapture() {
        model?.let { controller ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.stop() }
            val deadline = SystemClock.uptimeMillis() + 30_000
            while (SystemClock.uptimeMillis() < deadline && controller.ui.value.serviceRunning) SystemClock.sleep(50)
        }
    }

    @Test fun screenOffContinuesUntilNotificationStop() {
        assumeTrue("Use -e lockScreenTest true to opt in", InstrumentationRegistry.getArguments().getString("lockScreenTest") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission("com.umbracam.app", Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission("com.umbracam.app", Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[CameraStudioViewModel::class.java] }
        val controller = model!!
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.waitUntil(30_000) { controller.ui.value.ready && !controller.ui.value.initializing && !controller.ui.value.serviceRunning }
        compose.runOnIdle { controller.setMicrophone(false) }
        compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().performClick()
        compose.waitUntil(30_000) { controller.session.value?.state == RecordingState.RECORDING && controller.ui.value.durationMillis >= 1_500 }
        val before = controller.ui.value.durationMillis
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
        compose.waitUntil(15_000) { controller.session.value?.state == RecordingState.RECORDING && controller.ui.value.durationMillis >= before + 1_500 }
        val notification = compose.activity.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == RecordingService.NOTIFICATION_ID }.notification
        notification.actions.single { it.title.toString() == compose.activity.getString(R.string.camera_stop) }.actionIntent.send()
        compose.waitUntil(30_000) { controller.session.value?.state == RecordingState.SAVED && !controller.ui.value.serviceRunning }
        val saved = controller.session.value!!.completedSegments.last()
        assertTrue(saved.durationMillis >= before + 1_000)
        assertTrue(File(compose.activity.noBackupFilesDir, "camera/recordings/${saved.storageId}").length() > 0)
    }
}
