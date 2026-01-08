package dev.pranav.reef.accessibility

import android.app.usage.UsageStatsManager
import android.content.Context
import dev.pranav.reef.routine.Routines
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
        val limitWarningsEnabled = prefs.getBoolean("limit_warnings", true)

        // Check routine limits
        Routines.getLimitMs(packageName)?.let { limitMs ->
            val usageMs = Routines.getUsageMs(context, packageName)

            android.util.Log.d(
                "UsageTracker",
                "Routine check for $packageName: usage=$usageMs, limit=$limitMs"
            )

            if (limitWarningsEnabled && usageMs >= (limitMs * 0.85) && usageMs < limitMs) {
                val timeRemaining = limitMs - usageMs
                NotificationHelper.showReminderNotification(context, packageName, timeRemaining)
            }

            if (usageMs >= limitMs) {
                android.util.Log.d("UsageTracker", "BLOCKING $packageName due to routine limit")
                return BlockReason.ROUTINE_LIMIT
            }
        }

        // Check daily limits
        if (AppLimits.hasLimit(packageName)) {
            val dailyUsage = getDailyUsage(packageName, usm)
            val limit = AppLimits.getLimit(packageName)

            if (limitWarningsEnabled && dailyUsage >= (limit * 0.85) && dailyUsage < limit) {
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
