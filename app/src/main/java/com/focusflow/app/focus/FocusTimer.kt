package com.focusflow.app.focus

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.R
import com.focusflow.app.notify.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FocusState(
    val label: String = "",
    val taskId: String? = null,
    /** Wall-clock end time while running; null while paused or stopped. */
    val endAt: Long? = null,
    /** Time left when paused. */
    val pausedRemainingMs: Long = 0,
    val totalMs: Long = 0,
) {
    val isRunning: Boolean get() = endAt != null
    val isPaused: Boolean get() = endAt == null && pausedRemainingMs > 0
    val isActive: Boolean get() = isRunning || isPaused

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        endAt?.let { (it - now).coerceAtLeast(0) } ?: pausedRemainingMs
}

/**
 * Android stand-in for the iOS Live Activity / Dynamic Island timer.
 *
 * Shows an ongoing notification whose countdown the system keeps ticking
 * (a notification "chronometer"), so no service has to stay running. An
 * alarm fires at the end to post "Time's up".
 */
class FocusTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    fun start(label: String, minutes: Int, taskId: String? = null) {
        val total = minutes.coerceAtLeast(1) * 60_000L
        update(FocusState(label, taskId, System.currentTimeMillis() + total, 0, total))
    }

    fun pause() {
        val s = _state.value
        if (!s.isRunning) return
        update(s.copy(endAt = null, pausedRemainingMs = s.remainingMs().coerceAtLeast(1)))
    }

    fun resume() {
        val s = _state.value
        if (!s.isPaused) return
        update(s.copy(endAt = System.currentTimeMillis() + s.pausedRemainingMs, pausedRemainingMs = 0))
    }

    fun stop() = update(FocusState())

    fun finish() {
        val label = _state.value.label
        stop()
        Notifications.post(context, FINISHED_ID, "Time's up", label.ifBlank { "Focus session finished" })
    }

    /** After a reboot: finish a timer that ran out, otherwise put its alarm and notification back. */
    fun restoreAfterBoot() {
        val s = _state.value
        if (s.isRunning && s.remainingMs() == 0L) finish() else update(s)
    }

    private fun update(new: FocusState) {
        _state.value = new
        prefs.edit()
            .putString("label", new.label)
            .putString("taskId", new.taskId)
            .putLong("endAt", new.endAt ?: 0)
            .putLong("pausedRemaining", new.pausedRemainingMs)
            .putLong("total", new.totalMs)
            .apply()
        alarms.cancel(endIntent())
        new.endAt?.let { setEndAlarm(it) }
        showNotification(new)
    }

    private fun load() = FocusState(
        label = prefs.getString("label", "").orEmpty(),
        taskId = prefs.getString("taskId", null),
        endAt = prefs.getLong("endAt", 0).takeIf { it > 0 },
        pausedRemainingMs = prefs.getLong("pausedRemaining", 0),
        totalMs = prefs.getLong("total", 0),
    )

    private fun setEndAlarm(at: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent())
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent())
        }
    }

    @SuppressLint("MissingPermission") // checked by Notifications.canPost()
    private fun showNotification(s: FocusState) {
        val nm = NotificationManagerCompat.from(context)
        if (!s.isActive) {
            nm.cancel(ONGOING_ID)
            return
        }
        if (!Notifications.canPost(context)) return

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.label.ifBlank { "Focus" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(Notifications.openAppIntent(context))

        if (s.isRunning) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endAt!!)
                .setShowWhen(true)
                .addAction(0, "Pause", actionIntent(ACTION_PAUSE))
        } else {
            builder.setContentText("Paused · ${formatRemaining(s.pausedRemainingMs)} left")
                .setShowWhen(false)
                .addAction(0, "Resume", actionIntent(ACTION_RESUME))
        }
        builder.addAction(0, "Stop", actionIntent(ACTION_STOP))
        if (s.totalMs > 0) {
            val done = (s.totalMs - s.remainingMs()).coerceIn(0, s.totalMs)
            builder.setProgress(1000, (done * 1000 / s.totalMs).toInt(), false)
        }
        nm.notify(ONGOING_ID, builder.build())
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, FocusActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun endIntent() = actionIntent(ACTION_FINISHED)

    companion object {
        const val ACTION_PAUSE = "com.focusflow.focus.PAUSE"
        const val ACTION_RESUME = "com.focusflow.focus.RESUME"
        const val ACTION_STOP = "com.focusflow.focus.STOP"
        const val ACTION_FINISHED = "com.focusflow.focus.FINISHED"
        private const val ONGOING_ID = 1001
        private const val FINISHED_ID = 1002

        fun formatRemaining(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}

class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = (context.applicationContext as FocusFlowApp).container.focusTimer
        when (intent.action) {
            FocusTimer.ACTION_PAUSE -> timer.pause()
            FocusTimer.ACTION_RESUME -> timer.resume()
            FocusTimer.ACTION_STOP -> timer.stop()
            FocusTimer.ACTION_FINISHED -> timer.finish()
        }
    }
}
