package com.slot.app.alarm

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slot.app.data.AlarmItem
import com.slot.app.data.ClassSlot
import com.slot.app.data.Profile
import com.slot.app.data.SlotRepository
import com.slot.app.data.Subject
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the local alarm schedule in step with Firestore while the app process
 * is alive. Alarms themselves are handed to AlarmManager, so they survive the
 * process being killed; the snapshot is what the boot receiver re-arms from.
 */
object AlarmSync {
    private var job: Job? = null

    fun start(context: Context) {
        val app = context.applicationContext
        val auth = com.slot.app.data.AuthRepository.authOrNull() ?: return

        auth.addAuthStateListener { state ->
            job?.cancel()

            val user = state.currentUser
            if (user == null) {
                AlarmScheduler.cancelAll(app)
                return@addAuthStateListener
            }

            job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching {
                    val repo = SlotRepository(FirebaseFirestore.getInstance(), user.uid)
                    repo.ensureProfile(user, ZoneId.systemDefault().id)

                    combine(
                        repo.alarms(),
                        repo.classes(),
                        repo.subjects(),
                        repo.profile(),
                    ) { alarms, classes, subjects, profile ->
                        Snapshot(alarms, classes, subjects, profile)
                    }.collect { snapshot ->
                        val events = AlarmMath.occurrences(
                            alarms = snapshot.alarms,
                            classes = snapshot.classes,
                            subjects = snapshot.subjects.associateBy { it.id },
                            classLeadMinutes = snapshot.profile.leadMinutes,
                        )
                        AlarmScheduler.armAll(app, events)
                    }
                }.onFailure {
                    Log.e("Slot", "Alarm sync failed", it)
                }
            }
        }
    }

    private data class Snapshot(
        val alarms: List<AlarmItem>,
        val classes: List<ClassSlot>,
        val subjects: List<Subject>,
        val profile: Profile,
    )
}

/**
 * WorkManager safety net. Runs a few times a day in case the app was never
 * opened inside the 21-day horizon, or the user changed timezone.
 */
class AlarmRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()

        return try {
            val repo = SlotRepository(FirebaseFirestore.getInstance(), user.uid)
            val alarms = repo.alarmsOnce()
            val classes = repo.classesOnce()
            val subjects = repo.subjectsOnce()
            val profile = repo.profileOnce()

            val events = AlarmMath.occurrences(
                alarms = alarms,
                classes = classes,
                subjects = subjects.associateBy { it.id },
                classLeadMinutes = profile.leadMinutes,
            )

            val existing = AlarmSnapshot.load(applicationContext)
            if (events.isEmpty() && existing.isNotEmpty()) {
                // Almost certainly an offline cache miss. Keep what is armed.
                Result.success()
            } else {
                AlarmScheduler.armAll(applicationContext, events)
                Result.success()
            }
        } catch (cause: Exception) {
            Log.e("Slot", "Alarm refresh failed", cause)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "slot-alarm-refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmRefreshWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
