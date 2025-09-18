package dev.pranav.reef.accessibility

import android.annotation.SuppressLint
import android.app.Notification
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
        const val EXTRA_TIME_LEFT = "left"
        var isRunning = false
    }

    private lateinit var countDownTimer: CountDownTimer
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val focusTimeMillis = prefs.getLong("focus_time", TimeUnit.MINUTES.toMillis(10))

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(
                title = getString(R.string.focus_mode),
                text = getString(R.string.time_remaining, getFormattedTime(focusTimeMillis)),
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        startCountdown(focusTimeMillis)

        val notification = createNotification(
            title = "Focus Mode", text = "You have completed the focus mode!"
        )

        notificationManager.notify(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startCountdown(durationMillis: Long) {
        isRunning = true
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotificationAndBroadcast(millisUntilFinished)
            }

            override fun onFinish() {
                notificationManager.cancel(NOTIFICATION_ID)
                onComplete()
                isRunning = false
                stopSelf()
            }
        }.start()
    }

    private fun updateNotificationAndBroadcast(millisUntilFinished: Long) {
        val formattedTime = getFormattedTime(millisUntilFinished)

        sendTimerUpdateBroadcast(formattedTime)

        val notification = createNotification(
            title = getString(R.string.focus_mode),
            text = getString(R.string.time_remaining, formattedTime),
            pendingIntent = createTimerActivityPendingIntent(formattedTime)
        )

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun onComplete() {
        val formattedTime = getFormattedTime(0)

        prefs.edit { putBoolean("focus_mode", false) }

        sendTimerUpdateBroadcast(formattedTime)

        val notification = createNotification(
            title = getString(R.string.focus_mode),
            text = getString(R.string.focus_mode_complete),
        )
        countDownTimer.cancel()
        notificationManager.notify(NOTIFICATION_ID, notification)
        isRunning = false

        stopSelf()
    }

    private fun createNotification(
        title: String, text: String, pendingIntent: PendingIntent? = null
    ): Notification {
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(text)
                .setSmallIcon(R.drawable.round_hourglass_bottom_24).setSilent(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        pendingIntent?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    private fun createTimerActivityPendingIntent(formattedTime: String): PendingIntent {
        val intent = Intent(this, TimerActivity::class.java).apply {
            putExtra(EXTRA_TIME_LEFT, formattedTime)
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
        countDownTimer.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
        prefs.edit { putBoolean("focus_mode", false) }
    }
}

fun getFormattedTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
