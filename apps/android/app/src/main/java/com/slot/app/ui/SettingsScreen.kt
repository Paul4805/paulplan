package com.slot.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slot.app.BuildConfig
import com.slot.app.alarm.AlarmMath
import com.slot.app.alarm.NotificationChannels
import com.slot.app.core.design.Tokens
import com.slot.app.data.AlarmItem
import com.slot.app.data.SlotViewModel
import java.time.ZoneId
import java.util.Date

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SettingsScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()

    var refresh by remember { mutableIntStateOf(0) }
    var name by remember(user?.uid) { mutableStateOf(user?.displayName ?: "") }
    var alarmOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AlarmItem?>(null) }
    var garminKey by remember { mutableStateOf("") }
    val garminConfigured by vm.garminConfigured.collectAsStateWithLifecycle()
    val garminLastSync by vm.garminLastSync.collectAsStateWithLifecycle()

    val notifications = remember(refresh) { NotificationChannels.isGranted(context) }
    val exact = remember(refresh) { NotificationChannels.canScheduleExact(context) }
    val battery = remember(refresh) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = user?.email ?: "Not signed in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    OutlinedButton(
                        onClick = { vm.renameUser(name) },
                        enabled = name.isNotBlank() && name != user?.displayName,
                    ) { Text("Save name") }
                    Button(onClick = { vm.signOut() }) { Text("Sign out") }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = profile.theme == value,
                            onClick = { vm.setTheme(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    text = "Timezone · ${profile.timezone ?: ZoneId.systemDefault().id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Alarms", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        TextButton(onClick = { refresh += 1 }) { Text("Refresh") }
                        Button(onClick = { alarmOpen = true }) { Text("Add") }
                    }
                }

                Text(
                    "Alarms are scheduled locally with AlarmManager and re-armed after a reboot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (alarms.isEmpty()) {
                    EmptyHint("No alarms", "Add one here or on the web dashboard.")
                } else {
                    alarms.forEach { alarm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = alarm.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = AlarmMath.describe(alarm) +
                                        if (alarm.leadMinutes > 0) " · ${alarm.leadMinutes} min early" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = alarm.enabled,
                                onCheckedChange = { vm.setAlarmEnabled(alarm.id, it) },
                            )
                            TextButton(onClick = { deleteTarget = alarm }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("Delivery health", style = MaterialTheme.typography.titleMedium)
                PermissionRow(
                    label = "Notifications",
                    granted = notifications,
                    action = "Open",
                    onClick = {
                        context.startSafely(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
                PermissionRow(
                    label = "Exact alarms",
                    granted = exact,
                    action = "Grant",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startSafely(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                )
                PermissionRow(
                    label = "Full-screen alarms",
                    granted = NotificationChannels.canUseFullScreen(context),
                    action = "Allow",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 34) {
                            context.startSafely(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                )
                PermissionRow(
                    label = "Battery unrestricted",
                    granted = battery,
                    action = "Allow",
                    onClick = {
                        context.startSafely(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
                Text(
                    "Some phones (Xiaomi, Oppo, Samsung) also need Slot allowed in their own battery settings. Without these, an alarm can be delayed or suppressed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("Garmin", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Personal API key from Garmin Connect → Settings → Personal API. It stays on this phone; the web reads the synced sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!garminConfigured) {
                    OutlinedTextField(
                        value = garminKey,
                        onValueChange = { garminKey = it },
                        label = { Text("Personal API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            vm.saveGarminKey(garminKey)
                            garminKey = ""
                        },
                        enabled = garminKey.isNotBlank(),
                    ) { Text("Save & sync") }
                } else {
                    Text(
                        if (garminLastSync > 0) {
                            "Connected · last sync ${dateLabel(Date(garminLastSync))}"
                        } else {
                            "Connected · not synced yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Button(onClick = { vm.syncGarmin(manual = true) }) { Text("Sync now") }
                        TextButton(onClick = { vm.disconnectGarmin() }) { Text("Disconnect") }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            "Slot ${BuildConfig.VERSION_NAME} · Firestore Spark · alarms are local, sync is peer-to-peer through Firestore",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (alarmOpen) {
        AddAlarmDialog(
            onDismiss = { alarmOpen = false },
            onSave = { title, recurrence, day, minutes, lead ->
                vm.addAlarm(title, recurrence, day, minutes, lead)
            },
        )
    }

    deleteTarget?.let { alarm ->
        ConfirmDeleteDialog(
            title = "Delete alarm?",
            body = alarm.title,
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.deleteAlarm(alarm.id) },
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "On" else "Off",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onClick, enabled = !granted) { Text(action) }
    }
}
