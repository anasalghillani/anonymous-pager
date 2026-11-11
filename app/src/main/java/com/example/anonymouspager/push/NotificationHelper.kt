package com.example.anonymouspager.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.anonymouspager.MainActivity
import com.example.anonymouspager.R

object NotificationHelper {
    const val RING_CHANNEL_ID = "rings"
    const val SUPER_RING_CHANNEL_ID = "super_rings"

    /** Notification id used for a ring, so the app can clear it once the ring is answered in-app. */
    fun ringNotificationId(receiptId: String): Int = receiptId.hashCode()

    fun cancelRing(context: Context, receiptId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ringNotificationId(receiptId))
    }

    /** Opens the existing Wen screen instead of stacking a second copy on top of it. */
    fun openAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            RING_CHANNEL_ID,
            context.getString(R.string.channel_rings),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_rings_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)

        // Super rings use the alarm sound and alarm audio usage so they are hard to miss when the app is closed.
        val superChannel = NotificationChannel(
            SUPER_RING_CHANNEL_ID,
            context.getString(R.string.channel_super),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_super_desc)
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
