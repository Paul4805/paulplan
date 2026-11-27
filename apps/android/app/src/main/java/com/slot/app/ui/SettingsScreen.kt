package com.slot.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.slot.app.alarm.AlarmScheduler
import com.slot.app.alarm.NotificationChannels
import com.slot.app.core.design.Tokens
import com.slot.app.data.AlarmItem
import com.slot.app.data.GarminStore
import com.slot.app.data.Profile
import com.slot.app.data.SlotViewModel
import java.time.ZoneId
import java.util.Date

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val SECTIONS = listOf(
    "account" to "Account",
    "appearance" to "Appearance",
    "alarms" to "Alarms",
    "connections" to "Connections",
    "term" to "Term",
    "data" to "Data",
)

@Composable
fun SettingsScreen(vm: SlotViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf("account") }
    var refresh by remember { mutableIntStateOf(0) }
    var name by remember(user?.uid) { mutableStateOf(user?.displayName ?: "") }
    var alarmOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AlarmItem?>(null) }
    var archiveOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var garminKey by remember { mutableStateOf("") }

    val garminConfigured by vm.garminConfigured.collectAsStateWithLifecycle()
    val garminLastSync by vm.garminLastSync.collectAsStateWithLifecycle()
    val garminHealthAt = remember(garminLastSync) { GarminStore.healthAt(context) }
    val garminHealthDays = remember(garminLastSync) { GarminStore.healthDays(context) }
    val garminHealthError = remember(garminLastSync) { GarminStore.healthError(context) }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            SECTIONS.forEach { (value, label) ->
                FilterChip(
                    selected = section == value,
                    onClick = { section = value },
                    label = { Text(label) },
                )
            }
        }

        when (section) {
            "account" -> AccountSettings(
                email = user?.email,
                displayName = user?.displayName,
                name = name,
                onNameChange = { name = it },
                onSave = { vm.renameUser(name) },
                onSignOut = { vm.signOut() },
            )

            "appearance" -> AppearanceSettings(profile = profile, onTheme = { vm.setTheme(it) })

            "alarms" -> AlarmsSettings(
                alarms = alarms,
                context = context,
                notifications = notifications,
                exact = exact,
                fullScreen = NotificationChannels.canUseFullScreen(context),
                battery = battery,
                onRefresh = { refresh += 1 },
                onAdd = { alarmOpen = true },
                onToggle = { alarm, enabled -> vm.setAlarmEnabled(alarm.id, enabled) },
                onDelete = { deleteTarget = it },
            )

            "connections" -> ConnectionsSettings(
                context = context,
                configured = garminConfigured,
                lastSync = garminLastSync,
                healthAt = garminHealthAt,
                healthDays = garminHealthDays,
                healthError = garminHealthError,
                key = garminKey,
                onKeyChange = { garminKey = it },
                onSaveKey = {
                    vm.saveGarminKey(garminKey)
                    garminKey = ""
                },
                onSync = { vm.syncGarmin(manual = true) },
                onDisconnect = { vm.disconnectGarmin() },
            )

            "term" -> TermSettings(profile = profile, onArchive = { archiveOpen = true }, onRestore = { vm.restoreArchived() })

            "data" -> DataSettings(onDelete = { deleteOpen = true })
        }

        Text(
            "PaulPlan ${BuildConfig.VERSION_NAME} · Firestore Spark · alarms are local, sync is peer-to-peer through Firestore",
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

    if (archiveOpen) {
        ConfirmDeleteDialog(
            title = "Archive this term?",
            body = "Every subject and task will be hidden and the weekly timetable cleared. Study and training history stay.",
            confirmLabel = "Archive",
            onDismiss = { archiveOpen = false },
            onConfirm = { vm.archiveTerm() },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = {
                deleteOpen = false
                deletePassword = ""
            },
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(
                        "This deletes every subject, class, task, session and setting. It cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAccount(deletePassword)
                        deleteOpen = false
                        deletePassword = ""
                    },
                    enabled = deletePassword.isNotBlank(),
                ) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        deletePassword = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            content = content,
        )
    }
}

@Composable
private fun AccountSettings(
    email: String?,
    displayName: String?,
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
) {
    SettingsCard {
        Text("Account", style = MaterialTheme.typography.titleMedium)
        Text(
            text = email ?: "Not signed in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            OutlinedButton(
                onClick = onSave,
                enabled = name.isNotBlank() && name != displayName,
            ) { Text("Save name") }
            Button(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@Composable
private fun AppearanceSettings(profile: Profile, onTheme: (String) -> Unit) {
    SettingsCard {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                FilterChip(
                    selected = profile.theme == value,
                    onClick = { onTheme(value) },
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

@Composable
private fun AlarmsSettings(
    alarms: List<AlarmItem>,
    context: Context,
    notifications: Boolean,
    exact: Boolean,
    fullScreen: Boolean,
    battery: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (AlarmItem, Boolean) -> Unit,
    onDelete: (AlarmItem) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alarms", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                Button(onClick = onAdd) { Text("Add") }
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
                        onCheckedChange = { onToggle(alarm, it) },
                    )
                    TextButton(onClick = { onDelete(alarm) }) { Text("Delete") }
                }
            }
        }
    }

    SettingsCard {
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
            granted = fullScreen,
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
            "Some phones (Xiaomi, Oppo, Samsung) also need PaulPlan allowed in their own battery settings. Without these, an alarm can be delayed or suppressed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Button(onClick = { AlarmScheduler.fireTest(context) }) {
                Text("Send test alarm")
            }
            TextButton(onClick = onRefresh) { Text("Recheck") }
        }
        Text(
            "The test fires full-screen in 10 seconds. Lock the phone to check the lock-screen path.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionsSettings(
    context: Context,
    configured: Boolean,
    lastSync: Long,
    healthAt: Long,
    healthDays: Int,
    healthError: String?,
    key: String,
    onKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SettingsCard {
        Text("Connections", style = MaterialTheme.typography.titleMedium)
        Text("Garmin", style = MaterialTheme.typography.titleSmall)
        Text(
            "Personal API key from Garmin Connect → Settings → Personal API. It stays on this phone; the web reads the synced sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!configured) {
            OutlinedTextField(
                value = key,
                onValueChange = onKeyChange,
                label = { Text("Personal API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSaveKey, enabled = key.isNotBlank()) { Text("Save & sync") }
        } else {
            Text(
                if (lastSync > 0) {
                    "Connected · last sync ${dateLabel(Date(lastSync))}"
                } else {
                    "Connected · not synced yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (healthAt > 0) {
                    "Health · $healthDays days synced"
                } else {
                    "Health · waiting for the next sync"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!healthError.isNullOrBlank()) {
                Text(
                    "Health sync: $healthError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Button(onClick = onSync) { Text("Sync now") }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun TermSettings(profile: Profile, onArchive: () -> Unit, onRestore: () -> Unit) {
    SettingsCard {
        Text("Term", style = MaterialTheme.typography.titleMedium)
        Text(
            profile.termName?.takeIf { it.isNotBlank() } ?: "No term name",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            listOfNotNull(profile.termStart, profile.termEnd).joinToString(" – ")
                .ifBlank { "Set dates on the web dashboard." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Edit term dates on the web. Archiving hides every subject and task and clears the weekly timetable; history is kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            OutlinedButton(onClick = onRestore) { Text("Restore archived") }
            Button(
                onClick = onArchive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Archive term") }
        }
    }
}

@Composable
private fun DataSettings(onDelete: () -> Unit) {
    SettingsCard {
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Delete your account and everything under it. Export a backup from the web dashboard first if you want a copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text("Delete account") }
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
