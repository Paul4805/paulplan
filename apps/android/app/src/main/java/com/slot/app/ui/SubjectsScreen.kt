package com.slot.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.slot.app.data.Subject
import com.slot.app.data.TaskItem
import java.util.Calendar
import java.util.Date

private fun inLastWeek(date: Date?): Boolean {
    if (date == null) return false
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    return date.after(weekAgo.time)
}

private fun StudySession.minutesInWeek(): Int = if (inLastWeek(startedAt)) durationMinutes else 0

@Composable
fun SubjectsScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val classes by vm.classes.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Subject?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var classOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Subject?>(null) }

    val chosen = selected?.let { current -> subjects.firstOrNull { it.id == current.id } }

    fun weeklyMinutes(subjectId: String): Int =
        sessions.filter { it.subjectId == subjectId }.sumOf { it.minutesInWeek() }

    if (chosen != null) {
        val slots = classes.filter { it.subjectId == chosen.id }.sortedBy { it.dayOfWeek * 1440 + it.startMinutes }
        val subjectTasks = tasks.filter { it.subjectId == chosen.id }
        val done = subjectTasks.count { it.status == "done" }
        val weekly = weeklyMinutes(chosen.id)
        val target = chosen.targetHoursPerWeek ?: 0.0

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
                TextButton(onClick = { selected = null }) { Text("← Subjects") }
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    OutlinedButton(onClick = { classOpen = true }) { Text("Add") }
                    Button(onClick = { vm.logSession(chosen.id, 30, "manual") }) { Text("Log 30 min") }
                }
            }

            Text(chosen.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = listOfNotNull(chosen.code, chosen.teacher).joinToString(" · ").ifBlank { "No code" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard("This week", "$weekly min", "target ${if (target > 0) "${target}h" else "—"}", Modifier.weight(1f))
                StatCard("Tasks", "$done/${subjectTasks.size}", "completed", Modifier.weight(1f))
            }

            ProgressBar(if (target > 0) weekly.toFloat() / (target * 60).toFloat() else 0f)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionTitle("Weekly slots")
            if (slots.isEmpty()) {
                EmptyHint("No slots", "Add the weekly class times for this subject.")
            } else {
                slots.forEach { slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                    ) {
                        Text(dayLabel(slot.dayOfWeek), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${minutesLabel(slot.startMinutes)}–${minutesLabel(slot.endMinutes)}" +
                                (slot.room?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionTitle("Tasks")
            if (subjectTasks.isEmpty()) {
                EmptyHint("No tasks", "Tasks linked to this subject show up here and in Tasks.")
            } else {
                subjectTasks.forEach { task ->
                    TaskRow(
                        task = task,
                        subject = chosen,
                        onToggle = { vm.setTaskStatus(task.id, if (task.status == "done") "todo" else "done") },
                        onClick = { vm.setTaskStatus(task.id, if (task.status == "done") "todo" else "done") },
                    )
                }
            }

            TextButton(onClick = { deleteTarget = chosen }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete subject", color = MaterialTheme.colorScheme.error)
            }
        }

        if (classOpen) {
            AddEntryDialog(
                subjects = listOf(chosen),
                defaultKind = "class",
                onDismiss = { classOpen = false },
                onSave = { kind, subjectId, title, color, day, start, end, room, date ->
                    vm.addEntry(kind, subjectId, title, color, day, start, end, room, date)
                },
            )
        }

        deleteTarget?.let { subject ->
            ConfirmDeleteDialog(
                title = "Delete subject?",
                body = "Classes for ${subject.name} stay in the timetable pointing at a missing subject. Delete those separately on the web.",
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    vm.deleteSubject(subject.id)
                    selected = null
                },
            )
        }

        return
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
            Text("Subjects", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { addOpen = true }) { Text("New") }
        }

        if (subjects.isEmpty()) {
            EmptyHint("No subjects", "A subject groups classes, tasks, and study time.")
        } else {
            subjects.forEach { subject ->
                val weekly = weeklyMinutes(subject.id)
                val target = subject.targetHoursPerWeek ?: 0.0
                val subjectTasks = tasks.filter { it.subjectId == subject.id }
                val done = subjectTasks.count { it.status == "done" }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = subject },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("●", color = tagColor(subject.color), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "$weekly min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ProgressBar(if (target > 0) weekly.toFloat() / (target * 60).toFloat() else 0f)
                        Text(
                            text = "${subjectTasks.size - done} open tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddSubjectDialog(
            onDismiss = { addOpen = false },
            onSave = { name, code, color, teacher, room, target ->
                vm.addSubject(name, code, color, teacher, room, target)
            },
        )
    }
}
