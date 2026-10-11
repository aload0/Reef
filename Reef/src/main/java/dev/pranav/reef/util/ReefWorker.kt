package dev.pranav.reef.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.BlockerService
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.services.routines.RoutineSessionManager

class ReefWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {
        val safeContext = applicationContext.createDeviceProtectedStorageContext()

        val prefs = safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val isFocusModeActive = prefs.getBoolean("focus_mode", false)

        if (!ServiceHealthChecker.isAccessibilitySettingEnabled(safeContext)) {
            sendInstantNotification(
                safeContext,
                channelId = "reef_alerts",
                channelName = "Reef Alerts",
                title = safeContext.getString(R.string.accessibility_service_disabled_title),
                message = safeContext.getString(R.string.accessibility_service_description),
                notificationId = ACCESSIBILITY_ALERT_NOTIFICATION_ID
            )
            return Result.success()
        }

        if (ServiceHealthChecker.isServiceDesynced(safeContext)) {
            if (runAttemptCount == 0) {
                return Result.retry()
            }
            sendInstantNotification(
                safeContext,
                channelId = "reef_alerts",
                channelName = "Reef Alerts",
                title = safeContext.getString(R.string.accessibility_service_not_running),
                message = safeContext.getString(
                    R.string.accessibility_service_not_running_description
                ),
                notificationId = ACCESSIBILITY_ALERT_NOTIFICATION_ID
            )
            return Result.success()
        }

        if (isFocusModeActive) {
            val intent = Intent(safeContext, FocusModeService::class.java)
            safeContext.startForegroundService(intent)
        }

        if (!isPrefsInitialized) {
            dev.pranav.reef.util.prefs =
                safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }
        RoutineSessionManager.evaluateAndSync(safeContext)
        NotificationHelper.syncRoutineNotification(safeContext)

        return Result.success()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendInstantNotification(
        context: Context,
        channelId: String,
        channelName: String,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Channel for Reef alerts"
            }
        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val ACCESSIBILITY_ALERT_NOTIFICATION_ID = 7001
    }
}
