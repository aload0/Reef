package dev.pranav.reef.accessibility

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
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
import dev.pranav.reef.util.*
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class FocusModeService: Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val BREAK_ALERT_NOTIFICATION_ID = 2
        private const val COMPLETE_NOTIFICATION_ID = 3
        const val ACTION_TIMER_UPDATED = "dev.pranav.reef.TIMER_UPDATED"
        const val ACTION_START = "dev.pranav.reef.START_TIMER"
        const val ACTION_PAUSE = "dev.pranav.reef.PAUSE_TIMER"
        const val ACTION_RESUME = "dev.pranav.reef.RESUME_TIMER"
        const val ACTION_RESTART = "dev.pranav.reef.RESTART_TIMER"
        const val EXTRA_TIME_LEFT = "extra_time_left"
        const val EXTRA_TIMER_STATE = "extra_timer_state"
    }

    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val systemNotificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private var countDownTimer: CountDownTimer? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var previousInterruptionFilter: Int? = null
    private var initialDuration: Long = 0

    override fun onCreate() {
        super.onCreate()

        if (!isPrefsInitialized) {
            createDeviceProtectedStorageContext().also { safeContext ->
                prefs = safeContext.getSharedPreferences("prefs", MODE_PRIVATE)
            }
        }
    }

    private fun promoteToForeground() {
        val state = TimerStateManager.state.value
        val timeToDisplay = if (state.timeRemaining > 0) state.timeRemaining else
            prefs.getLong("focus_time", TimeUnit.MINUTES.toMillis(10))

        val notification = createNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(timeToDisplay)),
            showPauseButton = !state.isStrictMode && state.isRunning,
            timeLeft = timeToDisplay
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) {
            return START_NOT_STICKY
        }

        if (intent.action != ACTION_PAUSE) {
            promoteToForeground()
        }

        when (intent.action) {
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_RESTART -> restartCurrentPhase()
            ACTION_START -> startTimer()
        }

        return START_STICKY
    }

    private fun startTimer() {
        val focusTimeMillis = prefs.getLong("focus_time", TimeUnit.MINUTES.toMillis(10))
        val isStrictMode = prefs.getBoolean("strict_mode", false)
        val isPomodoroMode = prefs.getBoolean("pomodoro_mode", false)

        initialDuration = focusTimeMillis

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

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(focusTimeMillis)),
            showPauseButton = !isStrictMode,
            timeLeft = focusTimeMillis
        )
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
            showPauseButton = false,
            timeLeft = state.timeRemaining
        )
        broadcastTimerUpdate(formatTime(state.timeRemaining))
    }

    private fun resumeTimer() {
        val state = TimerStateManager.state.value

        TimerStateManager.updateState {
            copy(isRunning = true, isPaused = false)
        }

        val isFocusPhase = state.isPomodoroMode && state.pomodoroPhase == PomodoroPhase.FOCUS
        prefs.edit { putBoolean("focus_mode", isFocusPhase || !state.isPomodoroMode) }

        if (isFocusPhase || !state.isPomodoroMode) {
            enableDNDIfNeeded()
        }

        updateNotification(
            title = getNotificationTitle(),
            text = getString(R.string.time_remaining, formatTime(state.timeRemaining)),
            showPauseButton = !state.isStrictMode,
            timeLeft = state.timeRemaining
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
            showPauseButton = !TimerStateManager.state.value.isStrictMode,
            timeLeft = initialDuration
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
            showPauseButton = !state.isStrictMode && !state.isPaused,
            timeLeft = millisUntilFinished
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
        restoreDND()
        showFocusCompleteNotification()
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

        val shouldAutoStart = when (nextPhase.phase) {
            PomodoroPhase.FOCUS -> prefs.getBoolean("auto_start_pomodoros", false)
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> prefs.getBoolean(
                "auto_start_breaks",
                true
            )

            else -> false
        }

        TimerStateManager.updateState {
            copy(
                pomodoroPhase = nextPhase.phase,
                currentCycle = nextPhase.currentCycle,
                timeRemaining = nextPhase.duration,
                isRunning = shouldAutoStart,
                isPaused = !shouldAutoStart
            )
        }

        // Store current cycle for persistence
        prefs.edit {
            putInt("pomodoro_current_cycle", nextPhase.currentCycle)
            putBoolean("focus_mode", shouldAutoStart && nextPhase.phase == PomodoroPhase.FOCUS)
        }

        initialDuration = nextPhase.duration

        if (nextPhase.phase == PomodoroPhase.FOCUS) {
            if (shouldAutoStart) {
                enableDNDIfNeeded()
            }
            if (prefs.getBoolean("break_alerts", true)) {
                showBreakEndedNotification()
            }
        } else {
            if (!shouldAutoStart) {
                restoreDND()
            }
        }

        if (prefs.getBoolean("pomodoro_sound_enabled", true)) {
            if (prefs.getBoolean("pomodoro_vibration_enabled", true)) {
                AndroidUtilities.vibrate(this, 1000)
            }
            playTransitionSound()
        }

        val notificationText = if (shouldAutoStart) {
            getString(R.string.time_remaining, formatTime(nextPhase.duration))
        } else {
            getString(R.string.tap_to_start_next_phase)
        }

        updateNotification(
            title = getNotificationTitle(),
            text = notificationText,
            showPauseButton = shouldAutoStart && !state.isStrictMode,
            timeLeft = nextPhase.duration
        )
        broadcastTimerUpdate(formatTime(nextPhase.duration))

        if (shouldAutoStart) {
            startCountdown(nextPhase.duration)
        }
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
        showPauseButton: Boolean,
        timeLeft: Long = 0
    ): Notification {

        val progressPercent = if (initialDuration > 0 && timeLeft > 0) {
            ((initialDuration - timeLeft).toFloat() / initialDuration.toFloat() * 100f).toInt()
                .coerceIn(0, 100)
        } else 0

        val isStrictMode = TimerStateManager.state.value.isStrictMode

        if (notificationBuilder == null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_TIME_LEFT, text)
                putExtra("navigate_to_timer", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            notificationBuilder = NotificationCompat.Builder(this, FOCUS_MODE_CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setRequestPromotedOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val chipText = "${TimeUnit.MILLISECONDS.toMinutes(timeLeft)}m"

        return notificationBuilder!!.apply {
            setContentTitle(title)
            setContentText(text)
            setProgress(100, progressPercent, false)
            setShortCriticalText(chipText)

            clearActions()

            if (!isStrictMode) {
                val action = if (showPauseButton) {
                    ACTION_PAUSE to getString(R.string.notification_pause)
                } else {
                    ACTION_RESUME to getString(R.string.notification_resume)
                }

                val actionIntent =
                    Intent(this@FocusModeService, FocusModeService::class.java).apply {
                        this.action = action.first
                    }

                val actionPendingIntent = PendingIntent.getService(
                    this@FocusModeService,
                    if (showPauseButton) 1 else 2,
                    actionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                addAction(
                    NotificationCompat.Action.Builder(0, action.second, actionPendingIntent).build()
                )
            }
        }.build()
    }

    private fun updateNotification(
        title: String,
        text: String,
        showPauseButton: Boolean,
        timeLeft: Long = 0
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(title, text, showPauseButton, timeLeft)
        )
    }

    private fun broadcastTimerUpdate(formattedTime: String) {
        val state = TimerStateManager.state.value
        val intent = Intent(ACTION_TIMER_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_TIME_LEFT, formattedTime)
            putExtra(EXTRA_TIMER_STATE, state.pomodoroPhase.name)
            addFlags(FLAG_RECEIVER_FOREGROUND)
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
                systemNotificationManager.setInterruptionFilter(
                    previousInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                )
                previousInterruptionFilter = null
            }
        }
    }

    private fun showBreakEndedNotification() {
        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.break_ended_title))
            .setContentText(getString(R.string.break_ended_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BREAK_ALERT_NOTIFICATION_ID, notification)
    }

    private fun showFocusCompleteNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = try {
            val soundUriString = prefs.getString("pomodoro_sound", null)
            if (soundUriString.isNullOrEmpty()) {
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            } else {
                soundUriString.toUri()
            }
        } catch (_: Exception) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }

        val notification = NotificationCompat.Builder(this, FOCUS_MODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.focus_session_complete))
            .setContentText(getString(R.string.focus_session_complete_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
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
