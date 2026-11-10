package com.example.anonymouspager.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.anonymouspager.R
import com.example.anonymouspager.data.PagerRepository
import com.example.anonymouspager.data.PagerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val receiptId = intent.getStringExtra(EXTRA_RECEIPT_ID).orEmpty()
        val response = intent.getStringExtra(EXTRA_RESPONSE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        if (receiptId.isBlank() || response.isBlank()) return

        // goAsync() keeps this receiver (and the app process) alive until the server call finishes.
        // Without it Android can kill the process right after onReceive returns, so the answer is lost.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Android allows ~10s for a goAsync receiver; stay under it.
                val result = withTimeoutOrNull(CALL_TIMEOUT_MS) {
                    PagerRepository(appContext).respondToRing(receiptId, response)
                }
                if (result !is PagerResult.Ok) showFailure(appContext, notificationId)
            } catch (e: Exception) {
                showFailure(appContext, notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showFailure(context: Context, notificationId: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId,
            NotificationHelper.openAppIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationHelper.RING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Answer not sent")
            .setContentText("Open Wen to answer the ring")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile; nothing else to do.
        }
    }

    companion object {
        private const val EXTRA_RECEIPT_ID = "receiptId"
        private const val EXTRA_RESPONSE = "response"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val CALL_TIMEOUT_MS = 9_000L

        fun pendingIntent(
            context: Context,
            receiptId: String,
            response: String,
            notificationId: Int
        ): PendingIntent {
            val requestCode = "$receiptId:$response".hashCode()
            val intent = Intent(context, RingActionReceiver::class.java).apply {
                putExtra(EXTRA_RECEIPT_ID, receiptId)
                putExtra(EXTRA_RESPONSE, response)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
