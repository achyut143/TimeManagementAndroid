package com.focusflow.app.notify

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** The "Alarms & reminders" permission that lets reminders fire at the exact minute (Android 12+). */
object ExactAlarms {
    private const val PREFS = "exact_alarms"
    private const val KEY_DECLINED = "declined"

    fun isAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** True when the permission is off and the user hasn't said "Not now" before. */
    fun shouldPrompt(context: Context): Boolean =
        !isAllowed(context) &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DECLINED, false)

    fun markDeclined(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DECLINED, true).apply()
    }

    /** Opens this app's "Alarms & reminders" page, or the app's settings if that page isn't available. */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val packageUri = Uri.parse("package:${context.packageName}")
        try {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }
}
