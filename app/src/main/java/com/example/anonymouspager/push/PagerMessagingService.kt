package com.example.anonymouspager.push

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.anonymouspager.R
import com.example.anonymouspager.data.PagerRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PagerMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            PagerRepository(applicationContext).registerPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        if (type == "ring_response") {
            showResponse(message)
            return
        }
        if (type != "ring") return
        // App is open: the in-app ring already plays the sound and shows Accept/Ignore.
        if (AppVisibility.isForeground) return

        val receiptId = message.data["receiptId"].orEmpty()
        val superRing = message.data["superRing"] == "true"
        val notificationId = NotificationHelper.ringNotificationId(receiptId)
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            NotificationHelper.openAppIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val acceptIntent = RingActionReceiver.pendingIntent(this, receiptId, "accepted", notificationId)
        val ignoreIntent = RingActionReceiver.pendingIntent(this, receiptId, "ignored", notificationId)

        val channel = if (superRing) NotificationHelper.SUPER_RING_CHANNEL_ID else NotificationHelper.RING_CHANNEL_ID
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (superRing) R.string.notif_super_title else R.string.notif_ring_title))
            .setContentText(getString(R.string.notif_ring_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.accept), acceptIntent)
            .addAction(0, getString(R.string.ignore), ignoreIntent)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun showResponse(message: RemoteMessage) {
        val receiptId = message.data["receiptId"].orEmpty()
        val response = message.data["response"].orEmpty()
        val title = getString(if (response == "accepted") R.string.ring_accepted else R.string.ring_ignored)
        val notificationId = "response:$receiptId".hashCode()
        val openIntent = PendingIntent.getActivity(
            this,
            notificationId,
            NotificationHelper.openAppIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NotificationHelper.RING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_response_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }
}
