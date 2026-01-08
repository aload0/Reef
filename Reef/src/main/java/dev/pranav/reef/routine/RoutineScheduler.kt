package dev.pranav.reef.routine

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling of routines using WorkManager for reliable execution.
 * Handles scheduling activation and deactivation work requests.
 */
object RoutineScheduler {
    private const val TAG = "RoutineScheduler"

    /**
     * Schedule all enabled routines.
     */
    fun scheduleAllRoutines(context: Context) {
        val routines = Routines.getAll().filter { it.isEnabled }
        routines.forEach { routine ->
            scheduleRoutine(context, routine)
        }
    }

    /**
     * Schedule a single routine. Determines if it should be active now,
     * and schedules appropriate activation/deactivation work.
     */
    fun scheduleRoutine(context: Context, routine: Routine) {
        if (!routine.isEnabled || routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
            return
        }

        if (isRoutineActiveNow(routine)) {
            Routines.startSession(context, routine)

            if (routine.schedule.endTime != null) {
                scheduleDeactivation(context, routine)
            }
        } else {
            scheduleActivation(context, routine)

            if (routine.schedule.endTime != null) {
                scheduleDeactivation(context, routine)
            }
        }
    }

    /**
     * Schedule routine activation using WorkManager.
     */
    fun scheduleActivation(context: Context, routine: Routine) {
        scheduleWork(context, routine, isActivation = true)
    }

    /**
     * Schedule routine deactivation using WorkManager.
     */
    fun scheduleDeactivation(context: Context, routine: Routine) {
        scheduleWork(context, routine, isActivation = false)
    }

    /**
     * Cancel all scheduled work for a routine.
     */
    fun cancelRoutine(context: Context, routineId: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(RoutineWorker.getActivationWorkName(routineId))
        workManager.cancelUniqueWork(RoutineWorker.getDeactivationWorkName(routineId))
        Log.d(TAG, "Cancelled all work for routine: $routineId")
    }

    private fun scheduleWork(context: Context, routine: Routine, isActivation: Boolean) {
        val triggerTime = calculateNextTriggerTime(
            routine.schedule,
            useStartTime = isActivation
        ) ?: return

        val delay = triggerTime - System.currentTimeMillis()
        if (delay <= 0) {
            Log.w(TAG, "Trigger time already passed for ${routine.name}, skipping")
            return
        }

        val inputData = Data.Builder()
            .putString(RoutineWorker.KEY_ROUTINE_ID, routine.id)
            .putBoolean(RoutineWorker.KEY_IS_ACTIVATION, isActivation)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<RoutineWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        val workName = if (isActivation) {
            RoutineWorker.getActivationWorkName(routine.id)
        } else {
            RoutineWorker.getDeactivationWorkName(routine.id)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        val action = if (isActivation) "activation" else "deactivation"
        Log.d(TAG, "Scheduled ${routine.name} $action with WorkManager for ${Date(triggerTime)}")
    }

    /**
     * Check if the routine is active at the current moment.
     */
    fun isRoutineActiveNow(routine: Routine): Boolean {
        return getRoutineStartTime(routine) != null
    }

    /**
     * Get the start time of the routine for the current day, or null if it doesn't occur today.
     */
    fun getRoutineStartTime(routine: Routine): Long? {
        val now = LocalDateTime.now()
        val schedule = routine.schedule

        Log.d(TAG, "getRoutineStartTime: Checking if routine should be active")
        Log.d(TAG, "  Current time: $now")
        Log.d(TAG, "  Schedule type: ${schedule.type}")

        if (schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
            Log.d(TAG, "  Result: MANUAL routine, not active")
            return null
        }

        val startTime = schedule.time
        val endTime = schedule.endTime

        Log.d(TAG, "  Schedule start: $startTime")
        Log.d(TAG, "  Schedule end: $endTime")

        if (startTime == null || endTime == null) {
            Log.d(TAG, "  Result: Missing time, not active")
            return null
        }

        val candidates = listOf(
            now.withHour(startTime.hour).withMinute(startTime.minute).withSecond(0).withNano(0),
            now.minusDays(1).withHour(startTime.hour).withMinute(startTime.minute).withSecond(0)
                .withNano(0)
        )

        for (startCandidate in candidates) {
            val endCandidate = if (endTime.isBefore(startTime)) {
                startCandidate.plusDays(1).withHour(endTime.hour).withMinute(endTime.minute)
                    .withSecond(0).withNano(0)
            } else {
                startCandidate.withHour(endTime.hour).withMinute(endTime.minute).withSecond(0)
                    .withNano(0)
            }

            Log.d(TAG, "  Checking candidate: start=$startCandidate, end=$endCandidate")
            Log.d(
                TAG,
                "    now >= start: ${now.isEqual(startCandidate) || now.isAfter(startCandidate)}"
            )
            Log.d(TAG, "    now < end: ${now.isBefore(endCandidate)}")

            if ((now.isEqual(startCandidate) || now.isAfter(startCandidate)) && now.isBefore(
                    endCandidate
                )
            ) {
                if (schedule.type == RoutineSchedule.ScheduleType.WEEKLY) {
                    val matchesDay = schedule.daysOfWeek.contains(startCandidate.dayOfWeek)
                    Log.d(
                        TAG,
                        "    Weekly routine, day matches: $matchesDay (${startCandidate.dayOfWeek})"
                    )
                    if (matchesDay) {
                        val result =
                            startCandidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        Log.d(TAG, "  Result: ACTIVE (start=$result)")
                        return result
                    }
                } else {
                    val result =
                        startCandidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Log.d(TAG, "  Result: ACTIVE (start=$result)")
                    return result
                }
            }
        }

        Log.d(TAG, "  Result: NOT ACTIVE")
        return null
    }

    /**
     * Calculate the next trigger time for a routine schedule.
     */
    fun calculateNextTriggerTime(schedule: RoutineSchedule, useStartTime: Boolean): Long? {
        val now = LocalDateTime.now()
        val time = if (useStartTime) schedule.time else schedule.endTime
        if (time == null) return null

        return when (schedule.type) {
            RoutineSchedule.ScheduleType.DAILY -> calculateDailyTriggerTime(now, time)
            RoutineSchedule.ScheduleType.WEEKLY -> {
                var targetDays = schedule.daysOfWeek
                if (!useStartTime && schedule.time != null && schedule.endTime != null && schedule.endTime!!.isBefore(
                        schedule.time!!
                    )
                ) {
                    targetDays = targetDays.map { it.plus(1) }.toSet()
                }
                calculateWeeklyTriggerTime(now, time, targetDays)
            }

            RoutineSchedule.ScheduleType.MANUAL -> null
        }
    }

    /**
     * Get the maximum duration for which a routine can run, based on its schedule.
     */
    fun getMaxRoutineDuration(schedule: RoutineSchedule): Long {
        val startTime = schedule.time
        val endTime = schedule.endTime

        if (startTime == null || endTime == null) {
            return 24 * 60 * 60 * 1000L
        }

        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute

        val durationMinutes = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            (24 * 60 - startMinutes) + endMinutes
        }

        return durationMinutes * 60 * 1000L
    }

    private fun calculateDailyTriggerTime(now: LocalDateTime, time: java.time.LocalTime): Long {
        var nextTrigger = now.withHour(time.hour).withMinute(time.minute).withSecond(0)
        if (nextTrigger.isBefore(now) || nextTrigger.isEqual(now)) {
            nextTrigger = nextTrigger.plusDays(1)
        }
        return nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun calculateWeeklyTriggerTime(
        now: LocalDateTime,
        time: java.time.LocalTime,
        targetDays: Set<java.time.DayOfWeek>
    ): Long? {
        if (targetDays.isEmpty()) return null

        var nextTrigger = now.withHour(time.hour).withMinute(time.minute).withSecond(0)
        var daysChecked = 0

        while (daysChecked < 7) {
            val dayOfWeek = nextTrigger.dayOfWeek
            if (targetDays.contains(dayOfWeek) && nextTrigger.isAfter(now)) {
                return nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            nextTrigger = nextTrigger.plusDays(1)
            daysChecked++
        }

        return nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
