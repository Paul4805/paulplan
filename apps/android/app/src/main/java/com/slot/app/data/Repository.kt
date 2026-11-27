package com.slot.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SlotRepository(
    private val db: FirebaseFirestore,
    private val uid: String,
) {
    private fun col(name: String) = db.collection("users").document(uid).collection(name)

    private fun <T> collectionFlow(
        name: String,
        mapper: (DocumentSnapshot) -> T,
    ): Flow<List<T>> = callbackFlow {
        val registration = col(name).addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("Slot", "Listener failed for $name", error)
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map(mapper) ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    fun subjects(): Flow<List<Subject>> = collectionFlow("subjects") { it.toSubject() }
    fun classes(): Flow<List<ClassSlot>> = collectionFlow("classes") { it.toClassSlot() }
    fun tasks(): Flow<List<TaskItem>> = collectionFlow("tasks") { it.toTask() }
    fun sessions(): Flow<List<StudySession>> = collectionFlow("studySessions") { it.toSession() }
    fun ptSessions(): Flow<List<PtSession>> = collectionFlow("ptSessions") { it.toPtSession() }
    fun alarms(): Flow<List<AlarmItem>> = collectionFlow("alarms") { it.toAlarm() }
    fun dailyHealth(): Flow<List<DailyHealth>> = collectionFlow("dailyHealth") { it.toDailyHealth() }

    fun profile(): Flow<Profile> = callbackFlow {
        val registration = col("meta").document("profile")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Slot", "Profile listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toProfile() ?: Profile.Default)
            }
        awaitClose { registration.remove() }
    }

    suspend fun alarmsOnce(): List<AlarmItem> = col("alarms").get().await().documents.map { it.toAlarm() }
    suspend fun classesOnce(): List<ClassSlot> = col("classes").get().await().documents.map { it.toClassSlot() }
    suspend fun subjectsOnce(): List<Subject> = col("subjects").get().await().documents.map { it.toSubject() }
    suspend fun profileOnce(): Profile =
        col("meta").document("profile").get().await().let { if (it.exists()) it.toProfile() else Profile.Default }

    suspend fun ensureProfile(user: FirebaseUser, timezone: String) {
        val userRef = db.collection("users").document(uid)
        userRef.set(
            mapOf(
                "displayName" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoURL" to user.photoUrl?.toString(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        val profileRef = col("meta").document("profile")
        if (!profileRef.get().await().exists()) {
            profileRef.set(
                mapOf(
                    "timezone" to timezone,
                    "schemaVersion" to 1L,
                    "theme" to "system",
                    "weekStartsOn" to 1L,
                    "notifications" to mapOf(
                        "enabled" to true,
                        "leadMinutes" to 10L,
                        "sound" to true,
                        "vibrate" to true,
                    ),
                    "pomodoro" to mapOf("focus" to 25L, "short" to 5L, "long" to 15L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    /* ------------------------------------------------------------ writes --- */

    suspend fun addSubject(name: String, code: String, color: String, teacher: String, room: String, target: Double?) {
        col("subjects").add(
            mapOf(
                "name" to name,
                "code" to code,
                "color" to color,
                "teacher" to teacher,
                "room" to room,
                "targetHoursPerWeek" to target,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun deleteSubject(id: String) {
        col("subjects").document(id).delete().await()
    }

    suspend fun archiveTerm() {
        for (document in col("subjects").get().await().documents) {
            runCatching {
                document.reference.update(
                    mapOf(
                        "archived" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }
        for (document in col("tasks").get().await().documents) {
            runCatching {
                document.reference.update(
                    mapOf(
                        "archived" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }
        for (document in col("classes").get().await().documents) {
            runCatching { document.reference.delete().await() }
        }
    }

    suspend fun restoreArchived() {
        for (document in col("subjects").get().await().documents) {
            if (document.getBoolean("archived") == true) {
                runCatching {
                    document.reference.update(
                        mapOf(
                            "archived" to false,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
        }
        for (document in col("tasks").get().await().documents) {
            if (document.getBoolean("archived") == true) {
                runCatching {
                    document.reference.update(
                        mapOf(
                            "archived" to false,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
        }
    }

    suspend fun addEntry(
        kind: String,
        subjectId: String?,
        title: String?,
        color: String?,
        dayOfWeek: Int,
        startMinutes: Int,
        endMinutes: Int,
        room: String,
        date: String? = null,
    ) {
        val payload = mutableMapOf<String, Any?>(
            "kind" to kind,
            "subjectId" to subjectId,
            "title" to title,
            "dayOfWeek" to dayOfWeek.toLong(),
            "startMinutes" to startMinutes.toLong(),
            "endMinutes" to endMinutes.toLong(),
            "room" to room,
            "date" to date,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!color.isNullOrBlank()) payload["colorOverride"] = color

        col("classes").add(payload).await()
    }

    suspend fun deleteClass(id: String) {
        col("classes").document(id).delete().await()
    }

    suspend fun addTask(
        title: String,
        notes: String,
        subjectId: String?,
        dueAt: Date?,
        priority: String,
        status: String,
        remind: Boolean = false,
        timezone: String = java.time.ZoneId.systemDefault().id,
        leadMinutes: Int = 10,
    ) {
        val taskRef = col("tasks").add(
            mapOf(
                "title" to title,
                "notes" to notes,
                "subjectId" to subjectId,
                "dueAt" to dueAt?.let { Timestamp(it) },
                "priority" to priority,
                "status" to status,
                "completedAt" to if (status == "done") FieldValue.serverTimestamp() else null,
                "order" to System.currentTimeMillis().toDouble(),
                "remind" to (remind && dueAt != null),
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        if (!remind || dueAt == null) return

        val alarmRef = col("alarms").add(
            mapOf(
                "title" to title,
                "kind" to "task",
                "recurrence" to "once",
                "onceAt" to Timestamp(dueAt),
                "leadMinutes" to leadMinutes.toLong(),
                "exact" to true,
                "enabled" to true,
                "timezone" to timezone,
                "targetId" to taskRef.id,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        taskRef.update(
            mapOf(
                "alarmId" to alarmRef.id,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun setTaskStatus(id: String, status: String) {
        val reference = col("tasks").document(id)
        val alarmId = runCatching { reference.get().await().getString("alarmId") }.getOrNull()
        reference.update(
            mapOf(
                "status" to status,
                "completedAt" to if (status == "done") FieldValue.serverTimestamp() else null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        if (!alarmId.isNullOrBlank()) {
            runCatching {
                col("alarms").document(alarmId)
                    .update(
                        mapOf(
                            "enabled" to (status != "done"),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .await()
            }
        }
    }

    suspend fun deleteTask(id: String) {
        val reference = col("tasks").document(id)
        val alarmId = runCatching { reference.get().await().getString("alarmId") }.getOrNull()
        if (!alarmId.isNullOrBlank()) {
            runCatching { col("alarms").document(alarmId).delete().await() }
        }
        reference.delete().await()
    }

    suspend fun addAlarm(
        title: String,
        recurrence: String,
        dayOfWeek: Int?,
        timeMinutes: Int,
        leadMinutes: Int,
        timezone: String,
    ) {
        val payload = mutableMapOf<String, Any?>(
            "title" to title,
            "kind" to "custom",
            "recurrence" to recurrence,
            "timeMinutes" to timeMinutes.toLong(),
            "leadMinutes" to leadMinutes.toLong(),
            "exact" to true,
            "enabled" to true,
            "timezone" to timezone,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (recurrence == "weekly" && dayOfWeek != null) payload["dayOfWeek"] = dayOfWeek.toLong()

        col("alarms").add(payload).await()
    }

    suspend fun setAlarmEnabled(id: String, enabled: Boolean) {
        col("alarms").document(id).update(
            mapOf("enabled" to enabled, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun deleteAlarm(id: String) {
        col("alarms").document(id).delete().await()
    }

    suspend fun logSession(subjectId: String?, minutes: Int, source: String) {        val ended = Date()
        val started = Date(ended.time - minutes * 60_000L)
        col("studySessions").add(
            mapOf(
                "subjectId" to subjectId,
                "taskId" to null,
                "startedAt" to Timestamp(started),
                "endedAt" to Timestamp(ended),
                "durationMinutes" to minutes.toLong(),
                "source" to source,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun addPtSession(
        type: String,
        title: String,
        startedAt: Date,
        minutes: Int,
        distanceKm: Double?,
        notes: String,
    ) {
        val payload = mutableMapOf<String, Any?>(
            "type" to type,
            "title" to title,
            "startedAt" to Timestamp(startedAt),
            "durationMinutes" to minutes.toLong(),
            "source" to "manual",
            "notes" to notes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (distanceKm != null) payload["distanceKm"] = distanceKm

        col("ptSessions").add(payload).await()
    }

    suspend fun deletePtSession(id: String) {
        col("ptSessions").document(id).delete().await()
    }

    suspend fun cleanupAlarmStates() {
        val cutoff = Timestamp(Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
        val stale = col("alarmState").whereLessThan("actedAt", cutoff).get().await()
        for (document in stale.documents) {
            runCatching { document.reference.delete().await() }
        }
    }

    suspend fun updateTheme(theme: String) {
        col("meta").document("profile").set(
            mapOf("theme" to theme, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun writeAlarmState(
        eventId: String,
        alarmId: String,
        state: String,
        snoozeUntil: Date?,
    ) {
        col("alarmState").document(eventId).set(
            mapOf(
                "alarmId" to alarmId,
                "state" to state,
                "snoozeUntil" to snoozeUntil?.let { Timestamp(it) },
                "actedAt" to FieldValue.serverTimestamp(),
                "actedBy" to "android",
            ),
        ).await()
    }
}

object AuthRepository {
    fun authOrNull(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    fun currentUser(): FirebaseUser? = authOrNull()?.currentUser

    suspend fun signIn(email: String, password: String) {
        val auth = authOrNull() ?: error("Firebase is not configured. Add the keys to local.properties.")
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(name: String, email: String, password: String) {
        val auth = authOrNull() ?: error("Firebase is not configured. Add the keys to local.properties.")
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        if (name.isNotBlank()) {
            val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            result.user?.updateProfile(request)?.await()
        }
    }

    suspend fun resetPassword(email: String) {
        val auth = authOrNull() ?: error("Firebase is not configured.")
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun signInWithGoogle(context: Context, webClientId: String) {
        if (webClientId.isBlank()) {
            error("Google sign-in needs google.webClientId in local.properties. Email sign-in works without it.")
        }

        val manager = androidx.credentials.CredentialManager.create(context)
        val option = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = manager.getCredential(context, request)
        val credential = response.credential

        if (credential !is androidx.credentials.CustomCredential ||
            credential.type != com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google returned an unexpected credential type.")
        }

        val idToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
            .createFrom(credential.data)
            .idToken

        val auth = authOrNull() ?: error("Firebase is not configured.")
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    fun signOut() {
        authOrNull()?.signOut()
    }

    suspend fun deleteAccount(password: String, db: FirebaseFirestore) {
        val auth = authOrNull() ?: error("Firebase is not configured.")
        val user = auth.currentUser ?: error("You are not signed in.")
        val email = user.email ?: error("This account has no email address.")
        val hasPassword = user.providerData.any { it.providerId == "password" }
        if (!hasPassword) error("Sign in with email and password to delete the account in-app.")

        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()

        val base = db.collection("users").document(user.uid)
        for (name in listOf(
            "subjects",
            "classes",
            "tasks",
            "studySessions",
            "ptSessions",
            "alarms",
            "alarmState",
            "dailyHealth",
        )) {
            for (document in base.collection(name).get().await().documents) {
                runCatching { document.reference.delete().await() }
            }
        }
        for (document in base.collection("meta").get().await().documents) {
            runCatching { document.reference.delete().await() }
        }
        runCatching { base.delete().await() }
        user.delete().await()
    }

    fun updateDisplayName(name: String): Boolean {
        val user = currentUser() ?: return false
        val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        user.updateProfile(request)
        return true
    }
}
