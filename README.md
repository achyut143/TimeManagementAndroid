# FocusFlow for Android

Android version of the FocusFlow iOS app (`../TimeManagementSwiftUI`).
This first version covers the core: Tasks, Habits, Self-Efficacy points, Goals, Backlog,
reminders, a focus timer and a home-screen widget.

## Open and run

1. Install [Android Studio](https://developer.android.com/studio) (it bundles Java and the Android SDK).
2. **File → Open…** and pick this `Timemanagement` folder.
3. Let Gradle sync. Android Studio downloads Gradle 8.11.1 and every library on the first sync.
   If it asks to create the Gradle wrapper or upgrade the Android Gradle plugin, accept.
4. Pick an emulator or a plugged-in phone and press **Run**.

Run the unit tests with **right-click `app/src/test` → Run Tests**, or run `./gradlew test` from this folder.

## Tech stack

| iOS | Android (this project) |
|---|---|
| Swift | Kotlin 2.1 |
| SwiftUI | Jetpack Compose + Material 3 |
| SwiftData | Room (`data/`) |
| `@AppStorage` | DataStore (`SettingsStore`) |
| `@Query` / Observation | ViewModel + StateFlow |
| UserNotifications | AlarmManager + notifications (`notify/`) |
| Live Activity / Dynamic Island | Ongoing countdown notification (`focus/FocusTimer`) |
| WidgetKit | Jetpack Glance (`widget/TodayWidget`) |

Minimum Android 8.0 (API 26), target Android 15 (API 35).

## What's ported

The same rules as the iOS app, ported from Swift:

- **Tasks**: day view with an hourly timeline and an untimed list, plus prev/next day and a date picker.
  - Quick add uses the same syntax: `15:30 - 16:30 - task - 2 - r1`, with commas for several tasks.
  - Complete or not-complete a task, edit, snooze, send to backlog, delete, and manage subtasks.
  - Completing or missing a repeat task creates its next occurrence `repeatAgain` days later. The red repeat icon (`reassign`) means the next one is already handled.
  - A not-completed task rolls over to tomorrow, except daily repeat tasks.
  - "Mark all not completed" works for a whole day.
  - Repeat-task cards show the completion % and streak over the last 7/15/30/45/60 days, with the ≥85% / <85% filter.
- **Habits**: every repeat task grouped by title. Shows done, missed, %, current and best streak, and points. Filter by %, sort, filter by tag, and open a done/missed calendar per habit. Opens on the current month.
- **Self-Efficacy**: earned / total points per day in a 7-column grid with the same color scale. Change the range with arrows, presets or a range picker.
- **Goals**: create, edit and delete goals, link tasks from the task editor, and see progress per goal.
- **Backlog**: tasks with no date. Schedule one for today or tomorrow.
- **Reminders**: "Task Starting" and "Task Ending" notifications. They are rescheduled after a reboot.
- **Focus timer**: start it from a task. It shows a live countdown in the notification shade with Pause/Resume/Stop buttons, and a "Time's up" alert at the end.
- **Widget**: today's points and the next three tasks.
- The screen stays on while the app is open, as on iOS.

## Not ported yet

Projects, OKRs, Workouts (Tabata, AI workouts), Food, Vocabulary, Books & Quotes, Daily Notes,
Day Blocks, Impulse tracking, Event days, Discipline Muscle Score, AI features (OpenAI),
file attachments, PDF export, text-to-speech announcements, drag-to-move on the timeline,
the snooze "push following tasks" chain, and the Habits chart/table/time views.

## Layout

```
app/src/main/java/com/focusflow/app/
  FocusFlowApp.kt, MainActivity.kt   app setup and shared instances (AppContainer)
  data/      Room entities, DAOs, database, settings, TaskRepository (task rules)
  domain/    time math, points, habit stats, quick-add parser (plain Kotlin, unit tested)
  notify/    notification channels, task reminders, boot receiver
  focus/     focus timer + notification actions
  widget/    home-screen widget
  ui/        Compose screens: tasks, habits, points, goals, backlog
```

Data is stored only on the device. Nothing syncs with the iOS app.
