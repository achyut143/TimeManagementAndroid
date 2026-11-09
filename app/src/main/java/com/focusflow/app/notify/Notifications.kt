package com.focusflow.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focusflow.app.MainActivity
import com.focusflow.app.R

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_FOCUS = "focus_timer"
    const val CHANNEL_ALARM = "task_alarm"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a task starts and ends"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Task start alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a task starts, until you tap Got it"
                setSound(null, null) // the alarm service plays the sound and speech itself
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The running focus timer"
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission") // checked by canPost()
    fun post(context: Context, id: Int, title: String, body: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
