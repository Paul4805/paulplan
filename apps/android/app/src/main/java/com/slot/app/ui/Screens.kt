package com.slot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slot.app.core.design.Tokens
import com.slot.app.data.ClassSlot
import com.slot.app.data.SlotViewModel
import com.slot.app.data.StudySession
import com.slot.app.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay

private fun isToday(date: Date?): Boolean {
    if (date == null) return false
    val then = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()
    return then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
}

private fun sumMinutes(sessions: List<StudySession>): Int =
    sessions.filter { isToday(it.startedAt) }.sumOf { it.durationMinutes }

@Composable
fun TodayScreen(
    vm: SlotViewModel,
    onOpenSubjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val classes by vm.classes.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    val subjectById = subjects.associateBy { it.id }
    val now = LocalTime.now()
    val nowMinutes = now.hour * 60 + now.minute
    val today = LocalDate.now().dayOfWeek.value

    val todays = classes.filter { it.dayOfWeek == today }.sortedBy { it.startMinutes }
    val current = todays.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }
    val next = todays.firstOrNull { it.startMinutes > nowMinutes }
    val open = tasks.filter { it.status != "done" }
    val dueSoon = open.sortedBy { it.dueAt?.time ?: Long.MAX_VALUE }.take(5)
    val studied = sumMinutes(sessions)
    val minuteOfDay = nowMinutes

    var taskOpen by remember { mutableStateOf(false) }
    var classOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = when {
                        now.hour < 12 -> "Good morning."
                        now.hour < 17 -> "Good afternoon."
                        else -> "Good evening."
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = shortDateLabel(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                OutlinedButton(onClick = { taskOpen = true }) { Text("Task") }
                Button(onClick = { classOpen = true }) { Text("Class") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard(
                label = "Now",
                value = current?.let { subjectById[it.subjectId]?.name ?: "Class" }
                    ?: next?.let { subjectById[it.subjectId]?.name ?: "Class" }
                    ?: "Free",
                hint = current?.let { "until ${minutesLabel(it.endMinutes)}" }
                    ?: next?.let { "in ${it.startMinutes - minuteOfDay} min" }
                    ?: "Nothing scheduled",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Due soon",
                value = dueSoon.size.toString(),
                hint = if (open.isEmpty()) "Nothing open" else "${open.size} open",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard(
                label = "Studied today",
                value = "$studied min",
                hint = "${sessions.count { isToday(it.startedAt) }} sessions",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Classes",
                value = todays.size.toString(),
                hint = "today",
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Today's classes")
        if (todays.isEmpty()) {
            EmptyHint("No classes today", "Add your weekly timetable and it repeats on its own.")
        } else {
            todays.forEach { slot ->
                val subject = subjectById[slot.subjectId]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Text(
                        text = minutesLabel(slot.startMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject?.name ?: "Class",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                "${minutesLabel(slot.startMinutes)}–${minutesLabel(slot.endMinutes)}",
                                slot.room?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (slot.startMinutes <= nowMinutes && nowMinutes < slot.endMinutes) {
                        Text(
                            "NOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Due soon")
        if (dueSoon.isEmpty()) {
            EmptyHint("Nothing due", "The next few days are clear.")
        } else {
            dueSoon.forEach { task ->
                TaskRow(
                    task = task,
                    subject = task.subjectId?.let { subjectById[it] },
                    onToggle = { vm.setTaskStatus(task.id, if (task.status == "done") "todo" else "done") },
                    onClick = { vm.setTaskStatus(task.id, if (task.status == "done") "todo" else "done") },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        OutlinedButton(onClick = onOpenSubjects, modifier = Modifier.fillMaxWidth()) {
            Text("Subjects & progress")
        }

        Text(
            "Alarms fire on this phone, offline and after reboot. The web dashboard only shows them while a tab is open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (taskOpen) {
        AddTaskDialog(
            subjects = subjects,
            onDismiss = { taskOpen = false },
            onSave = { title, subjectId, dueAt, priority ->
                vm.addTask(title, "", subjectId, dueAt, priority, "todo")
            },
        )
    }

    if (classOpen) {
        AddClassDialog(
            subjects = subjects,
            onDismiss = { classOpen = false },
            onSave = { subjectId, day, start, end, room ->
                vm.addClass(subjectId, day, start, end, room)
            },
        )
    }
}

@Composable
fun TimetableScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val classes by vm.classes.collectAsStateWithLifecycle()
    val subjectById = subjects.associateBy { it.id }

    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var classOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ClassSlot?>(null) }
    var newSubjectOpen by remember { mutableStateOf(false) }

    val slots = classes.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinutes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Timetable", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                OutlinedButton(onClick = { newSubjectOpen = true }) { Text("Subject") }
                Button(onClick = { classOpen = true }) { Text("Class") }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            (1..7).forEach { day ->
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    label = { Text(dayLabel(day)) },
                )
            }
        }

        if (slots.isEmpty()) {
            EmptyHint("Nothing on ${dayLabel(selectedDay)}", "Add a class, or pick another day.")
        } else {
            slots.forEach { slot ->
                val subject = subjectById[slot.subjectId]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            if (subject != null) {
                                Text(
                                    "●",
                                    color = tagColor(subject.color),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = subject?.name ?: "Class",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            text = buildString {
                                append(minutesLabel(slot.startMinutes))
                                append("–")
                                append(minutesLabel(slot.endMinutes))
                                slot.room?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { deleteTarget = slot }) { Text("Remove") }
                }
            }
        }

        Spacer(Modifier.height(Tokens.Space.s2))
        Text(
            "Tip: the week grid lives on the web. The phone keeps the same data in sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (classOpen) {
        AddClassDialog(
            subjects = subjects,
            onDismiss = { classOpen = false },
            onSave = { subjectId, day, start, end, room ->
                vm.addClass(subjectId, day, start, end, room)
                selectedDay = day
            },
        )
    }

    if (newSubjectOpen) {
        AddSubjectDialog(
            onDismiss = { newSubjectOpen = false },
            onSave = { name, code, color, teacher, room, target ->
                vm.addSubject(name, code, color, teacher, room, target)
            },
        )
    }

    deleteTarget?.let { slot ->
        ConfirmDeleteDialog(
            title = "Remove class?",
            body = "${subjectById[slot.subjectId]?.name ?: "This class"} on ${dayLabel(slot.dayOfWeek)} at ${minutesLabel(slot.startMinutes)}.",
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.deleteClass(slot.id) },
        )
    }
}

@Composable
fun TasksScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val subjectById = subjects.associateBy { it.id }

    var filter by rememberSaveable { mutableStateOf("open") }
    var taskOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }

    val shown = tasks.filter {
        when (filter) {
            "open" -> it.status != "done"
            else -> it.status == filter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tasks", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { taskOpen = true }) { Text("New") }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            listOf(
                "open" to "Open",
                "doing" to "Doing",
                "todo" to "To do",
                "done" to "Done",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) },
                )
            }
        }

        if (shown.isEmpty()) {
            EmptyHint("Nothing here", "Add a task, or switch the filter.")
        } else {
            shown.forEach { task ->
                Column {
                    TaskRow(
                        task = task,
                        subject = task.subjectId?.let { subjectById[it] },
                        onToggle = {
                            vm.setTaskStatus(task.id, if (task.status == "done") "todo" else "done")
                        },
                        onClick = {
                            val next = when (task.status) {
                                "todo" -> "doing"
                                "doing" -> "done"
                                else -> "todo"
                            }
                            vm.setTaskStatus(task.id, next)
                        },
                        onLongClick = { deleteTarget = task },
                    )
                }
            }
        }
    }

    if (taskOpen) {
        AddTaskDialog(
            subjects = subjects,
            onDismiss = { taskOpen = false },
            onSave = { title, subjectId, dueAt, priority ->
                vm.addTask(title, "", subjectId, dueAt, priority, "todo")
            },
        )
    }

    deleteTarget?.let { task ->
        ConfirmDeleteDialog(
            title = "Delete task?",
            body = task.title,
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.deleteTask(task.id) },
        )
    }
}

@Composable
fun FocusScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val subjectById = subjects.associateBy { it.id }

    val focusMinutes = profile.focusMinutes
    val shortMinutes = profile.shortMinutes

    var phase by rememberSaveable { mutableStateOf("focus") }
    var running by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(focusMinutes * 60) }
    var subjectId by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(focusMinutes, initialized) {
        if (!initialized) {
            secondsLeft = focusMinutes * 60
            initialized = true
        }
    }

    LaunchedEffect(running, phase) {
        if (!running) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft -= 1
        }
        running = false
        if (phase == "focus") {
            vm.logSession(subjectId.ifBlank { null }, focusMinutes, "pomodoro")
        }
        phase = if (phase == "focus") "short" else "focus"
        secondsLeft = (if (phase == "focus") focusMinutes else shortMinutes) * 60
    }

    val duration = if (phase == "focus") focusMinutes else shortMinutes
    val total = (duration * 60).coerceAtLeast(1)
    val progress = 1f - secondsLeft.toFloat() / total.toFloat()
    val todaySessions = sessions.filter { isToday(it.startedAt) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            listOf("focus" to "Focus", "short" to "Break").forEach { (value, label) ->
                FilterChip(
                    selected = phase == value,
                    onClick = {
                        phase = value
                        running = false
                        secondsLeft = (if (value == "focus") focusMinutes else shortMinutes) * 60
                    },
                    label = { Text(label) },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(
                text = "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                style = MaterialTheme.typography.headlineLarge,
            )
            ProgressBar(progress, modifier = Modifier.padding(horizontal = Tokens.Space.s6))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Button(onClick = { running = !running }) {
                    Text(if (running) "Pause" else "Start")
                }
                OutlinedButton(onClick = {
                    running = false
                    secondsLeft = duration * 60
                }) { Text("Reset") }
            }
        }

        SectionTitle("Subject")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            FilterChip(
                selected = subjectId.isBlank(),
                onClick = { subjectId = "" },
                label = { Text("None") },
            )
            subjects.forEach { subject ->
                FilterChip(
                    selected = subjectId == subject.id,
                    onClick = { subjectId = subject.id },
                    label = { Text(subject.name) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Today · ${todaySessions.sumOf { it.durationMinutes }} min")
        if (todaySessions.isEmpty()) {
            EmptyHint("No sessions yet", "Start the timer, or log one from a subject.")
        } else {
            todaySessions.forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                ) {
                    Text(
                        text = subjectById[session.subjectId]?.name ?: "Unassigned",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${session.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "Sessions are written to Firestore when the timer ends, so they appear on the web too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
