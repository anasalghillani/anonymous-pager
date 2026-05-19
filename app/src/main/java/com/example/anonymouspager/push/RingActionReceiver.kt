package com.example.anonymouspager.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.anonymouspager.data.PagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val receiptId = intent.getStringExtra(EXTRA_RECEIPT_ID).orEmpty()
        val response = intent.getStringExtra(EXTRA_RESPONSE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        if (receiptId.isBlank() || response.isBlank()) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            PagerRepository(context).respondToRing(receiptId, response)
        }
    }

    companion object {
        private const val EXTRA_RECEIPT_ID = "receiptId"
        private const val EXTRA_RESPONSE = "response"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"

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
