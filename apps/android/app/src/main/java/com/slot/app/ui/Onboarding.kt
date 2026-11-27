package com.slot.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slot.app.alarm.AlarmScheduler
import com.slot.app.alarm.NotificationChannels
import com.slot.app.core.design.Tokens

private fun android.content.Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * First-run delivery check. Alarms are only trustworthy when notifications,
 * exact alarms and full-screen intents are actually allowed on this phone.
 */
@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val notifications = remember(refresh) { NotificationChannels.isGranted(context) }
    val exact = remember(refresh) { NotificationChannels.canScheduleExact(context) }
    val fullScreen = remember(refresh) { NotificationChannels.canUseFullScreen(context) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Make alarms reliable") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Text(
                    "PaulPlan fires alarms on this phone, offline and after a reboot. Three permissions decide whether they actually ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PermissionLine("Notifications", notifications) {
                    context.startSafely(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
                PermissionLine("Exact alarms", exact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startSafely(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
                PermissionLine("Full-screen alarms", fullScreen) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        context.startSafely(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    }
                }

                TextButton(onClick = { AlarmScheduler.fireTest(context) }) {
                    Text("Send a test alarm in 10 seconds")
                }

                Text(
                    "Xiaomi, Oppo and Samsung may also need PaulPlan allowed in their own battery settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { refresh += 1 }) { Text("Recheck") }
        },
    )
}

@Composable
private fun PermissionLine(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (granted) "On" else "Off",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onClick, enabled = !granted) { Text(if (granted) "OK" else "Allow") }
    }
}
