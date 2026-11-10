package com.example.anonymouspager.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationHelper {
    const val RING_CHANNEL_ID = "rings"
    const val SUPER_RING_CHANNEL_ID = "super_rings"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            RING_CHANNEL_ID,
            "Rings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Private pager alerts"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)

        // Super rings use the alarm sound and alarm audio usage so they are hard to miss when the app is closed.
        val superChannel = NotificationChannel(
            SUPER_RING_CHANNEL_ID,
            "Super rings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent pager alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 320, 200, 320, 200, 320)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(superChannel)
    }
}
