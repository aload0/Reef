package dev.pranav.reef.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.Calendar

object ScreenUsageHelper {

    fun fetchUsageForInterval(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        val usageMap = mutableMapOf<String, Long>()
        val lastResumedEvents = mutableMapOf<String, UsageEvents.Event>()

        runCatching {
            val usageEvents = usageStatsManager.queryEvents(start - (3 * 60 * 60 * 1000), end)

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                if (targetPackage != null && event.packageName != targetPackage) continue

                val packageName = event.packageName
                val currentTimeStamp = event.timeStamp
                val eventKey = packageName + event.className

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> lastResumedEvents[eventKey] = event
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        lastResumedEvents.remove(eventKey)?.let { lastResumedEvent ->
                            if (currentTimeStamp > start) {
                                val resumeTimeStamp = maxOf(lastResumedEvent.timeStamp, start)
                                usageMap[packageName] = usageMap.getOrDefault(
                                    packageName,
                                    0L
                                ) + (currentTimeStamp - resumeTimeStamp)
                            }
                        }
                    }
                }
            }
        }

        lastResumedEvents.values
            .maxByOrNull { it.timeStamp }
            ?.let { event ->
                val packageName = event.packageName
                val usageTime = usageMap.getOrDefault(packageName, 0L)
                usageMap[packageName] = usageTime + (end - maxOf(event.timeStamp, start))
            }

        return usageMap
            .mapValues { it.value / 1000 }
            .filterValues { it > 0L }
    }

    fun fetchAppUsageTodayTillNow(usageStatsManager: UsageStatsManager): Map<String, Long> {
        val midNightCal = Calendar.getInstance()
        midNightCal[Calendar.HOUR_OF_DAY] = 0
        midNightCal[Calendar.MINUTE] = 0
        midNightCal[Calendar.SECOND] = 0
        midNightCal[Calendar.MILLISECOND] = 0

        val start = midNightCal.timeInMillis
        val end = System.currentTimeMillis()
        return fetchUsageForInterval(usageStatsManager, start, end)
    }

    fun fetchAppUsageForDay(
        usageStatsManager: UsageStatsManager,
        dayOffset: Int = 0
    ): Map<String, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
        cal[Calendar.HOUR_OF_DAY] = 0
        cal[Calendar.MINUTE] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 0
        val start = cal.timeInMillis

        cal[Calendar.HOUR_OF_DAY] = 23
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999
        val end = minOf(cal.timeInMillis, System.currentTimeMillis())

        return fetchUsageForInterval(usageStatsManager, start, end)
    }

    fun fetchUsageForPackageLastWeek(
        usageStatsManager: UsageStatsManager,
        packageName: String
    ): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())

        for (dayOffset in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
            cal[Calendar.HOUR_OF_DAY] = 0
            cal[Calendar.MINUTE] = 0
            cal[Calendar.SECOND] = 0
            cal[Calendar.MILLISECOND] = 0
            val start = cal.timeInMillis
            val dayLabel = dayFormat.format(cal.time)

            cal[Calendar.HOUR_OF_DAY] = 23
            cal[Calendar.MINUTE] = 59
            cal[Calendar.SECOND] = 59
            cal[Calendar.MILLISECOND] = 999
            val end = minOf(cal.timeInMillis, System.currentTimeMillis())

            val usage =
                fetchUsageForInterval(usageStatsManager, start, end, packageName)[packageName] ?: 0L
            result.add(dayLabel to usage)
        }

        return result
    }

    fun fetchUsageInMs(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        val usageMap = mutableMapOf<String, Long>()
        val lastResumedEvents = mutableMapOf<String, UsageEvents.Event>()

        runCatching {
            val usageEvents = usageStatsManager.queryEvents(start - (3 * 60 * 60 * 1000), end)

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                if (targetPackage != null && event.packageName != targetPackage) continue

                val packageName = event.packageName
                val currentTimeStamp = event.timeStamp
                val eventKey = packageName + event.className

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> lastResumedEvents[eventKey] = event
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        lastResumedEvents.remove(eventKey)?.let { lastResumedEvent ->
                            if (currentTimeStamp > start) {
                                val resumeTimeStamp = maxOf(lastResumedEvent.timeStamp, start)
                                usageMap[packageName] = usageMap.getOrDefault(
                                    packageName,
                                    0L
                                ) + (currentTimeStamp - resumeTimeStamp)
                            }
                        }
                    }
                }
            }
        }

        lastResumedEvents.values
            .maxByOrNull { it.timeStamp }
            ?.let { event ->
                val packageName = event.packageName
                val usageTime = usageMap.getOrDefault(packageName, 0L)
                usageMap[packageName] = usageTime + (end - maxOf(event.timeStamp, start))
            }

        return usageMap.filterValues { it > 0L }
    }
}

