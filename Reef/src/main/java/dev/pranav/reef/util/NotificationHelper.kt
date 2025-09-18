package dev.pranav.reef.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.R
import dev.pranav.reef.RoutinesActivity
import dev.pranav.reef.data.Routine

object NotificationHelper {
    private const val ROUTINE_NOTIFICATION_ID = 100

    fun showRoutineActivatedNotification(context: Context, routine: Routine) {
        val intent = Intent(context, RoutinesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val limitsText = when (routine.limits.size) {
            0 -> "No app limits applied"
            1 -> "1 app limit applied"
            else -> "${routine.limits.size} app limits applied"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Routine Activated")
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
        val intent = Intent(context, RoutinesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Routine Deactivated")
            .setContentText("${routine.name} has ended")
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
}
