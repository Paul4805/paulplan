package com.slot.app.data

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SlotViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = AuthRepository.authOrNull()
    private val database: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private val _user = MutableStateFlow(auth?.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    init {
        auth?.addAuthStateListener { _user.value = it.currentUser }
    }

    private fun <T> scoped(subscribe: (SlotRepository) -> Flow<List<T>>): Flow<List<T>> =
        _user.flatMapLatest { current ->
            val db = database
            if (current == null || db == null) flowOf(emptyList())
            else subscribe(SlotRepository(db, current.uid))
        }

    val subjects: StateFlow<List<Subject>> = scoped { it.subjects() }
        .map { list -> list.filterNot { it.archived }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val classes: StateFlow<List<ClassSlot>> = scoped { it.classes() }
        .map { list -> list.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<TaskItem>> = scoped { it.tasks() }
        .map { list ->
            list
                .filterNot { it.archived }
                .sortedWith(compareBy({ statusRank(it.status) }, { it.dueAt?.time ?: Long.MAX_VALUE }, { it.order }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<StudySession>> = scoped { it.sessions() }
        .map { list -> list.sortedByDescending { it.startedAt?.time ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alarms: StateFlow<List<AlarmItem>> = scoped { it.alarms() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ptSessions: StateFlow<List<PtSession>> = scoped { it.ptSessions() }
        .map { list -> list.sortedByDescending { it.startedAt?.time ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyHealth: StateFlow<List<DailyHealth>> = scoped { it.dailyHealth() }
        .map { list -> list.sortedByDescending { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<Profile> = _user.flatMapLatest { current ->
        val db = database
        if (current == null || db == null) flowOf(Profile.Default)
        else SlotRepository(db, current.uid).profile()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile.Default)

    /* ------------------------------------------------------------- writes --- */

    private fun write(block: suspend (SlotRepository) -> Unit) {
        val current = _user.value
        val db = database
        if (current == null || db == null) return

        viewModelScope.launch {
            runCatching { block(SlotRepository(db, current.uid)) }
                .onFailure { show(it.message ?: "Could not save that") }
        }
    }

    fun addTask(
        title: String,
        notes: String,
        subjectId: String?,
        dueAt: Date?,
        priority: String,
        status: String,
        remind: Boolean = false,
    ) = write {
        it.addTask(
            title,
            notes,
            subjectId,
            dueAt,
            priority,
            status,
            remind,
            profile.value.timezone ?: java.time.ZoneId.systemDefault().id,
            profile.value.leadMinutes,
        )
    }

    fun addEntry(
        kind: String,
        subjectId: String?,
        title: String?,
        color: String?,
        dayOfWeek: Int,
        startMinutes: Int,
        endMinutes: Int,
        room: String,
    ) = write { it.addEntry(kind, subjectId, title, color, dayOfWeek, startMinutes, endMinutes, room) }

    fun deleteClass(id: String) = write { it.deleteClass(id) }

    fun addSubject(name: String, code: String, color: String, teacher: String, room: String, target: Double?) =
        write { it.addSubject(name, code, color, teacher, room, target) }

    fun deleteSubject(id: String) = write { it.deleteSubject(id) }

    fun setTaskStatus(id: String, status: String) = write { it.setTaskStatus(id, status) }

    fun deleteTask(id: String) = write { it.deleteTask(id) }

    fun logSession(subjectId: String?, minutes: Int, source: String) =
        write { it.logSession(subjectId, minutes, source) }

    fun addPtSession(
        type: String,
        title: String,
        startedAt: Date,
        minutes: Int,
        distanceKm: Double?,
        notes: String,
    ) = write { it.addPtSession(type, title, startedAt, minutes, distanceKm, notes) }

    fun deletePtSession(id: String) = write { it.deletePtSession(id) }

    /* ------------------------------------------------------------- garmin --- */

    val garminConfigured = MutableStateFlow(
        GarminStore.apiKey(getApplication()) != null,
    )
    val garminLastSync = MutableStateFlow(GarminStore.lastSync(getApplication()))

    fun saveGarminKey(key: String) {
        if (key.isBlank()) return
        GarminStore.saveKey(getApplication(), key)
        garminConfigured.value = true
        syncGarmin(manual = true)
    }

    fun syncGarmin(manual: Boolean = false) {
        val current = _user.value ?: return
        val db = database ?: return
        if (GarminStore.apiKey(getApplication()) == null) return

        viewModelScope.launch {
            runCatching { GarminSync.sync(getApplication(), current.uid, db) }
                .onSuccess { imported ->
                    garminLastSync.value = GarminStore.lastSync(getApplication())
                    if (manual) {
                        show(if (imported > 0) "Imported $imported sessions" else "Already up to date")
                    }
                }
                .onFailure { if (manual) show(it.message ?: "Garmin sync failed") }
        }
    }

    fun disconnectGarmin() {
        GarminStore.clear(getApplication())
        garminConfigured.value = false
        garminLastSync.value = 0L
        show("Garmin disconnected")
    }

    fun addAlarm(title: String, recurrence: String, dayOfWeek: Int?, timeMinutes: Int, leadMinutes: Int) {
        val zone = profile.value.timezone ?: java.time.ZoneId.systemDefault().id
        write { it.addAlarm(title, recurrence, dayOfWeek, timeMinutes, leadMinutes, zone) }
    }

    fun setAlarmEnabled(id: String, enabled: Boolean) = write { it.setAlarmEnabled(id, enabled) }

    fun deleteAlarm(id: String) = write { it.deleteAlarm(id) }

    fun setTheme(theme: String) = write { it.updateTheme(theme) }

    fun archiveTerm() = write { it.archiveTerm() }

    fun restoreArchived() = write { it.restoreArchived() }

    /* --------------------------------------------------------------- auth --- */

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            runCatching { AuthRepository.signIn(email.trim(), password) }
                .onFailure { show(authMessage(it)) }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            runCatching { AuthRepository.register(name, email.trim(), password) }
                .onFailure { show(authMessage(it)) }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            runCatching { AuthRepository.signInWithGoogle(context, com.slot.app.BuildConfig.GOOGLE_WEB_CLIENT_ID) }
                .onFailure { show(authMessage(it)) }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            runCatching { AuthRepository.resetPassword(email.trim()) }
                .onSuccess { show("Reset link sent to $email") }
                .onFailure { show(authMessage(it)) }
        }
    }

    fun signOut() = AuthRepository.signOut()

    fun deleteAccount(password: String) {
        val db = database ?: return
        viewModelScope.launch {
            runCatching { AuthRepository.deleteAccount(password, db) }
                .onSuccess { show("Account deleted") }
                .onFailure { show(authMessage(it)) }
        }
    }

    fun renameUser(name: String) {
        if (AuthRepository.updateDisplayName(name.trim())) show("Name updated")
    }

    private fun show(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    private fun authMessage(cause: Throwable): String = when {
        cause.message?.contains("password", ignoreCase = true) == true -> "Check the email and password."
        cause.message?.contains("network", ignoreCase = true) == true -> "No connection. Try again."
        else -> cause.message ?: "Something went wrong."
    }
}

private fun statusRank(status: String): Int = when (status) {
    "doing" -> 0
    "todo" -> 1
    else -> 2
}
