package com.slot.app.focus

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class FocusState(
    val phase: String = "focus",
    val running: Boolean = false,
    val endsAt: Long = 0L,
    val remainingSec: Int = 25 * 60,
    val subjectId: String? = null,
    val minutes: Int = 25,
    val focusMinutes: Int = 25,
    val shortMinutes: Int = 5,
)

data class FinishedFocus(
    val focus: Boolean,
    val minutes: Int,
    val subjectId: String?,
)

/**
 * The pomodoro lives in a foreground service, so it survives the app being
 * backgrounded or killed. This object owns the state and mirrors it into
 * SharedPreferences; [FocusService] owns the countdown and the notification.
 */
object FocusTimer {
    private const val PREFS = "slot_focus"
    private const val KEY_PHASE = "phase"
    private const val KEY_RUNNING = "running"
    private const val KEY_ENDS_AT = "ends_at"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_SUBJECT = "subject_id"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_FOCUS_MINUTES = "focus_minutes"
    private const val KEY_SHORT_MINUTES = "short_minutes"
    private const val KEY_LAST_FINISH = "last_finish"

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true

        val prefs = prefs(context)
        val phase = prefs.getString(KEY_PHASE, "focus") ?: "focus"
        val focusMinutes = prefs.getInt(KEY_FOCUS_MINUTES, 25)
        val shortMinutes = prefs.getInt(KEY_SHORT_MINUTES, 5)
        val minutes = prefs.getInt(KEY_MINUTES, if (phase == "focus") focusMinutes else shortMinutes)
        val running = prefs.getBoolean(KEY_RUNNING, false)
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        val remaining = if (running && endsAt > 0) {
            max(0, ((endsAt - System.currentTimeMillis()) / 1000).toInt())
        } else {
            prefs.getInt(KEY_REMAINING, minutes * 60)
        }

        _state.value = FocusState(
            phase = phase,
            running = running && endsAt > 0,
            endsAt = endsAt,
            remainingSec = remaining,
            subjectId = prefs.getString(KEY_SUBJECT, null),
            minutes = minutes,
            focusMinutes = focusMinutes,
            shortMinutes = shortMinutes,
        )
    }

    fun ensureService(context: Context) {
        load(context)
        val state = _state.value
        if (state.running && state.endsAt > 0) startService(context)
    }

    fun start(
        context: Context,
        phase: String,
        subjectId: String?,
        focusMinutes: Int,
        shortMinutes: Int,
    ) {
        load(context)
        val minutes = if (phase == "focus") focusMinutes else shortMinutes
        val next = _state.value.copy(
            phase = phase,
            running = true,
            endsAt = System.currentTimeMillis() + minutes * 60_000L,
            remainingSec = minutes * 60,
            subjectId = subjectId,
            minutes = minutes,
            focusMinutes = focusMinutes,
            shortMinutes = shortMinutes,
        )
        setState(context, next)
        startService(context)
    }

    fun pause(context: Context) {
        load(context)
        val state = _state.value
        if (!state.running) return
        val remaining = max(0, ((state.endsAt - System.currentTimeMillis()) / 1000).toInt())
        setState(context, state.copy(running = false, endsAt = 0L, remainingSec = remaining))
        context.stopService(Intent(context, FocusService::class.java))
    }

    fun reset(context: Context) {
        load(context)
        val state = _state.value
        setState(
            context,
            state.copy(
                running = false,
                endsAt = 0L,
                remainingSec = state.minutes * 60,
            ),
        )
        context.stopService(Intent(context, FocusService::class.java))
    }

    fun stop(context: Context) {
        load(context)
        val state = _state.value
        setState(
            context,
            state.copy(running = false, endsAt = 0L, remainingSec = state.minutes * 60),
        )
        context.stopService(Intent(context, FocusService::class.java))
    }

    fun setPhase(context: Context, phase: String, focusMinutes: Int, shortMinutes: Int) {
        load(context)
        val minutes = if (phase == "focus") focusMinutes else shortMinutes
        setState(
            context,
            _state.value.copy(
                phase = phase,
                running = false,
                endsAt = 0L,
                remainingSec = minutes * 60,
                minutes = minutes,
                focusMinutes = focusMinutes,
                shortMinutes = shortMinutes,
            ),
        )
        context.stopService(Intent(context, FocusService::class.java))
    }

    fun skip(context: Context) {
        load(context)
        val state = _state.value
        val nextPhase = if (state.phase == "focus") "short" else "focus"
        val minutes = if (nextPhase == "focus") state.focusMinutes else state.shortMinutes
        setState(
            context,
            state.copy(
                phase = nextPhase,
                running = false,
                endsAt = 0L,
                remainingSec = minutes * 60,
                minutes = minutes,
            ),
        )
        context.stopService(Intent(context, FocusService::class.java))
    }

    fun setSubject(context: Context, subjectId: String?) {
        load(context)
        setState(context, _state.value.copy(subjectId = subjectId))
    }

    fun remaining(context: Context): Int {
        load(context)
        val state = _state.value
        return if (state.running && state.endsAt > 0) {
            max(0, ((state.endsAt - System.currentTimeMillis()) / 1000).toInt())
        } else {
            state.remainingSec
        }
    }

    fun syncRemaining(context: Context, seconds: Int) {
        val state = _state.value
        if (state.running && state.remainingSec != seconds) {
            _state.value = state.copy(remainingSec = seconds)
        }
    }

    /** Advances to the next phase once; safe to call again after a restart. */
    fun finish(context: Context): FinishedFocus? {
        load(context)
        val state = _state.value
        if (!state.running) return null

        val prefs = prefs(context)
        if (state.endsAt > 0 && prefs.getLong(KEY_LAST_FINISH, 0L) == state.endsAt) return null
        if (state.endsAt > 0) prefs.edit().putLong(KEY_LAST_FINISH, state.endsAt).apply()

        val nextPhase = if (state.phase == "focus") "short" else "focus"
        val minutes = if (nextPhase == "focus") state.focusMinutes else state.shortMinutes
        setState(
            context,
            state.copy(
                phase = nextPhase,
                running = false,
                endsAt = 0L,
                remainingSec = minutes * 60,
                minutes = minutes,
                subjectId = state.subjectId,
            ),
        )

        return FinishedFocus(
            focus = state.phase == "focus",
            minutes = state.minutes,
            subjectId = state.subjectId,
        )
    }

    private fun setState(context: Context, state: FocusState) {
        _state.value = state
        val editor = prefs(context).edit()
            .putString(KEY_PHASE, state.phase)
            .putBoolean(KEY_RUNNING, state.running)
            .putLong(KEY_ENDS_AT, state.endsAt)
            .putInt(KEY_REMAINING, state.remainingSec)
            .putString(KEY_SUBJECT, state.subjectId)
            .putInt(KEY_MINUTES, state.minutes)
            .putInt(KEY_FOCUS_MINUTES, state.focusMinutes)
            .putInt(KEY_SHORT_MINUTES, state.shortMinutes)
        editor.apply()
    }

    private fun startService(context: Context) {
        val intent = Intent(context, FocusService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
