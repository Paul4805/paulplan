package com.slot.app.alarm

import com.slot.app.data.AlarmItem
import com.slot.app.data.ClassSlot
import com.slot.app.data.Subject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * A concrete occurrence that the phone can hand to AlarmManager.
 *
 * [kind] is informational: "class" and "reminder" are heads-up notifications,
 * "alarm" is a full-screen alarm.
 */
data class ScheduledAlarm(
    val eventId: String,
    val alarmId: String,
    val title: String,
    val detail: String,
    val triggerAtMillis: Long,
    val fullScreen: Boolean,
    val kind: String,
)

/**
 * Pure computation, no Android types, so it can be reasoned about (and later
 * unit-tested) without a device. The web build has no equivalent because it has
 * no background scheduling.
 */
object AlarmMath {
    const val HORIZON_DAYS = 21

    fun occurrences(
        alarms: List<AlarmItem>,
        classes: List<ClassSlot>,
        subjects: Map<String, Subject>,
        classLeadMinutes: Int,
        now: Date = Date(),
        days: Int = HORIZON_DAYS,
    ): List<ScheduledAlarm> {
        val nowInstant = now.toInstant()
        val today = LocalDate.now()
        val systemZone = ZoneId.systemDefault()
        val lead = classLeadMinutes.coerceAtLeast(0)
        val result = mutableListOf<ScheduledAlarm>()

        // Class heads-up: one per slot per day in the horizon.
        for (offset in 0 until days) {
            val date = today.plusDays(offset.toLong())
            val weekday = date.dayOfWeek.value // 1 = Monday … 7 = Sunday

            for (slot in classes) {
                if (slot.dayOfWeek != weekday) continue
                val start = date.atStartOfDay(systemZone).plusMinutes(slot.startMinutes.toLong()).toInstant()
                val notifyAt = start.minusSeconds(lead * 60L)
                if (!notifyAt.isAfter(nowInstant)) continue

                val subject = subjects[slot.subjectId]
                result += ScheduledAlarm(
                    eventId = "c:${slot.id}:${start.epochSecond / 60}",
                    alarmId = "class:${slot.id}",
                    title = subject?.name ?: "Class",
                    detail = buildString {
                        append(timeLabel(slot.startMinutes))
                        slot.room?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    triggerAtMillis = notifyAt.toEpochMilli(),
                    fullScreen = false,
                    kind = "class",
                )
            }
        }

        // Explicit alarms: reminder (if lead > 0) plus the alarm itself.
        for (alarm in alarms) {
            if (!alarm.enabled) continue
            val zone = zoneOrSystem(alarm.timezone)

            when (alarm.recurrence) {
                "once" -> {
                    val at = alarm.onceAt ?: continue
                    addAlarmEvents(result, alarm, at.toInstant(), zone, nowInstant)
                }

                "weekly" -> {
                    for (offset in 0 until days) {
                        val date = today.plusDays(offset.toLong())
                        if (alarm.dayOfWeek != date.dayOfWeek.value) continue
                        val minutes = alarm.timeMinutes ?: continue
                        addAlarmEvents(
                            result,
                            alarm,
                            date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant(),
                            zone,
                            nowInstant,
                        )
                    }
                }

                else -> {
                    for (offset in 0 until days) {
                        val date = today.plusDays(offset.toLong())
                        val minutes = alarm.timeMinutes ?: continue
                        addAlarmEvents(
                            result,
                            alarm,
                            date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant(),
                            zone,
                            nowInstant,
                        )
                    }
                }
            }
        }

        return result.sortedBy { it.triggerAtMillis }
    }

    private fun addAlarmEvents(
        out: MutableList<ScheduledAlarm>,
        alarm: AlarmItem,
        fireAt: Instant,
        zone: ZoneId,
        now: Instant,
    ) {
        val minuteKey = fireAt.epochSecond / 60
        val lead = alarm.leadMinutes.coerceAtLeast(0)

        if (lead > 0) {
            val headsUp = fireAt.minusSeconds(lead * 60L)
            if (headsUp.isAfter(now)) {
                out += ScheduledAlarm(
                    eventId = "a:${alarm.id}:$minuteKey:lead",
                    alarmId = alarm.id,
                    title = alarm.title,
                    detail = "in $lead min",
                    triggerAtMillis = headsUp.toEpochMilli(),
                    fullScreen = false,
                    kind = "reminder",
                )
            }
        }

        if (fireAt.isAfter(now)) {
            val local = fireAt.atZone(zone).toLocalTime()
            out += ScheduledAlarm(
                eventId = "a:${alarm.id}:$minuteKey",
                alarmId = alarm.id,
                title = alarm.title,
                detail = timeLabel(local),
                triggerAtMillis = fireAt.toEpochMilli(),
                fullScreen = true,
                kind = "alarm",
            )
        }
    }

    fun timeLabel(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    fun timeLabel(time: LocalTime): String =
        "%02d:%02d".format(time.hour, time.minute)

    private fun zoneOrSystem(id: String): ZoneId =
        runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())

    fun describe(alarm: AlarmItem): String {
        val time = alarm.timeMinutes?.let { timeLabel(it) } ?: "--:--"
        val repeat = when {
            alarm.recurrence == "weekly" -> dayName(alarm.dayOfWeek ?: 1)
            alarm.recurrence == "once" -> "Once"
            else -> "Daily"
        }
        return "$repeat · $time"
    }

    fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Sun"
    }
}
