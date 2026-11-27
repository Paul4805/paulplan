package com.slot.app.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.slot.app.MainActivity
import com.slot.app.R
import org.json.JSONArray
import org.json.JSONObject

object AlarmScheduler {
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_FULL_SCREEN = "full_screen"
    const val EXTRA_KIND = "kind"

    private const val ACTION_FIRE = "com.slot.app.action.FIRE_ALARM"

    fun armAll(context: Context, events: List<ScheduledAlarm>) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        for (event in events) {
            if (event.triggerAtMillis <= now) continue
            val pending = fireIntent(context, event)

            if (canExact) {
                // setAlarmClock is the only API that reliably survives Doze, OEM
                // battery managers, and shows the alarm icon in the status bar.
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(event.triggerAtMillis, openIntent(context, event)),
                    pending,
                )
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.triggerAtMillis, pending)
            }
        }

        // Persist only future occurrences: the snapshot is what a boot receiver
        // re-arms, and it should not grow with every past alarm.
        AlarmSnapshot.save(context, events.filter { it.triggerAtMillis > now })
    }

    fun armOne(context: Context, event: ScheduledAlarm) {
        val existing = AlarmSnapshot.load(context)
            .filterNot { it.eventId == event.eventId }
            .plus(event)
            .sortedBy { it.triggerAtMillis }
        armAll(context, existing)
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (manager != null) {
            for (event in AlarmSnapshot.load(context)) {
                manager.cancel(fireIntent(context, event))
            }
        }
        AlarmSnapshot.clear(context)
    }

    /** Fires a full-screen alarm in [seconds] so the user can verify delivery. */
    fun fireTest(context: Context, seconds: Int = 10) {
        val now = System.currentTimeMillis()
        val event = ScheduledAlarm(
            eventId = "test:$now",
            alarmId = "test",
            title = "Test alarm",
            detail = "Alarms are working on this phone.",
            triggerAtMillis = now + seconds * 1_000L,
            fullScreen = true,
            kind = "alarm",
        )
        armOne(context, event)
    }

    private fun fireIntent(context: Context, event: ScheduledAlarm): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_EVENT_ID, event.eventId)
            putExtra(EXTRA_ALARM_ID, event.alarmId)
            putExtra(EXTRA_TITLE, event.title)
            putExtra(EXTRA_DETAIL, event.detail)
            putExtra(EXTRA_FULL_SCREEN, event.fullScreen)
            putExtra(EXTRA_KIND, event.kind)
        }
        return PendingIntent.getBroadcast(
            context,
            event.eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openIntent(context: Context, event: ScheduledAlarm): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            event.eventId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun alarmActivityIntent(context: Context, event: ScheduledAlarm): Intent =
        Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_EVENT_ID, event.eventId)
            putExtra(EXTRA_ALARM_ID, event.alarmId)
            putExtra(EXTRA_TITLE, event.title)
            putExtra(EXTRA_DETAIL, event.detail)
        }
}

/**
 * SharedPreferences, not Room or DataStore: the boot receiver needs a
 * synchronous read within its ~10 second window, before Firebase can start.
 * This is a derived cache, never a source of truth.
 */
object AlarmSnapshot {
    private const val PREFS = "slot_alarm_snapshot"
    private const val KEY = "events"

    /**
     * Device-protected storage, not credential-encrypted: the boot receiver runs
     * before the first unlock, when normal SharedPreferences are unreadable.
     */
    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, events: List<ScheduledAlarm>) {
        val array = JSONArray()
        for (event in events) {
            array.put(
                JSONObject().apply {
                    put("eventId", event.eventId)
                    put("alarmId", event.alarmId)
                    put("title", event.title)
                    put("detail", event.detail)
                    put("triggerAtMillis", event.triggerAtMillis)
                    put("fullScreen", event.fullScreen)
                    put("kind", event.kind)
                },
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    fun load(context: Context): List<ScheduledAlarm> = runCatching {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()

        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ScheduledAlarm(
                eventId = item.optString("eventId"),
                alarmId = item.optString("alarmId"),
                title = item.optString("title"),
                detail = item.optString("detail"),
                triggerAtMillis = item.optLong("triggerAtMillis"),
                fullScreen = item.optBoolean("fullScreen"),
                kind = item.optString("kind"),
            )
        }
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}

object NotificationChannels {
    const val ALARMS = "slot.alarms"
    const val REMINDERS = "slot.reminders"
    const val FOCUS = "slot.focus"
    const val FOCUS_ALERTS = "slot.focus_alerts"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val alarms = NotificationChannel(
            ALARMS,
            context.getString(R.string.channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarms_description)
            enableVibration(true)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(alarms, reminders))

        val focus = NotificationChannel(
            FOCUS,
            context.getString(R.string.channel_focus),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_focus_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(focus)

        val focusAlerts = NotificationChannel(
            FOCUS_ALERTS,
            context.getString(R.string.channel_focus_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_focus_alerts_description)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(focusAlerts)
    }

    fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** Android 14+ gates full-screen intents behind a special app access. */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }
}
