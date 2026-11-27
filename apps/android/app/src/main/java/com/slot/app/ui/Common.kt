package com.slot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slot.app.core.design.LocalTagPalette
import com.slot.app.core.design.Tokens
import com.slot.app.data.Profile
import com.slot.app.data.Subject
import com.slot.app.data.TaskItem
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun minutesLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun dayLabel(dayOfWeek: Int): String = DAY_LABELS.getOrElse(dayOfWeek - 1) { "—" }

fun dateLabel(date: Date?): String =
    date?.let { SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(it) } ?: "No deadline"

fun shortDateLabel(date: Date): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date)

/** True when the current date is inside the configured term (or no term set). */
fun termActive(profile: Profile, today: LocalDate = LocalDate.now()): Boolean {
    val start = profile.termStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = profile.termEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start == null && end == null) return true
    return (start == null || !today.isBefore(start)) && (end == null || !today.isAfter(end))
}

fun termMessage(profile: Profile, today: LocalDate = LocalDate.now()): String? {
    if (termActive(profile, today)) return null
    val start = profile.termStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (start != null && today.isBefore(start)) {
        "The term has not started, so weekly classes are hidden."
    } else {
        "The term has ended, so weekly classes are hidden."
    }
}

@Composable
fun tagColor(name: String): Color {
    val palette = LocalTagPalette.current
    return when (name) {
        "sky" -> palette.sky
        "violet" -> palette.violet
        "amber" -> palette.amber
        "rose" -> palette.rose
        "emerald" -> palette.emerald
        else -> palette.indigo
    }
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
    }
}

@Composable
fun EmptyHint(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.card))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Tokens.Radius.card))
            .padding(Tokens.Space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StatCard(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(Tokens.Radius.card),
    ) {
        Column(modifier = Modifier.padding(Tokens.Space.s4)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(text = value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Tokens.Space.s1))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier) {
    val clamped = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(Tokens.Radius.pill))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun SubjectLine(
    subject: Subject?,
    fallback: String = "No subject",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        if (subject != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tagColor(subject.color)),
            )
        }
        Text(
            text = subject?.name ?: fallback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: TaskItem,
    subject: Subject?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val done = task.status == "done"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.card))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Tokens.Radius.card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    1.dp,
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(4.dp),
                )
                .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                if (subject != null) {
                    SubjectLine(subject, modifier = Modifier.weight(1f, fill = false))
                }
                Text(
                    text = dateLabel(task.dueAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (task.priority != "low") {
            Text(
                text = task.priority.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (task.priority == "high") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
