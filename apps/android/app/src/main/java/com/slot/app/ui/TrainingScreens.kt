package com.slot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slot.app.core.design.Tokens
import com.slot.app.data.PtSession
import com.slot.app.data.SlotViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun typeLabel(type: String): String = when (type) {
    "run" -> "Run"
    "football" -> "Football"
    "gym" -> "Gym"
    else -> "Training"
}

private fun typeColorName(type: String): String = when (type) {
    "run" -> "indigo"
    "football" -> "emerald"
    "gym" -> "amber"
    else -> "violet"
}

private fun hours(minutes: Int): String = "%.1f h".format(minutes / 60.0)

@Composable
fun TrainingScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val sessions by vm.ptSessions.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PtSession?>(null) }

    val weekStart = remember {
        LocalDate.now()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
    }

    val week = sessions.filter { (it.startedAt?.toInstant() ?: Instant.EPOCH) >= weekStart }
    val weekMinutes = week.sumOf { it.durationMinutes }
    val weekKm = week.sumOf { it.distanceKm ?: 0.0 }
    val allMinutes = sessions.sumOf { it.durationMinutes }

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
            Text("Training", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { addOpen = true }) { Text("Log") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard(
                label = "This week",
                value = hours(weekMinutes),
                hint = "${week.size} sessions",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Distance",
                value = "%.1f km".format(weekKm),
                hint = "this week",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard(
                label = "All time",
                value = hours(allMinutes),
                hint = "${sessions.size} sessions",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Disciplines",
                value = sessions.map { it.type }.distinct().size.toString(),
                hint = "types logged",
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Sessions")

        if (sessions.isEmpty()) {
            EmptyHint(
                "No training yet",
                "Log a run, a football match or a gym session. Strava archive import lives on the web.",
            )
        } else {
            sessions.forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tagColor(typeColorName(session.type))),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title ?: typeLabel(session.type),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                session.startedAt?.let { append(dateLabel(it)) }
                                if (session.source == "strava-csv") append(" · Strava")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${session.durationMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        session.distanceKm?.let {
                            Text(
                                text = "%.1f km".format(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { deleteTarget = session }) { Text("Remove") }
                }
            }
        }
    }

    if (addOpen) {
        AddTrainingDialog(
            onDismiss = { addOpen = false },
            onSave = { type, title, startedAt, minutes, km, notes ->
                vm.addPtSession(type, title, startedAt, minutes, km, notes)
            },
        )
    }

    deleteTarget?.let { session ->
        ConfirmDeleteDialog(
            title = "Delete session?",
            body = session.title ?: typeLabel(session.type),
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.deletePtSession(session.id) },
        )
    }
}

@Composable
private fun MiniBars(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val max = maxOf(60, values.maxOrNull() ?: 0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val fraction = if (value <= 0) 0.03f else (value.toFloat() / max).coerceIn(0.06f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (value > 0) color else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
fun StatsScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val study by vm.sessions.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val entries by vm.classes.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val pt by vm.ptSessions.collectAsStateWithLifecycle()

    val weekStart = remember {
        LocalDate.now()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
    }

    fun isThisWeek(date: java.util.Date?): Boolean =
        (date?.toInstant() ?: Instant.EPOCH) >= weekStart

    val studyWeek = study.filter { isThisWeek(it.startedAt) }.sumOf { it.durationMinutes }
    val studyAll = study.sumOf { it.durationMinutes }
    val ptWeek = pt.filter { isThisWeek(it.startedAt) }.sumOf { it.durationMinutes }
    val ptAll = pt.sumOf { it.durationMinutes }
    val ptWeekCount = pt.count { isThisWeek(it.startedAt) }
    val kmWeek = pt.filter { isThisWeek(it.startedAt) }.sumOf { it.distanceKm ?: 0.0 }

    val doneAll = tasks.count { it.status == "done" }
    val doneWeek = tasks.count { it.status == "done" && isThisWeek(it.completedAt) }
    val open = tasks.size - doneAll

    val bySubject = subjects
        .map { subject ->
            subject.name to study
                .filter { it.subjectId == subject.id && isThisWeek(it.startedAt) }
                .sumOf { it.durationMinutes }
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)

    val byType = listOf("run", "football", "gym", "other")
        .map { type -> type to pt.filter { it.type == type }.sumOf { it.durationMinutes } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

    val days = remember { (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) } }
    val zone = ZoneId.systemDefault()

    fun dayOf(date: java.util.Date?): LocalDate? =
        date?.toInstant()?.atZone(zone)?.toLocalDate()

    val studyByDay = days.map { day ->
        study.filter { dayOf(it.startedAt) == day }.sumOf { it.durationMinutes }
    }
    val ptByDay = days.map { day ->
        pt.filter { dayOf(it.startedAt) == day }.sumOf { it.durationMinutes }
    }

    val balance = if (studyWeek + ptWeek > 0) {
        "${Math.round(studyWeek * 100f / (studyWeek + ptWeek))}%"
    } else {
        "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard("Studied this week", hours(studyWeek), "all time ${hours(studyAll)}", Modifier.weight(1f))
            StatCard("Tasks done", doneWeek.toString(), "$doneAll all time · $open open", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard("Schedule", entries.size.toString(), "classes and events", Modifier.weight(1f))
            StatCard("Subjects", subjects.size.toString(), "active", Modifier.weight(1f))
        }

        SectionTitle("Study, last 7 days")
        MiniBars(studyByDay, MaterialTheme.colorScheme.primary)

        SectionTitle("Study by subject, this week")
        if (bySubject.isEmpty()) {
            EmptyHint("No study yet", "Log a session from Focus or a subject page.")
        } else {
            val max = bySubject.maxOf { it.second }.coerceAtLeast(1)
            bySubject.forEach { (name, minutes) ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            hours(minutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressBar(minutes.toFloat() / max)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("Physical training", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard("Trained this week", hours(ptWeek), "all time ${hours(ptAll)}", Modifier.weight(1f))
            StatCard("Sessions", ptWeekCount.toString(), "${pt.size} all time", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            StatCard("Distance", "%.1f km".format(kmWeek), "this week", Modifier.weight(1f))
            StatCard("Balance", balance, "study vs training", Modifier.weight(1f))
        }

        SectionTitle("Training, last 7 days")
        MiniBars(ptByDay, Color(0xFF22C55E))

        SectionTitle("Training by type, all time")
        if (byType.isEmpty()) {
            EmptyHint("No training logged", "Log a run or gym session in Training.")
        } else {
            val max = byType.maxOf { it.second }.coerceAtLeast(1)
            byType.forEach { (type, minutes) ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tagColor(typeColorName(type))),
                            )
                            Text(typeLabel(type), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            hours(minutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressBar(minutes.toFloat() / max)
                }
            }
        }
    }
}
