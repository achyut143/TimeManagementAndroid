package com.focusflow.app.notify

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.ui.theme.FocusFlowTheme

/** Full-screen alarm page, shown over the lock screen while a task start is ringing. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FocusFlowTheme {
                val ringing by TaskAlarmService.ringing.collectAsStateWithLifecycle()
                // Close once the alarm stops (Got it here, in the notification or in the app).
                LaunchedEffect(ringing.isEmpty()) { if (ringing.isEmpty()) finish() }
                AlarmScreen(
                    titles = ringing.map { it.title },
                    onAcknowledge = {
                        TaskAlarmService.stop(this)
                        // "Got it" also unlocks into the app if the phone has no secure lock.
                        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
                        finish()
                    },
                    onSnooze = { TaskAlarmService.snooze(this); finish() },
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(titles: List<String>, onAcknowledge: () -> Unit, onSnooze: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Alarm, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Text("Time to start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            titles.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Got it", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Snooze ${TaskAlarmService.SNOOZE_MINUTES} min")
            }
        }
    }
}
