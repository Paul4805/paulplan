package com.slot.app.alarm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.slot.app.MainActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT_ID) ?: return
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Alarm"
        val detail = intent.getStringExtra(AlarmScheduler.EXTRA_DETAIL).orEmpty()
        val fullScreen = intent.getBooleanExtra(AlarmScheduler.EXTRA_FULL_SCREEN, false)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val target = if (fullScreen) {
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AlarmScheduler.EXTRA_EVENT_ID, eventId)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_DETAIL, detail)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channel = if (fullScreen) NotificationChannels.ALARMS else NotificationChannels.REMINDERS

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(detail)
            .setCategory(
                if (fullScreen) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER,
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .apply { if (fullScreen) setFullScreenIntent(contentIntent, true) }
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(eventId.hashCode(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val events = AlarmSnapshot.load(context)
                if (events.isNotEmpty()) {
                    AlarmScheduler.armAll(context, events)
                }
            }
        }
    }
}
