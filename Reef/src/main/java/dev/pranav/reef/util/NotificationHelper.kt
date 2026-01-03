package dev.pranav.reef.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.data.Routine

object NotificationHelper {
    private const val ROUTINE_NOTIFICATION_ID = 100
    private const val REMINDER_NOTIFICATION_ID = 200

    fun Context.createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.blocker_channel_name),
            importance
        ).apply {
            description = getString(R.string.blocker_channel_description)
            setSound(null, null)
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showRoutineActivatedNotification(context: Context, routine: Routine) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to_routines", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val limitsText = when (routine.limits.size) {
            0 -> context.getString(R.string.no_app_limits_applied)
            else -> context.resources.getQuantityString(
                R.plurals.app_limits_applied,
                routine.limits.size
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.routine_activated))
            .setContentText("${routine.name} - $limitsText")
            .setSmallIcon(R.drawable.round_schedule_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(ROUTINE_NOTIFICATION_ID, builder.build())
        }
    }

    fun showRoutineDeactivatedNotification(context: Context, routine: Routine) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to_routines", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.routine_deactivated))
            .setContentText(context.getString(R.string.routine_has_ended, routine.name))
            .setSmallIcon(R.drawable.round_schedule_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(ROUTINE_NOTIFICATION_ID + 1, builder.build())
        }
    }

    fun showReminderNotification(context: Context, packageName: String, timeRemaining: Long) {
        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

        val minutes = (timeRemaining / 60000).toInt()

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.time_limit_reminder))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.app_will_be_blocked_in,
                    minutes,
                    appName
                )
            )
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                REMINDER_NOTIFICATION_ID + packageName.hashCode(),
                builder.build()
            )
        }
    }
}
