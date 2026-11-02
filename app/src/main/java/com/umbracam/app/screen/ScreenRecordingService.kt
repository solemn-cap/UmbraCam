package com.umbracam.app.screen

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.umbracam.app.MainActivity
import com.umbracam.app.R
import com.umbracam.app.UmbraCamApplication
import com.umbracam.core.recording.SingleCameraSessionCoordinator
import com.umbracam.core.recording.RecordingState
import kotlinx.coroutines.*

class ScreenRecordingService : Service() {
    private val controller get() = (application as UmbraCamApplication).screenRecording
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var owned: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        controller.pendingAttempt()?.let { attempt ->
            try { promote(attempt); owned = attempt }
            catch (_: Exception) { controller.serviceFailed(attempt); stopSelf() }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val attempt = intent?.getStringExtra(ATTEMPT)
        if (intent?.action == STOP) {
            if (attempt != null && controller.session.value?.attemptId == attempt) controller.stop()
            if (owned == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != START || attempt == null) {
            if (owned == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (controller.pendingAttempt() != attempt) {
            controller.serviceFailed(attempt)
            if (owned == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            check(notificationsAllowed(this))
            promote(attempt); owned = attempt; controller.serviceAccepted(attempt)
            scope.launch { controller.session.collect { session ->
                if (session?.attemptId != attempt || session.state !in SingleCameraSessionCoordinator.busyStates) {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                } else getSystemService(NotificationManager::class.java).notify(ID, notification(attempt))
            } }
        } catch (_: Exception) { controller.serviceFailed(attempt); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }
    private fun promote(attempt: String) {
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            (if (controller.cameraRequired(attempt)) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) or
            (if (controller.microphoneRequired(attempt)) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        startForeground(ID, notification(attempt), types)
    }
    private fun notification(attempt: String): Notification {
        ensureChannel(this)
        val stop = Intent(this, ScreenRecordingService::class.java).setAction(STOP)
            .setData(Uri.parse("umbracam://screen/stop/$attempt")).putExtra(ATTEMPT, attempt)
        val open = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SCREEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val state = controller.session.value?.state
        val title = when (state) {
            RecordingState.RECORDING -> R.string.screen_notification
            RecordingState.STOPPING -> R.string.recording_stopping
            else -> R.string.recording_preparing
        }
        val detail = if (controller.cameraRequired(attempt)) R.string.reaction_notification_detail else R.string.screen_notification_detail
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_recording_notification)
            .setContentTitle(getString(title)).setContentText(getString(detail))
            .setContentIntent(PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, getString(R.string.camera_stop), PendingIntent.getService(this, 10, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }.build()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { controller.stop(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { scope.cancel(); owned?.let(controller::serviceEnded); super.onDestroy() }
    companion object {
        const val START = "com.umbracam.app.screen.START"
        const val STOP = "com.umbracam.app.screen.STOP"
        const val ATTEMPT = "attempt"
        const val ID = 1002
        const val CHANNEL = "screen_recording"
        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.screen_title), NotificationManager.IMPORTANCE_LOW))
        }
        fun notificationsAllowed(context: Context): Boolean {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        }
    }
}
