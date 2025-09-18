package dev.pranav.reef.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

object RoutineScheduler {
    private const val TAG = "RoutineScheduler"

    fun scheduleAllRoutines(context: Context) {
        val routines = RoutineManager.getRoutines().filter { it.isEnabled }
        routines.forEach { routine ->
            scheduleRoutine(context, routine)
        }
    }

    fun scheduleRoutine(context: Context, routine: Routine) {
        if (!routine.isEnabled || routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
            return
        }

        // Schedule activation
        scheduleRoutineAction(context, routine, isActivation = true)

        // Schedule deactivation if end time exists
        if (routine.schedule.endTime != null) {
            scheduleRoutineAction(context, routine, isActivation = false)
        }
    }

    private fun scheduleRoutineAction(context: Context, routine: Routine, isActivation: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RoutineActivationReceiver::class.java).apply {
            putExtra("routine_id", routine.id)
            putExtra("is_activation", isActivation)
        }
        val requestCode = if (isActivation) routine.id.hashCode() else routine.id.hashCode() + 1
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = if (isActivation) {
            calculateNextTriggerTime(routine.schedule, useStartTime = true)
        } else {
            calculateNextTriggerTime(routine.schedule, useStartTime = false)
        }

        if (triggerTime != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    val action = if (isActivation) "activation" else "deactivation"
                    Log.d(TAG, "Scheduled routine ${routine.name} $action for ${Date(triggerTime)}")
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    val action = if (isActivation) "activation" else "deactivation"
                    Log.d(
                        TAG,
                        "Scheduled routine ${routine.name} $action (inexact) for ${Date(triggerTime)}"
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                val action = if (isActivation) "activation" else "deactivation"
                Log.d(
                    TAG,
                    "Scheduled routine ${routine.name} $action (fallback) for ${Date(triggerTime)}"
                )
            }
        }
    }

    fun cancelRoutine(context: Context, routineId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel activation alarm
        val activationIntent = Intent(context, RoutineActivationReceiver::class.java)
        val activationPendingIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode(),
            activationIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        activationPendingIntent?.let { alarmManager.cancel(it) }

        // Cancel deactivation alarm
        val deactivationPendingIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode() + 1,
            activationIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        deactivationPendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun calculateNextTriggerTime(schedule: RoutineSchedule, useStartTime: Boolean): Long? {
        val now = LocalDateTime.now()
        val time = if (useStartTime) schedule.time else schedule.endTime
        if (time == null) return null

        return when (schedule.type) {
            RoutineSchedule.ScheduleType.DAILY -> {
                var nextTrigger = now.withHour(time.hour).withMinute(time.minute).withSecond(0)
                if (nextTrigger.isBefore(now) || nextTrigger.isEqual(now)) {
                    nextTrigger = nextTrigger.plusDays(1)
                }
                nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            RoutineSchedule.ScheduleType.WEEKLY -> {
                val targetDays = schedule.daysOfWeek
                if (targetDays.isEmpty()) return null

                var nextTrigger = now.withHour(time.hour).withMinute(time.minute).withSecond(0)
                var daysToAdd = 0

                do {
                    val dayOfWeek = nextTrigger.dayOfWeek
                    if (targetDays.contains(dayOfWeek) && nextTrigger.isAfter(now)) {
                        break
                    }
                    nextTrigger = nextTrigger.plusDays(1)
                    daysToAdd++
                } while (daysToAdd < 7)

                nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            RoutineSchedule.ScheduleType.MANUAL -> null
        }
    }
}

class RoutineActivationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra("routine_id") ?: return
        val isActivation = intent.getBooleanExtra("is_activation", true)
        val routine = RoutineManager.getRoutines().find { it.id == routineId } ?: return

        if (!routine.isEnabled) return

        if (isActivation) {
            activateRoutine(context, routine)
        } else {
            deactivateRoutine(context, routine)
        }
    }

    private fun activateRoutine(context: Context, routine: Routine) {
        Log.d("RoutineActivationReceiver", "Activating routine: ${routine.name}")

        // Apply routine limits using the separate system
        val limitsMap = routine.limits.associate { it.packageName to it.limitMinutes }
        RoutineLimits.setRoutineLimits(limitsMap, routine.id)

        // Schedule next occurrence if recurring
        if (routine.schedule.isRecurring) {
            RoutineScheduler.scheduleRoutine(context, routine)
        }

        // Show notification about routine activation
        NotificationHelper.showRoutineActivatedNotification(context, routine)
    }

    private fun deactivateRoutine(context: Context, routine: Routine) {
        Log.d("RoutineActivationReceiver", "Deactivating routine: ${routine.name}")

        // Clear routine limits
        RoutineLimits.clearRoutineLimits()

        // Schedule next occurrence if recurring
        if (routine.schedule.isRecurring) {
            RoutineScheduler.scheduleRoutine(context, routine)
        }

        // Show notification about routine deactivation
        NotificationHelper.showRoutineDeactivatedNotification(context, routine)
    }
}
