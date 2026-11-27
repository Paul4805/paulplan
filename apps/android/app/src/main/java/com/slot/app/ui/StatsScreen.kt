package com.slot.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slot.app.core.design.Tokens
import com.slot.app.data.DailyHealth
import com.slot.app.data.PtSession
import com.slot.app.data.SlotViewModel
import com.slot.app.data.Subject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private val TRAINING_GREEN = Color(0xFF22C55E)

private data class BreakdownRow(
    val label: String,
    val value: String,
    val fraction: Float,
    val dot: String? = null,
)

private data class StreakStats(val current: Int, val longest: Int, val total: Int)

@Composable
fun StatsScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val study by vm.sessions.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val entries by vm.classes.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val pt by vm.ptSessions.collectAsStateWithLifecycle()
    val health by vm.dailyHealth.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    var range by rememberSaveable { mutableStateOf("week") }
    var detail by remember { mutableStateOf<PtSession?>(null) }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val termStartDate = profile.termStart?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    val rangeStartDate = when (range) {
        "month" -> today.withDayOfMonth(1)
        "term" -> termStartDate ?: today.minusDays(30)
        else -> today.with(DayOfWeek.MONDAY)
    }
    val earliestChart = today.minusDays(30)
    val chartStart = if (rangeStartDate.isBefore(earliestChart)) earliestChart else rangeStartDate
    val days = generateSequence(chartStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .ifEmpty { listOf(today) }
    val startInstant = rangeStartDate.atStartOfDay(zone).toInstant()

    fun inRange(date: Date?): Boolean =
        (date?.toInstant() ?: Instant.EPOCH) >= startInstant

    fun dayOf(date: Date?): LocalDate? = date?.toInstant()?.atZone(zone)?.toLocalDate()

    val studyRange = study.filter { inRange(it.startedAt) }.sumOf { it.durationMinutes }
    val studyAll = study.sumOf { it.durationMinutes }
    val ptRange = pt.filter { inRange(it.startedAt) }.sumOf { it.durationMinutes }
    val ptAll = pt.sumOf { it.durationMinutes }
    val ptRangeCount = pt.count { inRange(it.startedAt) }
    val kmRange = pt.filter { inRange(it.startedAt) }.sumOf { it.distanceKm ?: 0.0 }

    val doneAll = tasks.count { it.status == "done" }
    val doneRange = tasks.count { it.status == "done" && inRange(it.completedAt) }
    val open = tasks.size - doneAll

    val bySubject: List<Pair<Subject, Int>> = subjects
        .filterNot { it.archived }
        .map { subject ->
            subject to study
                .filter { it.subjectId == subject.id && inRange(it.startedAt) }
                .sumOf { it.durationMinutes }
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)

    val byType: List<Pair<String, Int>> = listOf("run", "football", "gym", "other")
        .map { type -> type to pt.filter { it.type == type && inRange(it.startedAt) }.sumOf { it.durationMinutes } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

    val studyByDay: Map<LocalDate, Int> = study
        .mapNotNull { session -> dayOf(session.startedAt)?.let { it to session.durationMinutes } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

    val ptByDay: Map<LocalDate, Int> = pt
        .mapNotNull { session -> dayOf(session.startedAt)?.let { it to session.durationMinutes } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

    val studyStreak = streakFor(studyByDay)
    val ptStreak = streakFor(ptByDay)

    val recentHealth = health.filter {
        val date = runCatching { LocalDate.parse(it.date) }.getOrNull() ?: return@filter false
        !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
    }
    val avgSleep = recentHealth.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.average()
    val avgSteps = recentHealth.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()
    val avgHrv = recentHealth.mapNotNull { it.hrv }.takeIf { it.isNotEmpty() }?.average()
    val latestScore = recentHealth.firstOrNull()?.sleepScore

    val balance = if (studyRange + ptRange > 0) {
        "${Math.round(studyRange * 100f / (studyRange + ptRange))}%"
    } else {
        "—"
    }

    fun rowsFor(pairs: List<Pair<String, Int>>, dots: List<String?>): List<BreakdownRow> {
        if (pairs.isEmpty()) return emptyList()
        val max = pairs.maxOf { it.second }.coerceAtLeast(1).toFloat()
        return pairs.mapIndexed { index, (label, minutes) ->
            BreakdownRow(label, hours(minutes), minutes / max, dots.getOrNull(index))
        }
    }

    val subjectRows = rowsFor(
        bySubject.map { it.first.name to it.second },
        bySubject.map { it.first.color },
    )
    val typeRows = rowsFor(
        byType.map { typeLabel(it.first) to it.second },
        byType.map { typeColorName(it.first) },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s6),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s6),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text("Stats", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Study, training and recovery at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            listOf("week" to "Week", "month" to "Month", "term" to "Term").forEach { (value, label) ->
                FilterChip(
                    selected = range == value,
                    onClick = { range = value },
                    label = { Text(label) },
                )
            }
        }

        Text(
            when (range) {
                "month" -> "This month"
                "term" -> profile.termName?.takeIf { it.isNotBlank() } ?: "This term"
                else -> "This week"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            SectionLabel("Academics")

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Studied",
                    value = hours(studyRange),
                    hint = "total ${hours(studyAll)}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Tasks done",
                    value = doneRange.toString(),
                    hint = if (open > 0) "$open open" else "all clear",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Schedule",
                    value = entries.size.toString(),
                    hint = "classes and events",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Subjects",
                    value = subjects.size.toString(),
                    hint = "active",
                    modifier = Modifier.weight(1f),
                )
            }

            ChartCard(
                title = "Study",
                total = hours(studyRange),
                values = days.map { studyByDay[it] ?: 0 },
                days = days,
                color = MaterialTheme.colorScheme.primary,
                emptyText = "No study logged in this range.",
            )

            BreakdownCard(
                title = "By subject",
                rows = subjectRows,
                emptyText = "No study in this range.",
            )

            HeatmapCard(
                title = "Study heatmap",
                streak = studyStreak,
                values = studyByDay,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            SectionLabel("Physical training")

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Trained",
                    value = hours(ptRange),
                    hint = "total ${hours(ptAll)}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Sessions",
                    value = ptRangeCount.toString(),
                    hint = "${pt.size} all time",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Distance",
                    value = "%.1f km".format(kmRange),
                    hint = "this range",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Balance",
                    value = balance,
                    hint = "study vs training",
                    modifier = Modifier.weight(1f),
                )
            }

            ChartCard(
                title = "Training",
                total = hours(ptRange),
                values = days.map { ptByDay[it] ?: 0 },
                days = days,
                color = TRAINING_GREEN,
                emptyText = "No training logged in this range.",
            )

            BreakdownCard(
                title = "By type",
                rows = typeRows,
                emptyText = "No training in this range.",
            )

            HeatmapCard(
                title = "Training heatmap",
                streak = ptStreak,
                values = ptByDay,
                color = TRAINING_GREEN,
            )

            if (recentHealth.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    StatCard(
                        label = "Sleep",
                        value = avgSleep?.let { sleepLabel(it) } ?: "—",
                        hint = latestScore?.let { "score $it" } ?: "7-day average",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Steps",
                        value = avgSteps?.let { "%,.0f".format(it) } ?: "—",
                        hint = "daily average",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "HRV",
                        value = avgHrv?.let { "${Math.round(it)} ms" } ?: "—",
                        hint = "last night",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                EmptyHint(
                    "No recovery data",
                    "Connect Garmin in Settings to sync sleep, steps and HRV.",
                )
            }

            SectionLabel("Recent sessions")
            if (pt.isEmpty()) {
                EmptyHint("No training yet", "Log a run, a match or a gym session.")
            } else {
                pt.take(6).forEach { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Tokens.Radius.card))
                            .clickable { detail = session }
                            .padding(vertical = Tokens.Space.s2),
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
                                    session.startedAt?.let { append(shortDateLabel(it)) }
                                    session.distanceKm?.let { append(" · %.1f km".format(it)) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${session.durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Tokens.Space.s4))
    }

    detail?.let { session ->
        SessionDetailDialog(session = session, onDismiss = { detail = null })
    }
}

private fun streakFor(values: Map<LocalDate, Int>): StreakStats {
    val today = LocalDate.now()
    val days = (83 downTo 0).map { today.minusDays(it.toLong()) }
    var current = 0
    for (index in days.indices.reversed()) {
        if ((values[days[index]] ?: 0) > 0) current += 1 else break
    }
    var longest = 0
    var run = 0
    for (day in days) {
        if ((values[day] ?: 0) > 0) {
            run += 1
            if (run > longest) longest = run
        } else {
            run = 0
        }
    }
    val active = days.count { (values[it] ?: 0) > 0 }
    return StreakStats(current, maxOf(longest, current), active)
}

private fun sleepLabel(minutes: Double): String {
    val whole = minutes.toInt()
    return "${whole / 60}h ${whole % 60}m"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun ChartCard(
    title: String,
    total: String,
    values: List<Int>,
    days: List<LocalDate>,
    color: Color,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    total,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DayBars(values = values, days = days, color = color)

            if (values.all { it == 0 }) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayBars(values: List<Int>, days: List<LocalDate>, color: Color) {
    val max = maxOf(60, values.maxOrNull() ?: 0)
    val compact = values.size > 10
    val labelEvery = if (values.size <= 7) 1 else if (values.size <= 14) 2 else 5

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else Tokens.Space.s2),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEach { value ->
                val fraction = if (value <= 0) {
                    0.015f
                } else {
                    (value.toFloat() / max).coerceIn(0.06f, 1f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (value > 0) color else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }

        Spacer(Modifier.height(Tokens.Space.s2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else Tokens.Space.s2),
        ) {
            days.forEachIndexed { index, day ->
                val show = index % labelEvery == 0 || index == days.lastIndex
                Text(
                    text = if (!show) "" else if (days.size <= 7) {
                        day.dayOfWeek.name.take(1)
                    } else {
                        day.dayOfMonth.toString()
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == days.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(
    title: String,
    streak: StreakStats,
    values: Map<LocalDate, Int>,
    color: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Last 12 weeks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${streak.current} day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "best ${streak.longest} · ${streak.total} active days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Heatmap(values = values, color = color)
        }
    }
}

@Composable
private fun Heatmap(values: Map<LocalDate, Int>, color: Color) {
    val today = LocalDate.now()
    val start = today.with(DayOfWeek.MONDAY).minusWeeks(11)
    val max = maxOf(1, values.values.maxOrNull() ?: 0)

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (week in 0 until 12) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dayIndex in 0 until 7) {
                    val date = start.plusDays((week * 7 + dayIndex).toLong())
                    val value = values[date] ?: 0
                    val future = date.isAfter(today)
                    val level = if (value <= 0) 0 else maxOf(1, (value * 4 + max - 1) / max)
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    future -> Color.Transparent
                                    level == 0 -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    else -> color.copy(alpha = 0.25f + level * 0.19f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    rows: List<BreakdownRow>,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            if (rows.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                            ) {
                                if (row.dot != null) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(tagColor(row.dot)),
                                    )
                                }
                                Text(
                                    text = row.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = row.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ProgressBar(row.fraction)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailDialog(session: PtSession, onDismiss: () -> Unit) {
    val pace = session.distanceKm?.takeIf { it > 0 }?.let {
        val seconds = session.durationMinutes * 60.0 / it
        "%d:%02d /km".format((seconds / 60).toInt(), (seconds % 60).toInt())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title ?: typeLabel(session.type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                session.startedAt?.let {
                    Text(
                        dateLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DetailRow("Duration", "${session.durationMinutes} min")
                DetailRow("Distance", session.distanceKm?.let { "%.2f km".format(it) } ?: "—")
                DetailRow("Pace", pace ?: "—")
                DetailRow("Average HR", session.avgHr?.let { "${Math.round(it)} bpm" } ?: "—")
                DetailRow("Max HR", session.maxHr?.let { "${Math.round(it)} bpm" } ?: "—")
                DetailRow("Elevation", session.elevationGain?.let { "${Math.round(it)} m" } ?: "—")
                DetailRow("Calories", session.calories?.let { "${Math.round(it)} kcal" } ?: "—")
                DetailRow(
                    "Source",
                    when (session.source) {
                        "garmin-api", "garmin-json" -> "Garmin"
                        "strava-csv" -> "Strava"
                        else -> "Manual"
                    },
                )
                session.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
