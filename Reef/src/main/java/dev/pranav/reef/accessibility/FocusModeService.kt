package dev.pranav.reef.accessibility

import android.annotation.SuppressLint
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
import dev.pranav.reef.R
import dev.pranav.reef.TimerActivity
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.prefs
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class FocusModeService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_TIMER_UPDATED = "dev.pranav.reef.TIMER_UPDATED"
        const val ACTION_PAUSE = "dev.pranav.reef.PAUSE_TIMER"
        const val ACTION_RESUME = "dev.pranav.reef.RESUME_TIMER"
        const val EXTRA_TIME_LEFT = "extra_time_left"

        var isRunning = false
        var isPaused = false
    }

    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private var countDownTimer: CountDownTimer? = null
    private var currentMillisRemaining: Long = 0
    private var isStrictMode: Boolean = false
    private var notificationBuilder: NotificationCompat.Builder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (!isStrictMode) {
                    pauseTimer()
                }
                return START_STICKY
            }

            ACTION_RESUME -> {
                if (!isStrictMode) {
                    resumeTimer()
                }
                return START_STICKY
            }

            else -> {
                val focusTimeMillis = prefs.getLong("focus_time", TimeUnit.MINUTES.toMillis(10))
                isStrictMode = prefs.getBoolean("strict_mode", false)
                currentMillisRemaining = focusTimeMillis
                isRunning = true
                isPaused = false

                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createNotification(
                        title = getString(R.string.focus_mode),
                        text = getString(
                            R.string.time_remaining,
                            getFormattedTime(focusTimeMillis)
                        ),
                        showPauseButton = !isStrictMode
                    ),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else 0
                )

                startCountdown(focusTimeMillis)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startCountdown(timeMillis: Long) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused) {
                    currentMillisRemaining = millisUntilFinished
                    updateNotificationAndBroadcast(millisUntilFinished)
                }
            }

            override fun onFinish() {
                onComplete()
            }
        }.start()
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        isPaused = true

        prefs.edit {
            putLong("focus_time_remaining", currentMillisRemaining)
            putBoolean("focus_mode", false)
        }

        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(
                title = getString(R.string.focus_mode),
                text = getFormattedTime(currentMillisRemaining),
                showPauseButton = false
            )
        )

        sendTimerUpdateBroadcast(getFormattedTime(currentMillisRemaining))
    }

    private fun resumeTimer() {
        isRunning = true
        isPaused = false

        prefs.edit {
            putBoolean("focus_mode", true)
        }

        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(
                title = getString(R.string.focus_mode),
                text = getString(R.string.time_remaining, getFormattedTime(currentMillisRemaining)),
                showPauseButton = true
            )
        )

        startCountdown(currentMillisRemaining)
    }

    private fun updateNotificationAndBroadcast(millisUntilFinished: Long) {
        val formattedTime = getFormattedTime(millisUntilFinished)

        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(
                title = getString(R.string.focus_mode),
                text = getString(R.string.time_remaining, formattedTime),
                showPauseButton = !isStrictMode && !isPaused
            )
        )

        sendTimerUpdateBroadcast(formattedTime)
    }

    private fun onComplete() {
        isRunning = false
        isPaused = false
        prefs.edit {
            putBoolean("focus_mode", false)
        }
        sendTimerUpdateBroadcast("00:00")
        stopSelf()
    }

    private fun createNotification(
        title: String,
        text: String,
        showPauseButton: Boolean
    ): android.app.Notification {
        if (notificationBuilder == null) {
            val intent = Intent(this, TimerActivity::class.java).apply {
                putExtra(EXTRA_TIME_LEFT, text)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        }

        notificationBuilder?.setContentTitle(title)
        notificationBuilder?.setContentText(text)
        notificationBuilder?.clearActions()

        if (showPauseButton) {
            val pauseIntent = Intent(this, FocusModeService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder?.addAction(
                R.drawable.ic_launcher_foreground,
                "Pause",
                pausePendingIntent
            )
        } else {
            val resumeIntent = Intent(this, FocusModeService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this,
                2,
                resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder?.addAction(
                R.drawable.ic_launcher_foreground,
                "Resume",
                resumePendingIntent
            )
        }

        return notificationBuilder?.build()
            ?: throw IllegalStateException("Builder not initialized")
    }

    private fun sendTimerUpdateBroadcast(formattedTime: String) {
        val intent = Intent(ACTION_TIMER_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_TIME_LEFT, formattedTime)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
        isRunning = false
        isPaused = false
        prefs.edit {
            putBoolean("focus_mode", false)
        }
    }
}

fun getFormattedTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
