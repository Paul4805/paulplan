package com.slot.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.slot.app.data.SlotViewModel
import com.slot.app.data.Subject
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

    fun isThisWeek(date: Date?): Boolean =
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

    val bySubject: List<Pair<Subject, Int>> = subjects
        .map { subject ->
            subject to study
                .filter { it.subjectId == subject.id && isThisWeek(it.startedAt) }
                .sumOf { it.durationMinutes }
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)

    val byType: List<Pair<String, Int>> = listOf("run", "football", "gym", "other")
        .map { type -> type to pt.filter { it.type == type }.sumOf { it.durationMinutes } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

    val days = remember { (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) } }
    val dayLabels = remember { days.map { it.dayOfWeek.name.take(1) } }
    val zone = ZoneId.systemDefault()

    fun dayOf(date: Date?): LocalDate? = date?.toInstant()?.atZone(zone)?.toLocalDate()

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

    val subjectRows = if (bySubject.isEmpty()) {
        emptyList()
    } else {
        val max = bySubject.maxOf { it.second }.coerceAtLeast(1).toFloat()
        bySubject.map { (subject, minutes) ->
            BreakdownRow(
                label = subject.name,
                value = hours(minutes),
                fraction = minutes / max,
                dot = subject.color,
            )
        }
    }

    val typeRows = if (byType.isEmpty()) {
        emptyList()
    } else {
        val max = byType.maxOf { it.second }.coerceAtLeast(1).toFloat()
        byType.map { (type, minutes) ->
            BreakdownRow(
                label = typeLabel(type),
                value = hours(minutes),
                fraction = minutes / max,
                dot = typeColorName(type),
            )
        }
    }

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
                "The last seven days, at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            SectionLabel("Academics")

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Studied",
                    value = hours(studyWeek),
                    hint = "this week · ${hours(studyAll)} total",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Tasks done",
                    value = doneWeek.toString(),
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
                title = "Study, last 7 days",
                total = hours(studyWeek),
                values = studyByDay,
                labels = dayLabels,
                color = MaterialTheme.colorScheme.primary,
                emptyText = "No study logged yet.",
            )

            BreakdownCard(
                title = "By subject, this week",
                rows = subjectRows,
                emptyText = "No study this week.",
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            SectionLabel("Physical training")

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Trained",
                    value = hours(ptWeek),
                    hint = "this week · ${hours(ptAll)} total",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Sessions",
                    value = ptWeekCount.toString(),
                    hint = "${pt.size} all time",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                StatCard(
                    label = "Distance",
                    value = "%.1f km".format(kmWeek),
                    hint = "this week",
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
                title = "Training, last 7 days",
                total = hours(ptWeek),
                values = ptByDay,
                labels = dayLabels,
                color = TRAINING_GREEN,
                emptyText = "No training logged this week.",
            )

            BreakdownCard(
                title = "By type, all time",
                rows = typeRows,
                emptyText = "No training logged.",
            )
        }

        Spacer(Modifier.height(Tokens.Space.s4))
    }
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
    labels: List<String>,
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

            WeekBars(values = values, labels = labels, color = color)

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
private fun WeekBars(values: List<Int>, labels: List<String>, color: Color) {
    val max = maxOf(60, values.maxOrNull() ?: 0)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
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
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == labels.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
