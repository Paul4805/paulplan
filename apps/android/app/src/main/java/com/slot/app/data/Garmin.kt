package com.slot.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Garmin's Personal API issues a 24h bearer token from a long-lived personal
 * API key. Garmin sends no CORS headers, so the browser cannot call it — the
 * phone is the sync agent. The key never leaves the device; sessions land in
 * Firestore and the web reads them like any other training entry.
 */
object GarminStore {
    private const val PREFS = "slot_garmin"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String? =
        prefs(context).getString("api_key", null)?.takeIf { it.isNotBlank() }

    fun saveKey(context: Context, key: String) {
        prefs(context).edit().putString("api_key", key.trim()).apply()
        clearToken(context)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun token(context: Context): String? {
        val token = prefs(context).getString("token", null) ?: return null
        val fetchedAt = prefs(context).getLong("token_at", 0L)
        // Tokens last 24h; refresh well before that.
        if (System.currentTimeMillis() - fetchedAt > 20 * 60 * 60 * 1000L) return null
        return token
    }

    fun saveToken(context: Context, token: String) {
        prefs(context).edit()
            .putString("token", token)
            .putLong("token_at", System.currentTimeMillis())
            .apply()
    }

    private fun clearToken(context: Context) {
        prefs(context).edit().remove("token").remove("token_at").apply()
    }

    fun lastSync(context: Context): Long = prefs(context).getLong("last_sync", 0L)

    fun markSynced(context: Context) {
        prefs(context).edit().putLong("last_sync", System.currentTimeMillis()).apply()
    }

    fun lastHandledRequest(context: Context): Long = prefs(context).getLong("handled", 0L)

    fun markHandled(context: Context, value: Long) {
        prefs(context).edit().putLong("handled", value).apply()
    }
}

private data class GarminActivity(
    val id: String,
    val title: String,
    val type: String,
    val startedAt: Date,
    val minutes: Int,
    val km: Double?,
)

object GarminSync {
    private const val HOST = "https://personal.connectapi.garmin.com"
    private const val TOKEN_PATH =
        "/personalapitoken-service/personal-api/personal-api-key/token"

    suspend fun sync(
        context: Context,
        uid: String,
        db: FirebaseFirestore,
    ): Int = withContext(Dispatchers.IO) {
        val key = GarminStore.apiKey(context) ?: return@withContext 0
        val token = GarminStore.token(context) ?: exchangeToken(key).also {
            GarminStore.saveToken(context, it)
        }

        val collection = db.collection("users").document(uid).collection("ptSessions")
        val existing = collection.get().await().documents
            .mapNotNull { it.getString("externalId") }
            .toSet()

        var imported = 0
        var start = 0
        val pageSize = 50

        while (start <= 450) {
            val page = fetchActivities(token, start, pageSize)
            if (page.length() == 0) break

            for (index in 0 until page.length()) {
                val activity = parse(page.optJSONObject(index) ?: continue) ?: continue
                if (activity.id in existing) continue

                collection.add(
                    mapOf(
                        "type" to activity.type,
                        "title" to activity.title,
                        "startedAt" to Timestamp(activity.startedAt),
                        "durationMinutes" to activity.minutes.toLong(),
                        "distanceKm" to activity.km,
                        "source" to "garmin-api",
                        "externalId" to activity.id,
                        "notes" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                imported += 1
            }

            if (page.length() < pageSize) break
            start += pageSize
        }

        // `requestedAt` is intentionally not written here: it is the web's
        // request marker, and rewriting it would retrigger the listener.
        db.collection("users").document(uid).collection("meta").document("garmin").set(
            mapOf(
                "enabled" to true,
                "lastSyncAt" to FieldValue.serverTimestamp(),
                "lastImported" to imported,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        GarminStore.markSynced(context)
        imported
    }

    private fun exchangeToken(key: String): String {
        val connection = (URL("$HOST$TOKEN_PATH").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "ApiKey $key")
            connectTimeout = 15_000
            readTimeout = 25_000
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("Garmin rejected the key (HTTP $code). Check that it is still active.")
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }.trim().trim('"')
        connection.disconnect()

        if (!body.startsWith("eyJ")) error("Garmin returned an unexpected token response.")
        return body
    }

    private fun fetchActivities(token: String, start: Int, limit: Int): JSONArray {
        val url = URL("$HOST/activitylist-service/activities/search/activities?start=$start&limit=$limit")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("Garmin activities request failed (HTTP $code).")
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONArray(body)
    }

    private fun parse(json: JSONObject?): GarminActivity? {
        if (json == null) return null

        val id = json.optLong("activityId", 0L).takeIf { it > 0 }?.toString() ?: return null
        val started = parseStart(json.optString("startTimeGMT")) ?: return null
        val minutes = (json.optDouble("duration", 0.0) / 60.0).roundToInt()
        if (minutes <= 0) return null

        val meters = json.optDouble("distance", 0.0)
        val typeKey = json.optJSONObject("activityType")?.optString("typeKey").orEmpty()
        val title = json.optString("activityName").ifBlank {
            typeKey.ifBlank { "Garmin activity" }
        }

        return GarminActivity(
            id = id,
            title = title,
            type = mapType(typeKey),
            startedAt = started,
            minutes = minutes.coerceAtMost(1440),
            km = if (meters > 0) Math.round(meters / 10.0) / 100.0 else null,
        )
    }

    private fun parseStart(value: String): Date? = runCatching {
        val local = LocalDateTime.parse(value.replace(' ', 'T'))
        Date.from(local.toInstant(ZoneOffset.UTC))
    }.getOrNull()

    private fun mapType(key: String): String {
        val value = key.lowercase()
        return when {
            value.contains("run") -> "run"
            value.contains("soccer") || value.contains("football") -> "football"
            value.contains("strength") || value.contains("gym") || value.contains("weight") -> "gym"
            else -> "other"
        }
    }
}

/** Reacts to a "Sync now" request written by the web app. */
object GarminSyncWatcher {
    private var scope: CoroutineScope? = null

    fun start(context: Context) {
        val app = context.applicationContext
        val auth = AuthRepository.authOrNull() ?: return

        auth.addAuthStateListener { state ->
            scope?.cancel()
            val user = state.currentUser ?: return@addAuthStateListener
            val db = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
                ?: return@addAuthStateListener

            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = newScope

            db.collection("users").document(user.uid).collection("meta").document("garmin")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val requested = snapshot.getTimestamp("requestedAt")?.toDate()?.time ?: 0L
                    if (requested <= 0 || requested <= GarminStore.lastHandledRequest(app)) {
                        return@addSnapshotListener
                    }
                    if (GarminStore.apiKey(app) == null) return@addSnapshotListener

                    GarminStore.markHandled(app, requested)
                    newScope.launch {
                        runCatching { GarminSync.sync(app, user.uid, db) }
                    }
                }

            // Opportunistic sync when the app opens, at most every 6 hours.
            if (GarminStore.apiKey(app) != null &&
                System.currentTimeMillis() - GarminStore.lastSync(app) > 6 * 60 * 60 * 1000L
            ) {
                newScope.launch {
                    runCatching { GarminSync.sync(app, user.uid, db) }
                }
            }
        }
    }
}

class GarminSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (GarminStore.apiKey(applicationContext) == null) return Result.success()
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()

        return try {
            GarminSync.sync(applicationContext, user.uid, FirebaseFirestore.getInstance())
            Result.success()
        } catch (cause: Exception) {
            android.util.Log.e("Slot", "Garmin sync failed", cause)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "slot-garmin-sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<GarminSyncWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
