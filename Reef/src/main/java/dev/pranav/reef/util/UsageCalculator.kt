package dev.pranav.reef.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageCalculator {

    fun calculateUsage(
        context: Context,
        usm: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        return ScreenUsageHelper.fetchUsageInMs(usm, startTime, endTime, targetPackage)
    }

    fun getDailyUsage(usm: UsageStatsManager, packageName: String): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        return ScreenUsageHelper.fetchUsageInMs(usm, startOfDay, now, packageName)[packageName]
            ?: 0L
    }

    fun getTodayUsageMap(usm: UsageStatsManager): Map<String, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return ScreenUsageHelper.fetchUsageInMs(usm, cal.timeInMillis, System.currentTimeMillis())
    }
}
