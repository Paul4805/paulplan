package com.slot.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slot.app.core.design.Tokens
import com.slot.app.data.Subject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.Space.s2),
    )
}

@Composable
private fun ChipRow(
    values: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        values.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun TimeFields(
    hour: String,
    minute: String,
    onHour: (String) -> Unit,
    onMinute: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        OutlinedTextField(
            value = hour,
            onValueChange = { onHour(it.filter(Char::isDigit).take(2)) },
            label = { Text("HH") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minute,
            onValueChange = { onMinute(it.filter(Char::isDigit).take(2)) },
            label = { Text("MM") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun parseMinutes(hour: String, minute: String): Int? {
    val h = hour.toIntOrNull() ?: return null
    val m = minute.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun parseDueDate(date: String, time: String): Date? {
    if (date.isBlank()) return null
    return runCatching {
        val day = LocalDate.parse(date.trim())
        val localTime = if (time.isBlank()) LocalTime.of(23, 59) else LocalTime.parse(time.trim())
        Date.from(day.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant())
    }.getOrNull()
}

@Composable
fun AddTaskDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (title: String, subjectId: String?, dueAt: Date?, priority: String, remind: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id) }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("17:00") }
    var priority by remember { mutableStateOf("medium") }
    var remind by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Subject")
                ChipRow(
                    values = listOf("" to "None") + subjects.map { it.id to it.name },
                    selected = subjectId ?: "",
                    onSelect = { subjectId = it.ifBlank { null } },
                )

                FieldLabel("Due date (YYYY-MM-DD, optional)")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    placeholder = { Text("2026-03-14") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Due time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Priority")
                ChipRow(
                    values = listOf("low" to "Low", "medium" to "Medium", "high" to "High"),
                    selected = priority,
                    onSelect = { priority = it },
                )

                FieldLabel("Reminder")
                ChipRow(
                    values = listOf("off" to "Off", "on" to "Remind me"),
                    selected = if (remind) "on" else "off",
                    onSelect = { remind = it == "on" },
                )
                Text(
                    if (date.isBlank()) {
                        "Set a due date to enable a reminder."
                    } else {
                        "Fires on this phone at the due time, even offline."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val due = parseDueDate(date, time)
                    if (title.isBlank()) {
                        error = "Give the task a title."
                    } else if (date.isNotBlank() && due == null) {
                        error = "Use YYYY-MM-DD for the date."
                    } else {
                        onSave(title.trim(), subjectId, due, priority, remind && due != null)
                        onDismiss()
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddEntryDialog(
    subjects: List<Subject>,
    defaultKind: String = "class",
    onDismiss: () -> Unit,
    onSave: (
        kind: String,
        subjectId: String?,
        title: String?,
        color: String?,
        dayOfWeek: Int,
        startMinutes: Int,
        endMinutes: Int,
        room: String,
        date: String?,
    ) -> Unit,
) {
    var kind by remember { mutableStateOf(defaultKind) }
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("indigo") }
    var day by remember { mutableStateOf(LocalDate.now().dayOfWeek.value) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var startHour by remember { mutableStateOf("09") }
    var startMinute by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("10") }
    var endMinute by remember { mutableStateOf("00") }
    var room by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val isClass = kind == "class"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isClass) "Add class" else "Add event") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                FieldLabel("Type")
                ChipRow(
                    values = listOf("class" to "Class", "event" to "Event"),
                    selected = kind,
                    onSelect = { kind = it },
                )

                if (isClass) {
                    FieldLabel("Subject")
                    ChipRow(
                        values = subjects.map { it.id to it.name },
                        selected = subjectId,
                        onSelect = { subjectId = it },
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Talk, meeting, church…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FieldLabel("Colour")
                    ChipRow(
                        values = listOf("indigo", "sky", "violet", "amber", "rose", "emerald")
                            .map { it to it.replaceFirstChar { c -> c.uppercase() } },
                        selected = color,
                        onSelect = { color = it },
                    )
                }

                FieldLabel(if (isClass) "Day" else "Date")
                if (isClass) {
                    ChipRow(
                        values = (1..7).map { it.toString() to dayLabel(it) },
                        selected = day.toString(),
                        onSelect = { day = it.toInt() },
                    )
                } else {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Defaults to today. This event appears only on that date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FieldLabel("Start")
                TimeFields(startHour, startMinute, { startHour = it }, { startMinute = it })
                FieldLabel("End")
                TimeFields(endHour, endMinute, { endHour = it }, { endMinute = it })

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = parseMinutes(startHour, startMinute)
                    val end = parseMinutes(endHour, endMinute)
                    val eventDate = if (!isClass) {
                        runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                    } else {
                        null
                    }
                    when {
                        isClass && subjects.isEmpty() -> error = "Create a subject first, or switch to Event."
                        isClass && subjectId.isBlank() -> error = "Pick a subject."
                        !isClass && title.isBlank() -> error = "Give the event a title."
                        !isClass && eventDate == null -> error = "Use YYYY-MM-DD for the date."
                        start == null || end == null -> error = "Enter valid times."
                        end <= start -> error = "End must be after start."
                        else -> {
                            onSave(
                                kind,
                                if (isClass) subjectId else null,
                                if (isClass) null else title.trim(),
                                if (isClass) null else color,
                                eventDate?.dayOfWeek?.value ?: day,
                                start,
                                end,
                                room.trim(),
                                eventDate?.toString(),
                            )
                            onDismiss()
                        }
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, color: String, teacher: String, room: String, target: Double?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("indigo") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New subject") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Teacher (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Weekly target hours (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Colour")
                ChipRow(
                    values = listOf("indigo", "sky", "violet", "amber", "rose", "emerald")
                        .map { it to it.replaceFirstChar(Char::uppercase) },
                    selected = color,
                    onSelect = { color = it },
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = "Give the subject a name."
                    } else {
                        onSave(name.trim(), code.trim(), color, teacher.trim(), room.trim(), target.toDoubleOrNull())
                        onDismiss()
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, recurrence: String, dayOfWeek: Int?, timeMinutes: Int, leadMinutes: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("daily") }
    var day by remember { mutableStateOf(1) }
    var hour by remember { mutableStateOf("07") }
    var minute by remember { mutableStateOf("00") }
    var lead by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New alarm") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Repeat")
                ChipRow(
                    values = listOf("daily" to "Daily", "weekly" to "Weekly"),
                    selected = recurrence,
                    onSelect = { recurrence = it },
                )

                if (recurrence == "weekly") {
                    FieldLabel("Day")
                    ChipRow(
                        values = (1..7).map { it.toString() to dayLabel(it) },
                        selected = day.toString(),
                        onSelect = { day = it.toInt() },
                    )
                }

                FieldLabel("Time")
                TimeFields(hour, minute, { hour = it }, { minute = it })

                FieldLabel("Heads-up")
                ChipRow(
                    values = listOf("0" to "At time", "5" to "5 min", "10" to "10 min", "15" to "15 min", "30" to "30 min"),
                    selected = lead.toString(),
                    onSelect = { lead = it.toInt() },
                )

                Text(
                    "Alarms are scheduled on this phone and fire offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = parseMinutes(hour, minute)
                    if (title.isBlank()) {
                        error = "Give the alarm a title."
                    } else if (minutes == null) {
                        error = "Enter a valid time."
                    } else {
                        onSave(title.trim(), recurrence, if (recurrence == "weekly") day else null, minutes, lead)
                        onDismiss()
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddTrainingDialog(
    onDismiss: () -> Unit,
    onSave: (
        type: String,
        title: String,
        startedAt: Date,
        minutes: Int,
        distanceKm: Double?,
        notes: String,
    ) -> Unit,
) {
    var type by remember { mutableStateOf("run") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("45") }
    var km by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val now = java.time.LocalDateTime.now()
        date = "%04d-%02d-%02d".format(now.year, now.monthValue, now.dayOfMonth)
        time = "%02d:%02d".format(now.hour, now.minute)
    }

    val label = when (type) {
        "run" -> "Run"
        "football" -> "Football"
        "gym" -> "Gym"
        else -> "Training"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log training") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                FieldLabel("Type")
                ChipRow(
                    values = listOf("run" to "Run", "football" to "Football", "gym" to "Gym", "other" to "Other"),
                    selected = type,
                    onSelect = { type = it },
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    placeholder = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Start")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (type == "run") {
                    OutlinedTextField(
                        value = km,
                        onValueChange = { km = it },
                        label = { Text("Distance (km, optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val started = runCatching {
                        java.time.LocalDate.parse(date)
                            .atTime(java.time.LocalTime.parse(time))
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                    }.getOrNull()
                    val duration = minutes.toIntOrNull()

                    when {
                        started == null -> error = "Use YYYY-MM-DD and HH:mm."
                        duration == null || duration <= 0 -> error = "Enter a duration."
                        else -> {
                            onSave(
                                type,
                                title.ifBlank { label },
                                Date.from(started),
                                duration.coerceAtMost(1440),
                                km.toDoubleOrNull()?.takeIf { it > 0 },
                                notes.trim(),
                            )
                            onDismiss()
                        }
                    }
                },
            ) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    confirmLabel: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
