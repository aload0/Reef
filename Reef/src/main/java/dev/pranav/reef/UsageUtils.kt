package dev.pranav.reef

import android.app.usage.UsageStatsManager
import dev.pranav.reef.screens.HourlyUsageData
import dev.pranav.reef.util.ScreenUsageHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun getDailyUsageForLastWeek(
    packageName: String,
    usageStatsManager: UsageStatsManager,
    weekOffset: Int = 0
): List<HourlyUsageData> {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val result = mutableListOf<HourlyUsageData>()

    val baseOffset = weekOffset * 7

    for (dayOffset in 6 downTo 0) {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -dayOffset + baseOffset)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val dayEnd = minOf(calendar.timeInMillis, System.currentTimeMillis())

        if (dayStart > System.currentTimeMillis()) {
            continue
        }

        val usageMs = ScreenUsageHelper.fetchUsageInMs(
            usageStatsManager,
            dayStart,
            dayEnd,
            packageName
        )[packageName] ?: 0L

        val instant = Instant.ofEpochMilli(dayStart)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        val day = zonedDateTime.format(dateFormatter)

        result.add(
            HourlyUsageData(
                day = day,
                usageMinutes = usageMs / 60000.0,
                timestamp = dayStart
            )
        )
    }

    return result
}
