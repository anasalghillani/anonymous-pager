package com.example.anonymouspager.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val RING_CHANNEL_ID = "rings"

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
    }
}
