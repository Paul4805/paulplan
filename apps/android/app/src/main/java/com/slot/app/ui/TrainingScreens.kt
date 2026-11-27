package com.slot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slot.app.core.design.Tokens
import com.slot.app.data.PtSession
import com.slot.app.data.SlotViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun typeLabel(type: String): String = when (type) {
    "run" -> "Run"
    "football" -> "Football"
    "gym" -> "Gym"
    else -> "Training"
}

fun typeColorName(type: String): String = when (type) {
    "run" -> "indigo"
    "football" -> "emerald"
    "gym" -> "amber"
    else -> "violet"
}

fun hours(minutes: Int): String = "%.1f h".format(minutes / 60.0)

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
                                when (session.source) {
                                    "strava-csv" -> append(" · Strava")
                                    "garmin-json", "garmin-api" -> append(" · Garmin")
                                }
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
