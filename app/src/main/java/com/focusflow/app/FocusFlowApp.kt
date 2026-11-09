package com.focusflow.app

import android.app.Application
import android.content.Context
import com.focusflow.app.data.AppDatabase
import com.focusflow.app.data.SettingsStore
import com.focusflow.app.data.TaskRepository
import com.focusflow.app.focus.FocusTimer
import com.focusflow.app.notify.Notifications
import com.focusflow.app.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Holds the app's single instances. Small apps don't need Hilt for this. */
class AppContainer(context: Context) {
    val db = AppDatabase.build(context)
    val settings = SettingsStore(context)
    val reminders = ReminderScheduler(context)
    val focusTimer = FocusTimer(context)
    val repository = TaskRepository(context, db, reminders)
}

class FocusFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        container = AppContainer(this)
        // Make sure today's repeat tasks exist for reminders and the widget, even before the app is opened.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.repository.ensureRepeatsFor(LocalDate.now()) }
        }
    }
}
