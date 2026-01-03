package dev.pranav.reef.receivers

import android.Manifest
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.ScreenUsageHelper
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

class DailySummaryReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isPrefsInitialized) {
            prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }

        if (!prefs.getBoolean("daily_summary", false)) {
            return
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayUsage = ScreenUsageHelper.fetchAppUsageTodayTillNow(usageStatsManager)
        val totalUsageSeconds = todayUsage.values.sum()
        val totalUsageMinutes = totalUsageSeconds / 60

        val hours = totalUsageMinutes / 60
        val minutes = totalUsageMinutes % 60

        val usageText = when {
            hours > 0 && minutes > 0 -> context.getString(
                R.string.hour_min_short_suffix,
                hours,
                minutes
            )

            hours > 0 -> context.getString(R.string.hours_short_format, hours)
            minutes > 0 -> context.getString(R.string.minutes_short_format, minutes)
            else -> context.getString(R.string.less_than_one_minute)
        }

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to_usage", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.daily_summary_title))
            .setContentText(context.getString(R.string.daily_summary_message, usageText))
            .setSmallIcon(R.drawable.round_schedule_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(DAILY_SUMMARY_NOTIFICATION_ID, notification)
        }

        DailySummaryScheduler.scheduleNextDailySummary(context)
    }

    companion object {
        const val DAILY_SUMMARY_NOTIFICATION_ID = 300
    }
}

