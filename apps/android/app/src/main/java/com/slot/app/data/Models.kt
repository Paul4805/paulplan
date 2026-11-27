package com.slot.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class Subject(
    val id: String,
    val name: String,
    val code: String? = null,
    val color: String = "indigo",
    val teacher: String? = null,
    val room: String? = null,
    val targetHoursPerWeek: Double? = null,
    val archived: Boolean = false,
)

data class ClassSlot(
    val id: String,
    val subjectId: String? = null,
    val title: String? = null,
    val color: String? = null,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val room: String? = null,
)

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String? = null,
    val subjectId: String? = null,
    val dueAt: Date? = null,
    val priority: String = "medium",
    val status: String = "todo",
    val completedAt: Date? = null,
    val order: Double = 0.0,
)

data class StudySession(
    val id: String,
    val subjectId: String? = null,
    val startedAt: Date? = null,
    val endedAt: Date? = null,
    val durationMinutes: Int = 0,
    val source: String = "manual",
)

data class PtSession(
    val id: String,
    val type: String = "other",
    val title: String? = null,
    val startedAt: Date? = null,
    val durationMinutes: Int = 0,
    val distanceKm: Double? = null,
    val source: String = "manual",
    val notes: String? = null,
)

data class AlarmItem(
    val id: String,
    val title: String,
    val kind: String = "custom",
    val recurrence: String = "daily",
    val dayOfWeek: Int? = null,
    val timeMinutes: Int? = null,
    val onceAt: Date? = null,
    val leadMinutes: Int = 0,
    val exact: Boolean = true,
    val enabled: Boolean = true,
    val timezone: String = "UTC",
    val targetId: String? = null,
)

data class Profile(
    val timezone: String? = null,
    val leadMinutes: Int = 10,
    val focusMinutes: Int = 25,
    val shortMinutes: Int = 5,
    val theme: String = "system",
    val weekStartsOn: Int = 1,
) {
    companion object {
        val Default = Profile()
    }
}

fun DocumentSnapshot.toSubject(): Subject = Subject(
    id = id,
    name = getString("name") ?: "(untitled)",
    code = getString("code"),
    color = getString("color") ?: "indigo",
    teacher = getString("teacher"),
    room = getString("room"),
    targetHoursPerWeek = getDouble("targetHoursPerWeek"),
    archived = getBoolean("archived") ?: false,
)

fun DocumentSnapshot.toClassSlot(): ClassSlot = ClassSlot(
    id = id,
    subjectId = getString("subjectId"),
    title = getString("title"),
    color = getString("colorOverride"),
    dayOfWeek = (getLong("dayOfWeek") ?: 1L).toInt(),
    startMinutes = (getLong("startMinutes") ?: 540L).toInt(),
    endMinutes = (getLong("endMinutes") ?: 630L).toInt(),
    room = getString("room"),
)

fun DocumentSnapshot.toTask(): TaskItem = TaskItem(
    id = id,
    title = getString("title") ?: "(untitled)",
    notes = getString("notes"),
    subjectId = getString("subjectId"),
    dueAt = getTimestamp("dueAt")?.toDate(),
    priority = getString("priority") ?: "medium",
    status = getString("status") ?: "todo",
    completedAt = getTimestamp("completedAt")?.toDate(),
    order = getDouble("order") ?: 0.0,
)

fun DocumentSnapshot.toSession(): StudySession = StudySession(
    id = id,
    subjectId = getString("subjectId"),
    startedAt = getTimestamp("startedAt")?.toDate(),
    endedAt = getTimestamp("endedAt")?.toDate(),
    durationMinutes = (getLong("durationMinutes") ?: 0L).toInt(),
    source = getString("source") ?: "manual",
)

fun DocumentSnapshot.toPtSession(): PtSession = PtSession(
    id = id,
    type = getString("type") ?: "other",
    title = getString("title"),
    startedAt = getTimestamp("startedAt")?.toDate(),
    durationMinutes = (getLong("durationMinutes") ?: 0L).toInt(),
    distanceKm = getDouble("distanceKm"),
    source = getString("source") ?: "manual",
    notes = getString("notes"),
)

fun DocumentSnapshot.toAlarm(): AlarmItem = AlarmItem(
    id = id,
    title = getString("title") ?: "Alarm",
    kind = getString("kind") ?: "custom",
    recurrence = getString("recurrence") ?: "daily",
    dayOfWeek = getLong("dayOfWeek")?.toInt(),
    timeMinutes = getLong("timeMinutes")?.toInt(),
    onceAt = getTimestamp("onceAt")?.toDate(),
    leadMinutes = (getLong("leadMinutes") ?: 0L).toInt(),
    exact = getBoolean("exact") ?: true,
    enabled = getBoolean("enabled") ?: true,
    timezone = getString("timezone") ?: "UTC",
    targetId = getString("targetId"),
)

fun DocumentSnapshot.toProfile(): Profile {
    val notifications = get("notifications") as? Map<*, *>
    val pomodoro = get("pomodoro") as? Map<*, *>

    return Profile(
        timezone = getString("timezone"),
        leadMinutes = (notifications?.get("leadMinutes") as? Number)?.toInt() ?: 10,
        focusMinutes = (pomodoro?.get("focus") as? Number)?.toInt() ?: 25,
        shortMinutes = (pomodoro?.get("short") as? Number)?.toInt() ?: 5,
        theme = getString("theme") ?: "system",
        weekStartsOn = (getLong("weekStartsOn") ?: 1L).toInt(),
    )
}

fun Date.toTimestamp(): Timestamp = Timestamp(this)
