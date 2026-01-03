package dev.pranav.reef.accessibility

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.timer.PomodoroConfig
import dev.pranav.reef.timer.PomodoroPhase
import dev.pranav.reef.timer.TimerSessionState
import dev.pranav.reef.timer.TimerStateManager
import dev.pranav.reef.util.AndroidUtilities
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class FocusModeService: Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_TIMER_UPDATED = "dev.pranav.reef.TIMER_UPDATED"
        const val ACTION_PAUSE = "dev.pranav.reef.PAUSE_TIMER"
        const val ACTION_RESUME = "dev.pranav.reef.RESUME_TIMER"
        const val ACTION_RESTART = "dev.pranav.reef.RESTART_TIMER"
        const val EXTRA_TIME_LEFT = "extra_time_left"
        const val EXTRA_TIMER_STATE = "extra_timer_state"
    }

    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val systemNotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val androidUtilities by lazy { AndroidUtilities() }

    private var countDownTimer: CountDownTimer? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var previousInterruptionFilter: Int? = null
    private var initialDuration: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isPrefsInitialized) {
            prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        }

        when (intent?.action) {
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_RESTART -> restartCurrentPhase()
            else -> startTimer()
        }
        return START_STICKY
    }

    private fun startTimer() {
        val focusTimeMillis = prefs.getLong("focus_time", TimeUnit.MINUTES.toMillis(10))
        val isStrictMode = prefs.getBoolean("strict_mode", false)
        val isPomodoroMode = prefs.getBoolean("pomodoro_mode", false)

        initialDuration = focusTimeMillis

        // Initialize Pomodoro config if in Pomodoro mode
        if (isPomodoroMode) {
            val config = PomodoroConfig(
                focusDuration = prefs.getLong("pomodoro_focus_duration", 25 * 60 * 1000L),
                shortBreakDuration = prefs.getLong("pomodoro_short_break_duration", 5 * 60 * 1000L),
                longBreakDuration = prefs.getLong("pomodoro_long_break_duration", 15 * 60 * 1000L),
                cyclesBeforeLongBreak = prefs.getInt("pomodoro_cycles_before_long_break", 4)
            )
            TimerStateManager.setPomodoroConfig(config)

            val currentCycle = prefs.getInt("pomodoro_current_cycle", 1)
            TimerStateManager.updateState {
                copy(
                    isRunning = true,
                    isPaused = false,
                    timeRemaining = focusTimeMillis,
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    currentCycle = currentCycle,
                    totalCycles = config.cyclesBeforeLongBreak,
                    isPomodoroMode = true,
                    isStrictMode = isStrictMode
                )
            }
        } else {
            TimerStateManager.updateState {
                copy(
                    isRunning = true,
                    isPaused = false,
                    timeRemaining = focusTimeMillis,
                    isPomodoroMode = false,
                    isStrictMode = isStrictMode
                )
            }
        }

        prefs.edit { putBoolean("focus_mode", true) }

        enableDNDIfNeeded()

        val notification = createNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(focusTimeMillis)),
            showPauseButton = !isStrictMode
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        startCountdown(focusTimeMillis)
    }

    private fun getNotificationTitle(): String {
        val state = TimerStateManager.state.value
        if (!state.isPomodoroMode) {
            return getString(R.string.focus_mode)
        }

        return when (state.pomodoroPhase) {
            PomodoroPhase.SHORT_BREAK -> getString(R.string.short_break_label)
            PomodoroPhase.LONG_BREAK -> getString(R.string.long_break_label)
            else -> getString(R.string.focus_mode)
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startCountdown(timeMillis: Long) {
        countDownTimer?.cancel()

        countDownTimer = object: CountDownTimer(timeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val state = TimerStateManager.state.value
                if (!state.isPaused) {
                    TimerStateManager.updateState { copy(timeRemaining = millisUntilFinished) }
                    updateNotificationAndBroadcast(millisUntilFinished)
                }
            }

            override fun onFinish() = handleTimerComplete()
        }.start()
    }

    private fun pauseTimer() {
        val state = TimerStateManager.state.value
        if (state.isStrictMode) return

        countDownTimer?.cancel()

        TimerStateManager.updateState {
            copy(isRunning = false, isPaused = true)
        }

        prefs.edit { putBoolean("focus_mode", false) }

        updateNotification(
            title = getNotificationTitle(),
            text = formatTime(state.timeRemaining),
            showPauseButton = false
        )
        broadcastTimerUpdate(formatTime(state.timeRemaining))
    }

    private fun resumeTimer() {
        val state = TimerStateManager.state.value

        TimerStateManager.updateState {
            copy(isRunning = true, isPaused = false)
        }

        prefs.edit { putBoolean("focus_mode", true) }

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(state.timeRemaining)),
            showPauseButton = true
        )
        startCountdown(state.timeRemaining)
    }

    private fun restartCurrentPhase() {
        countDownTimer?.cancel()

        TimerStateManager.updateState {
            copy(
                timeRemaining = initialDuration,
                isPaused = false,
                isRunning = true
            )
        }

        prefs.edit { putBoolean("focus_mode", true) }

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(initialDuration)),
            showPauseButton = !TimerStateManager.state.value.isStrictMode
        )
        broadcastTimerUpdate(formatTime(initialDuration))
        startCountdown(initialDuration)
    }

    private fun updateNotificationAndBroadcast(millisUntilFinished: Long) {
        val state = TimerStateManager.state.value
        val formattedTime = formatTime(millisUntilFinished)

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formattedTime),
            showPauseButton = !state.isStrictMode && !state.isPaused
        )
        broadcastTimerUpdate(formattedTime)
    }

    private fun handleTimerComplete() {
        val state = TimerStateManager.state.value

        if (!state.isPomodoroMode) {
            endSession()
            return
        }

        transitionPomodoroPhase()
    }

    private fun endSession() {
        TimerStateManager.updateState {
            copy(isRunning = false, isPaused = false)
        }

        prefs.edit { putBoolean("focus_mode", false) }

        broadcastTimerUpdate("00:00")
        TimerStateManager.reset()
        stopSelf()
    }

    private fun transitionPomodoroPhase() {
        val state = TimerStateManager.state.value
        val config = TimerStateManager.getPomodoroConfig() ?: return endSession()

        val nextPhase = calculateNextPhase(state, config)

        if (nextPhase.isComplete) {
            endSession()
            return
        }

        // Update state with new phase
        TimerStateManager.updateState {
            copy(
                pomodoroPhase = nextPhase.phase,
                currentCycle = nextPhase.currentCycle,
                timeRemaining = nextPhase.duration
            )
        }

        // Store current cycle for persistence
        prefs.edit {
            putInt("pomodoro_current_cycle", nextPhase.currentCycle)
            putBoolean("focus_mode", nextPhase.phase == PomodoroPhase.FOCUS)
        }

        initialDuration = nextPhase.duration

        // Manage DND based on phase
        if (nextPhase.phase == PomodoroPhase.FOCUS) {
            enableDNDIfNeeded()
        } else {
            restoreDND()
        }

        if (prefs.getBoolean("enable_pomodoro_vibration", true)) {
            androidUtilities.vibrate(this, 1000)
        }

        if (prefs.getBoolean("enable_pomodoro_sound", true)) {
            playTransitionSound()
        }

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(nextPhase.duration)),
            showPauseButton = !state.isStrictMode
        )
        broadcastTimerUpdate(formatTime(nextPhase.duration))
        startCountdown(nextPhase.duration)
    }

    private data class NextPhaseResult(
        val phase: PomodoroPhase,
        val duration: Long,
        val currentCycle: Int,
        val isComplete: Boolean
    )

    private fun calculateNextPhase(
        state: TimerSessionState,
        config: PomodoroConfig
    ): NextPhaseResult {
        return when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS -> {
                if (state.currentCycle >= state.totalCycles) {
                    NextPhaseResult(
                        phase = PomodoroPhase.LONG_BREAK,
                        duration = config.longBreakDuration,
                        currentCycle = 0,
                        isComplete = false
                    )
                } else {
                    NextPhaseResult(
                        phase = PomodoroPhase.SHORT_BREAK,
                        duration = config.shortBreakDuration,
                        currentCycle = state.currentCycle + 1,
                        isComplete = false
                    )
                }
            }

            PomodoroPhase.LONG_BREAK -> {
                NextPhaseResult(
                    phase = PomodoroPhase.COMPLETE,
                    duration = 0,
                    currentCycle = 0,
                    isComplete = true
                )
            }

            else -> {
                NextPhaseResult(
                    phase = PomodoroPhase.FOCUS,
                    duration = config.focusDuration,
                    currentCycle = state.currentCycle,
                    isComplete = false
                )
            }
        }
    }

    private fun createNotification(
        title: String,
        text: String,
        showPauseButton: Boolean
    ): android.app.Notification {
        if (notificationBuilder == null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_TIME_LEFT, text)
                putExtra("navigate_to_timer", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        }

        return notificationBuilder!!.apply {
            setContentTitle(title)
            setContentText(text)
            clearActions()

            val action =
                if (showPauseButton) ACTION_PAUSE to getString(R.string.notification_pause) else ACTION_RESUME to getString(
                    R.string.notification_resume
                )
            val actionIntent = Intent(this@FocusModeService, FocusModeService::class.java).apply {
                this.action = action.first
            }
            val actionPendingIntent = PendingIntent.getService(
                this@FocusModeService,
                if (showPauseButton) 1 else 2,
                actionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            addAction(R.drawable.ic_launcher_foreground, action.second, actionPendingIntent)
        }.build()
    }

    private fun updateNotification(title: String, text: String, showPauseButton: Boolean) {
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(title, text, showPauseButton)
        )
    }

    private fun broadcastTimerUpdate(formattedTime: String) {
        val state = TimerStateManager.state.value
        val intent = Intent(ACTION_TIMER_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_TIME_LEFT, formattedTime)
            putExtra(EXTRA_TIMER_STATE, state.pomodoroPhase.name)
        }
        sendBroadcast(intent)
    }

    private fun playTransitionSound() {
        try {
            val soundUriString = prefs.getString("pomodoro_sound", null)
            val soundUri = if (soundUriString.isNullOrEmpty()) {
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            } else {
                soundUriString.toUri()
            }

            val ringtone = android.media.RingtoneManager.getRingtone(applicationContext, soundUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enableDNDIfNeeded() {
        if (!prefs.getBoolean("enable_dnd", false)) return

        if (systemNotificationManager.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter = systemNotificationManager.currentInterruptionFilter
            systemNotificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        }
    }

    private fun restoreDND() {
        if (previousInterruptionFilter != null) {
            if (systemNotificationManager.isNotificationPolicyAccessGranted) {
                systemNotificationManager.setInterruptionFilter(previousInterruptionFilter!!)
                previousInterruptionFilter = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
        restoreDND()

        prefs.edit { putBoolean("focus_mode", false) }

        TimerStateManager.reset()
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
