package com.umbracam.app.camera

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
import com.umbracam.core.recording.*
import kotlinx.coroutines.*

/** Started only by a visible user action; never sticky, exported, boot-started or self-restarted. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = (application as UmbraCamApplication).recording
    private var ownedAttempt: String? = null
    private var observation: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        // Fulfill startForegroundService's deadline even if Stop raced the pending Start command.
        controller.pendingServiceSession()?.let { pending ->
            try { promote(pending); ownedAttempt = pending.attemptId }
            catch (_: Exception) { controller.serviceStartFailed(pending.attemptId); stopSelf() }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val attempt = intent?.getStringExtra(EXTRA_ATTEMPT)
        if (intent?.action == ACTION_STOP) {
            if (attempt != null && controller.session.value?.attemptId == attempt) controller.stop()
            if (ownedAttempt == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || attempt == null || !controller.canPromoteService(attempt) || !notificationsAllowed(this)) {
            if (attempt != null) controller.serviceStartFailed(attempt, ownedAttempt != null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            promote(controller.session.value!!)
            ownedAttempt = attempt
            controller.serviceAccepted(attempt)
            observation?.cancel()
            observation = scope.launch {
                controller.session.collect { session ->
                    if (session == null || session.attemptId != attempt || session.state !in SingleCameraSessionCoordinator.busyStates) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(session))
                    }
                }
            }
        } catch (_: Exception) {
            controller.serviceStartFailed(attempt, ownedAttempt != null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun promote(session: RecordingSession) {
        val audio = CaptureSource.MICROPHONE in session.request.requiredSources
        val types = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        } else 0
        startForeground(NOTIFICATION_ID, notification(session), types)
    }

    private fun notification(session: RecordingSession): Notification {
        val open = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CAMERA, true)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val stop = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
            .setData(Uri.parse("umbracam://recording/stop/${session.attemptId}"))
            .putExtra(EXTRA_ATTEMPT, session.attemptId)
        val title = when (session.state) {
            RecordingState.RECORDING -> R.string.notification_recording
            RecordingState.RECOVERING -> R.string.recording_recovering
            RecordingState.STOPPING -> R.string.recording_stopping
            RecordingState.INTERRUPTED -> R.string.recording_interrupted
            else -> R.string.recording_preparing
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording_notification)
            .setContentTitle(getString(title))
            .setContentText(getString(R.string.notification_controls))
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, getString(R.string.camera_stop), PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.stop() // Dismissing the app's task is treated as an explicit stop, not restart permission.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observation?.cancel()
        scope.cancel()
        ownedAttempt?.let(controller::serviceEnded)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.umbracam.app.recording.START"
        const val ACTION_STOP = "com.umbracam.app.recording.STOP"
        const val EXTRA_ATTEMPT = "attempt"
        const val CHANNEL_ID = "active_recording"
        const val NOTIFICATION_ID = 1001

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
            }
        }

        fun notificationsAllowed(context: Context): Boolean {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
