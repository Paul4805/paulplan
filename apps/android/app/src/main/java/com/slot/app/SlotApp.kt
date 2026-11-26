package com.slot.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.slot.app.alarm.AlarmSync
import com.slot.app.alarm.AlarmRefreshWorker
import com.slot.app.alarm.NotificationChannels

class SlotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
        NotificationChannels.create(this)
        AlarmSync.start(this)
        AlarmRefreshWorker.enqueue(this)
    }

    private fun initializeFirebase() {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) {
            // The app still opens so the configuration screen can explain what is
            // missing; auth and data simply will not work.
            android.util.Log.e("Slot", "Firebase config missing in apps/android/local.properties")
            return
        }

        if (FirebaseApp.getApps(this).isNotEmpty()) return

        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()

        FirebaseApp.initializeApp(this, options)
    }
}
