package com.slot.app.alarm

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slot.app.core.design.SlotTheme
import com.slot.app.core.design.Tokens
import com.slot.app.data.SlotRepository
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AlarmActivity : ComponentActivity() {
    private val alarmPlayer by lazy { AlarmPlayer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            getSystemService(android.app.KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        alarmPlayer.start()

        val eventId = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT_ID)
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Alarm"
        val detail = intent.getStringExtra(AlarmScheduler.EXTRA_DETAIL).orEmpty()

        if (eventId == null) {
            finish()
            return
        }

        setContent {
            SlotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AlarmContent(
                        title = title,
                        detail = detail,
                        onDismiss = { resolve(eventId, alarmId, title, "dismissed", null) },
                        onSnooze = {
                            val until = Date(System.currentTimeMillis() + 10 * 60_000L)
                            resolve(eventId, alarmId, title, "snoozed", until)
                        },
                    )
                }
            }
        }
    }

    private fun resolve(
        eventId: String,
        alarmId: String,
        title: String,
        state: String,
        snoozeUntil: Date?,
    ) {
        alarmPlayer.stop()
        getSystemService(NotificationManager::class.java)?.cancel(eventId.hashCode())

        if (snoozeUntil != null) {
            AlarmScheduler.armOne(
                this,
                ScheduledAlarm(
                    eventId = "snooze:$eventId:${snoozeUntil.time / 60_000}",
                    alarmId = alarmId,
                    title = title,
                    detail = "Snoozed",
                    triggerAtMillis = snoozeUntil.time,
                    fullScreen = true,
                    kind = "alarm",
                ),
            )
        }

        lifecycleScope.launch {
            withTimeoutOrNull(2_500) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val user = FirebaseAuth.getInstance().currentUser ?: return@runCatching
                        SlotRepository(FirebaseFirestore.getInstance(), user.uid)
                            .writeAlarmState(eventId, alarmId, state, snoozeUntil)
                    }
                }
            }
            finish()
        }
    }

    override fun onDestroy() {
        alarmPlayer.stop()
        super.onDestroy()
    }
}

@Composable
private fun AlarmContent(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Tokens.Space.s6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ALARM",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(Tokens.Space.s4))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(Tokens.Space.s2))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Tokens.Space.s10))

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        Spacer(Modifier.height(Tokens.Space.s2))
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
            Text("Snooze 10 minutes")
        }
    }
}
