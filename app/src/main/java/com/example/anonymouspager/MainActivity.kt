package com.example.anonymouspager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.anonymouspager.ui.PagerApp
import com.example.anonymouspager.ui.PagerViewModel
import com.example.anonymouspager.ui.theme.Space
import com.example.anonymouspager.ui.theme.WenColors

class MainActivity : ComponentActivity() {
    private val viewModel: PagerViewModel by viewModels()

    /** False when the user denied (or turned off) notifications, so rings would arrive silently. */
    private var notificationsEnabled by mutableStateOf(true)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshNotificationState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            var dismissed by remember { mutableStateOf(false) }
            PagerApp(
                viewModel = viewModel,
                bottomBanner = {
                    if (!notificationsEnabled && !dismissed) {
                        NotificationsOffBanner(onEnable = ::enableNotifications, onDismiss = { dismissed = true })
                    }
                }
            )
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
            .padding(start = Space.screen, end = Space.screen, bottom = Space.lg),
        shape = RoundedCornerShape(16.dp),
        color = WenColors.EmberSoft,
        contentColor = WenColors.Text,
        border = BorderStroke(1.dp, WenColors.Ember.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = WenColors.Ember, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.md))
                Text(
                    stringResource(R.string.notifications_off),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.later), color = WenColors.TextMuted)
                }
                TextButton(onClick = onEnable) {
                    Text(stringResource(R.string.enable), color = WenColors.Ember, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
