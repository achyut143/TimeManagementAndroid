package com.focusflow.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusflow.app.notify.ExactAlarms
import com.focusflow.app.notify.Notifications
import com.focusflow.app.ui.FocusFlowNavHost
import com.focusflow.app.ui.theme.FocusFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // iOS keeps the screen awake while the app is open (isIdleTimerDisabled).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FocusFlowTheme {
                // Ask for notifications first; the alarm prompt waits until that's answered.
                var notificationsAnswered by remember { mutableStateOf(false) }
                val askPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { notificationsAnswered = true }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this@MainActivity)) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationsAnswered = true
                    }
                }
                FocusFlowNavHost()
                if (notificationsAnswered) ExactAlarmPrompt()
            }
        }
    }
}

/**
 * Explains why on-time reminders need "Alarms & reminders" and opens that
 * setting. Shown once; "Not now" isn't asked again.
 */
@Composable
private fun ExactAlarmPrompt() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    // Re-check every time the app comes back, e.g. from the settings page.
    LifecycleResumeEffect(Unit) {
        show = ExactAlarms.shouldPrompt(context)
        onPauseOrDispose { }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Get reminders on time") },
            text = {
                Text(
                    "To notify you exactly when a task starts and ends, FocusFlow needs the " +
                        "\"Alarms & reminders\" permission. Without it, Android may deliver " +
                        "reminders a few minutes late.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    show = false
                    ExactAlarms.openSettings(context)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    show = false
                    ExactAlarms.markDeclined(context)
                }) { Text("Not now") }
            },
        )
    }
}
