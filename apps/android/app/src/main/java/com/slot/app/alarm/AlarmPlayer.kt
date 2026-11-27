package com.slot.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A real alarm sound, not a notification chime: the default alarm tone loops
 * until Done or Snooze, with a repeating vibration pattern. Wrapped in
 * runCatching because a device can legitimately have no alarm tone.
 */
class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        stop()

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }

        runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null

        runCatching { vibrator?.cancel() }
        vibrator = null
    }
}
