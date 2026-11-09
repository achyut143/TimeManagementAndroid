package com.focusflow.app.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focusflow.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** What happens when a task starts. Kept in SharedPreferences so the alarm receiver can read it instantly. */
enum class AlarmMode(val label: String, val sound: Boolean, val speech: Boolean) {
    Both("Sound + speech", true, true),
    Sound("Sound only", true, false),
    Speech("Speech only", false, true),
    Off("Notification only", false, false),
    ;

    companion object {
        private const val PREFS = "task_alarm"
        private const val KEY = "mode"

        fun get(context: Context): AlarmMode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: Both

        fun set(context: Context, mode: AlarmMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}

/**
 * Rings when a task starts, until the user taps "Got it" (or "Snooze").
 * Runs as a foreground service so it keeps going with the app closed and the
 * screen off: alarm sound and spoken task name take turns, and the phone vibrates.
 * On a locked phone a full-screen [AlarmActivity] shows the buttons.
 */
class TaskAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var mode = AlarmMode.Both
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
                _ringing.value = _ringing.value.filterNot { it.taskId == taskId } + Ringing(taskId, title)
                goForeground()
                if (!running) startRinging()
            }
            ACTION_SNOOZE -> {
                _ringing.value.forEach { snooze(this, it) }
                stopAll()
            }
            else -> stopAll() // ACTION_STOP, or a restart with no intent
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Task starting")
        .setContentText(_ringing.value.joinToString(", ") { it.title })
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setFullScreenIntent(alarmActivityIntent(this), true)
        .setContentIntent(alarmActivityIntent(this))
        .setDeleteIntent(serviceIntent(this, ACTION_STOP, 1))
        .addAction(0, "Got it", serviceIntent(this, ACTION_STOP, 1))
        .addAction(0, "Snooze $SNOOZE_MINUTES min", serviceIntent(this, ACTION_SNOOZE, 2))
        .build()

    // region Ringing

    private fun startRinging() {
        running = true
        mode = AlarmMode.get(this).takeIf { it != AlarmMode.Off } ?: AlarmMode.Both
        acquireWakeLock()
        requestAudioFocus()
        vibrate()
        if (mode.speech) initTts()
        // Safety net so a forgotten alarm doesn't drain the battery all day.
        handler.postDelayed({ timeOut() }, MAX_RING_MS)
        nextCycle()
    }

    /** One round: alarm sound for a few seconds, then the spoken task name, then again. */
    private fun nextCycle() {
        if (!running) return
        if (mode.sound) {
            playSound() // loops by itself; with speech on, it pauses after a few seconds for the voice
            if (mode.speech) {
                handler.postDelayed({
                    pauseSound()
                    speak()
                }, SOUND_MS)
            }
        } else {
            speak()
        }
    }

    private fun speak() {
        if (!running) return
        val engine = tts
        if (engine == null || !ttsReady) {
            // Speech not ready (or unavailable): try again shortly, playing the sound meanwhile if allowed.
            handler.postDelayed({ if (mode.sound) nextCycle() else speak() }, 1_000)
            return
        }
        val text = _ringing.value.joinToString(". ") { phraseFor(engine, it.title) }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Speaks Telugu titles with a Telugu voice when the phone has one. */
    private fun phraseFor(engine: TextToSpeech, title: String): String {
        val telugu = title.any { it in 'ఀ'..'౿' }
        val locale = if (telugu) Locale("te", "IN") else Locale.getDefault()
        val ok = engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        engine.language = if (ok) locale else Locale.getDefault()
        return if (telugu && ok) "పని ప్రారంభించే సమయం. $title" else "Time to start: $title"
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) Log.w(TAG, "Text-to-speech unavailable; using sound only")
            if (!ttsReady && !mode.sound) mode = AlarmMode.Sound
        }.apply {
            setAudioAttributes(alarmAttributes)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.postDelayed({ nextCycle() }, PAUSE_AFTER_SPEECH_MS)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.postDelayed({ nextCycle() }, PAUSE_AFTER_SPEECH_MS)
                }
            })
        }
    }

    private fun playSound() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAttributes)
                setDataSource(this@TaskAlarmService, alarmUri())
                isLooping = true
                prepare()
            }
        }.onFailure { Log.w(TAG, "Couldn't play alarm sound", it) }.getOrNull()?.also { player = it }
        p?.start()
    }

    private fun pauseSound() {
        player?.takeIf { it.isPlaying }?.apply { pause(); seekTo(0) }
    }

    private fun alarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI

    private fun vibrate() {
        val pattern = longArrayOf(0, 800, 600)
        vibrator()?.vibrate(
            VibrationEffect.createWaveform(pattern, 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    @SuppressLint("WakelockTimeout") // released in stopAll(), and the ring times out
    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlow:taskAlarm")
            .apply { acquire(MAX_RING_MS + 60_000) }
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttributes)
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request
    }

    private fun timeOut() {
        val titles = _ringing.value.joinToString(", ") { it.title }
        stopAll()
        Notifications.post(this, NOTIFICATION_ID + 1, "Missed task start", "Not acknowledged: $titles")
    }

    private fun stopAll() {
        running = false
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        vibrator()?.cancel()
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _ringing.value = emptyList()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopAll()
        super.onDestroy()
    }

    // endregion

    data class Ringing(val taskId: String, val title: String)

    companion object {
        private const val TAG = "TaskAlarm"
        const val ACTION_RING = "com.focusflow.app.alarm.RING"
        const val ACTION_STOP = "com.focusflow.app.alarm.STOP"
        const val ACTION_SNOOZE = "com.focusflow.app.alarm.SNOOZE"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TASK_ID = "taskId"
        private const val NOTIFICATION_ID = 4242
        private const val UTTERANCE_ID = "task-alarm"
        const val SNOOZE_MINUTES = 5
        private const val SOUND_MS = 5_000L
        private const val PAUSE_AFTER_SPEECH_MS = 1_200L
        private const val MAX_RING_MS = 30 * 60 * 1000L

        private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val _ringing = MutableStateFlow<List<Ringing>>(emptyList())

        /** Tasks ringing right now; empty when quiet. The app and [AlarmActivity] show "Got it" while non-empty. */
        val ringing: StateFlow<List<Ringing>> = _ringing.asStateFlow()

        /** Starts ringing for a task. Returns false if Android didn't allow it (the caller then posts a plain notification). */
        fun ring(context: Context, taskId: String, title: String): Boolean = try {
            val intent = Intent(context, TaskAlarmService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TITLE, title)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException when the alarm wasn't exact.
            Log.w(TAG, "Couldn't start the alarm", e)
            false
        }

        fun stop(context: Context) {
            if (_ringing.value.isNotEmpty()) context.startService(Intent(context, TaskAlarmService::class.java).setAction(ACTION_STOP))
        }

        fun snooze(context: Context) {
            if (_ringing.value.isNotEmpty()) context.startService(Intent(context, TaskAlarmService::class.java).setAction(ACTION_SNOOZE))
        }

        /** Rings again in [SNOOZE_MINUTES] minutes. The task itself isn't moved. */
        private fun snooze(context: Context, r: Ringing) {
            val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            val intent = Intent(context, ReminderReceiver::class.java)
                .setData(Uri.parse("focusflow://snooze/${r.taskId}"))
                .putExtra(ReminderScheduler.EXTRA_TITLE, r.title)
                .putExtra(ReminderScheduler.EXTRA_KIND, ReminderScheduler.KIND_START)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, r.taskId)
            val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarms = context.getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, TaskAlarmService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun alarmActivityIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 3,
                Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Android 14+: whether the full-screen alarm page may show on the lock screen. */
        fun canShowFullScreen(context: Context): Boolean =
            Build.VERSION.SDK_INT < 34 ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        fun openFullScreenSettings(context: Context) {
            if (Build.VERSION.SDK_INT < 34) return
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
