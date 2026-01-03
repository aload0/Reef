package dev.pranav.reef.accessibility

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import dev.pranav.reef.routine.RoutineScheduleCalculator
import dev.pranav.reef.util.*
import java.util.Calendar

object UsageTracker {

    private val systemAppCache = mutableMapOf<String, Boolean>()

    enum class BlockReason {
        NONE,
        DAILY_LIMIT,
        ROUTINE_LIMIT
    }

    fun shouldBlock(context: Context, packageName: String): Boolean {
        return checkBlockReason(context, packageName) != BlockReason.NONE
    }

    fun checkBlockReason(context: Context, packageName: String): BlockReason {
        if (Whitelist.isWhitelisted(packageName)) return BlockReason.NONE

        if (shouldSkipPackage(context, packageName)) return BlockReason.NONE

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        if (RoutineLimits.hasRoutineLimit(packageName)) {
            val routineUsage = getRoutineUsage(packageName, usm)
            val limit = RoutineLimits.getRoutineLimit(packageName)

            Log.d("UsagePolicyEngine", "Routine usage: $routineUsage ms, limit: $limit ms")

            if (routineUsage >= (limit * 0.85) && routineUsage < limit) {
                if (!RoutineLimits.hasRoutineReminderBeenSent(packageName)) {
                    val timeRemaining = limit - routineUsage
                    NotificationHelper.showReminderNotification(context, packageName, timeRemaining)
                    RoutineLimits.markRoutineReminderSent(packageName)
                }
            }

            if (routineUsage >= limit) {
                return BlockReason.ROUTINE_LIMIT
            }
        }

        if (AppLimits.hasLimit(packageName)) {
            val dailyUsage = getDailyUsage(packageName, usm)
            val limit = AppLimits.getLimit(packageName)

            Log.d("UsagePolicyEngine", "Daily usage: $dailyUsage ms, limit: $limit ms")

            if (dailyUsage >= (limit * 0.85) && dailyUsage < limit) {
                if (!AppLimits.reminderSentToday(packageName)) {
                    val timeRemaining = limit - dailyUsage
                    NotificationHelper.showReminderNotification(context, packageName, timeRemaining)
                    AppLimits.markReminder(packageName)
                }
            }

            if (dailyUsage >= limit) {
                return BlockReason.DAILY_LIMIT
            }
        }

        return BlockReason.NONE
    }

    private fun getRoutineUsage(
        packageName: String,
        usm: UsageStatsManager
    ): Long {
        val routineId = RoutineLimits.getActiveRoutineId() ?: return 0L
        val routine = RoutineManager.getRoutines().find { it.id == routineId } ?: return 0L

        val routineStart = prefs.getLong(RoutineLimits.ROUTINE_START_TIME_KEY, 0L)
        val routineEnd =
            routineStart + RoutineScheduleCalculator.getMaxRoutineDuration(routine.schedule)
        val endTime = minOf(System.currentTimeMillis(), routineEnd)

        return ScreenUsageHelper.fetchUsageInMs(
            usm,
            routineStart,
            endTime,
            packageName
        )[packageName] ?: 0L
    }

    private fun getDailyUsage(packageName: String, usm: UsageStatsManager): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        return ScreenUsageHelper.fetchUsageInMs(
            usm,
            startOfDay,
            now,
            packageName
        )[packageName] ?: 0L
    }

    private fun shouldSkipPackage(context: Context, packageName: String): Boolean {
        if (systemAppCache.containsKey(packageName)) {
            return systemAppCache[packageName]!!
        }

        val shouldSkip = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            if (isSystem) {
                pm.getLaunchIntentForPackage(packageName) == null
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        systemAppCache[packageName] = shouldSkip
        return shouldSkip
    }
}
