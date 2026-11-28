package com.slot.app.focus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slot.app.MainActivity
import com.slot.app.alarm.NotificationChannels
import com.slot.app.data.SlotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        FocusTimer.load(this)
        NotificationChannels.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FocusTimer.load(this)
        when (intent?.action) {
            ACTION_PAUSE -> {
                FocusTimer.pause(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                FocusTimer.stop(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val state = FocusTimer.state.value
        if (!state.running) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification(FocusTimer.remaining(this)))
        if (job == null) {
            job = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val remaining = FocusTimer.remaining(this)
            FocusTimer.syncRemaining(this, remaining)

            if (remaining <= 0) {
                val finished = FocusTimer.finish(this)
                if (finished?.focus == true) {
                    logSession(finished)
                }
                notifyFinished(finished)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                break
            }

            if (remaining % 5 == 0 || remaining <= 3) {
                notifyProgress(remaining)
            }
            delay(1_000L)
        }
    }

    private suspend fun logSession(finished: FinishedFocus) {
        withContext(Dispatchers.IO) {
            runCatching {
                val user = FirebaseAuth.getInstance().currentUser ?: return@runCatching
                SlotRepository(FirebaseFirestore.getInstance(), user.uid)
                    .logSession(finished.subjectId, finished.minutes, "pomodoro")
            }
        }
    }

    private fun notifyProgress(remaining: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification(remaining))
    }

    private fun notifyFinished(finished: FinishedFocus?) {
        if (finished == null) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val title = if (finished.focus) "Focus finished" else "Break over"
        val text = if (finished.focus) {
            "${finished.minutes} minutes logged. Take a short break."
        } else {
            "Ready for another focus session."
        }

        val done = NotificationCompat.Builder(this, NotificationChannels.FOCUS_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .build()

        manager.notify(NOTIFICATION_ID + 1, done)
    }

    private fun notification(remaining: Int): Notification {
        val state = FocusTimer.state.value
        val phase = if (state.phase == "focus") "Focus" else "Break"
        val text = "%02d:%02d · %s".format(remaining / 60, remaining % 60, phase)

        return NotificationCompat.Builder(this, NotificationChannels.FOCUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PaulPlan timer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent())
            .addAction(0, "Pause", serviceIntent(ACTION_PAUSE, 1))
            .addAction(0, "Stop", serviceIntent(ACTION_STOP, 2))
            .build()
    }

    private fun openIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            40,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun serviceIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, FocusService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        job = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 5001
        const val ACTION_PAUSE = "com.slot.app.action.FOCUS_PAUSE"
        const val ACTION_STOP = "com.slot.app.action.FOCUS_STOP"
    }
}
