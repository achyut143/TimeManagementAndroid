package com.focusflow.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.endDateTime
import com.focusflow.app.domain.isDone
import com.focusflow.app.domain.startDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "Task Starting" and "Task Ending" notifications, like the iOS
 * `scheduleTaskNotifications`. Uses AlarmManager so they fire on time even
 * when the app is closed.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (task.isDone) return
        val now = LocalDateTime.now()
        task.startDateTime()?.takeIf { it.isAfter(now) }?.let {
            set(it, pendingIntent(task.id, KIND_START, task.title))
        }
        task.endDateTime()?.takeIf { it.isAfter(now) }?.let {
            set(it, pendingIntent(task.id, KIND_END, task.title))
        }
    }

    fun cancel(taskId: String) {
        alarms.cancel(pendingIntent(taskId, KIND_START, ""))
        alarms.cancel(pendingIntent(taskId, KIND_END, ""))
    }

    private fun set(at: LocalDateTime, intent: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // Exact alarms not allowed: Android may deliver this a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun pendingIntent(taskId: String, kind: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            // The data URI makes each task + kind its own alarm.
            .setData(Uri.parse("focusflow://reminder/$taskId/$kind"))
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val KIND_START = "start"
        const val KIND_END = "end"
        const val EXTRA_TITLE = "title"
        const val EXTRA_KIND = "kind"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val isStart = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) == ReminderScheduler.KIND_START
        Notifications.post(
            context,
            id = intent.data.hashCode(),
            title = if (isStart) "Task Starting" else "Task Ending",
            body = if (isStart) "Time to start: $title" else "Time to end: $title",
        )
    }
}

/**
 * Sets all reminders again when they may be missing or late:
 * - after a reboot (Android clears alarms), and
 * - when "Alarms & reminders" is turned on, so reminders set earlier as
 *   inexact alarms become exact.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handled = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!handled) return
        val container = (context.applicationContext as FocusFlowApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.db.taskDao().tasksFrom(LocalDate.now().minusDays(1))
                    .forEach(container.reminders::schedule)
                container.focusTimer.restoreAfterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
