package com.example.anonymouspager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.anonymouspager.ui.PagerApp
import com.example.anonymouspager.ui.PagerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PagerViewModel by viewModels()

    /** False when the user denied (or turned off) notifications, so rings would arrive silently. */
    private var notificationsEnabled by mutableStateOf(true)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshNotificationState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            var dismissed by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                PagerApp(viewModel = viewModel)
                if (!notificationsEnabled && !dismissed) {
                    NotificationsOffBanner(
                        onEnable = ::enableNotifications,
                        onDismiss = { dismissed = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check when coming back from system settings.
        refreshNotificationState()
    }

    private fun refreshNotificationState() {
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun enableNotifications() {
        val canAskAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (canAskAgain) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Denied twice (or turned off in settings): Android won't show the dialog again, so open settings.
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }
}

@Composable
private fun NotificationsOffBanner(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2A2620),
        contentColor = Color(0xFFF2E9C9),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Notifications are off. You won't hear rings when Wen is closed.",
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Later", color = Color(0xFFC8B36A)) }
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC8B36A),
                    contentColor = Color(0xFF12110E)
                )
            ) { Text("Enable") }
        }
    }
}
