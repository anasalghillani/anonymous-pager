package com.example.anonymouspager.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RingPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var ringJob: Job? = null

    fun play(superRing: Boolean) {
        ringJob?.cancel()
        ringJob = CoroutineScope(Dispatchers.Default).launch {
            val stream = if (superRing) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
            val toneType = if (superRing) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP
            val volume = if (superRing) 100 else 80
            val tone = runCatching { ToneGenerator(stream, volume) }.getOrNull() ?: return@launch
            val previousAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
            val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: previousAlarmVolume

            if (superRing && maxAlarmVolume > 0) {
                runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0) }
            }

            val repeats = if (superRing) 18 else 6
            repeat(repeats) {
                tone.startTone(toneType, if (superRing) 350 else 180)
                vibrate(superRing)
                delay(if (superRing) 520 else 700)
            }
            tone.release()

            if (superRing && previousAlarmVolume > 0) {
                runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0) }
            }
        }
    }

    fun stop() {
        ringJob?.cancel()
        ringJob = null
    }

    private fun vibrate(superRing: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    if (superRing) 320 else 120,
                    if (superRing) VibrationEffect.DEFAULT_AMPLITUDE else 90
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (superRing) 320 else 120)
        }
    }
}
