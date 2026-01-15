package dev.pranav.reef.util

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Calendar


object ScreenUsageHelper {

    private const val TAG = "ScreenUsageHelper"

    fun calculateUsage(
        @Suppress("UNUSED_PARAMETER") context: Context,
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        return fetchUsageInMs(usageStatsManager, startTime, endTime, targetPackage)
    }

    fun fetchUsageInMs(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        try {
            val eventBasedUsage =
                calculateUsageFromEvents(usageStatsManager, start, end, targetPackage)

            if (eventBasedUsage.isEmpty()) {
                Log.w(TAG, "Event-based tracking returned no data, using UsageStats fallback")
                return calculateUsageFromStats(usageStatsManager, start, end, targetPackage)
            }

            return eventBasedUsage
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching usage", e)
            return calculateUsageFromStats(usageStatsManager, start, end, targetPackage)
        }
    }

    private fun calculateUsageFromEvents(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        val packageForegroundTimes = mutableMapOf<String, Long>()
        val packageStartTimes = mutableMapOf<String, Long>()

        var usageEvents: UsageEvents
        val event = UsageEvents.Event()

        runCatching {
            val lookBackStart = start - (2 * 60 * 60 * 1000)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                usageEvents = usageStatsManager.queryEvents(lookBackStart, start)
            } else {
                val query = UsageEventsQuery.Builder(
                    lookBackStart,
                    start,
                ).setEventTypes(*intArrayOf(UsageEvents.Event.ACTIVITY_RESUMED))

                if (targetPackage != null) {
                    query.setPackageNames(targetPackage)
                }

                usageEvents = usageStatsManager.queryEvents(query.build())!!
            }

            while (usageEvents.hasNextEvent() && usageEvents.getNextEvent(event)) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    packageStartTimes[event.packageName] = event.timeStamp
                }
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                usageEvents = usageStatsManager.queryEvents(start, end)
            } else {
                val query = UsageEventsQuery.Builder(
                    start,
                    end,
                ).setEventTypes(*intArrayOf(UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED))

                if (targetPackage != null) {
                    query.setPackageNames(targetPackage)
                }

                usageEvents = usageStatsManager.queryEvents(query.build())!!
            }

            while (usageEvents.hasNextEvent() && usageEvents.getNextEvent(event)) {
                val packageName = event.packageName
                val timestamp = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        packageStartTimes[packageName] = timestamp
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        packageStartTimes[packageName]?.let { startTime ->
                            val duration = timestamp - startTime
                            packageForegroundTimes[packageName] =
                                packageForegroundTimes.getOrDefault(packageName, 0L) + duration
                            packageStartTimes.remove(packageName)
                        }
                    }
                }
            }
        }

        return packageForegroundTimes.filterValues { it > 0L }
    }

    private fun calculateUsageFromStats(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
        targetPackage: String? = null
    ): Map<String, Long> {
        val usageMap = mutableMapOf<String, Long>()

        runCatching {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                start,
                end
            )

            stats?.forEach { usageStat ->
                if (targetPackage == null || usageStat.packageName == targetPackage) {
                    val totalTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        usageStat.totalTimeVisible
                    } else {
                        usageStat.totalTimeInForeground
                    }

                    if (totalTime > 0) {
                        usageMap[usageStat.packageName] = totalTime
                    }
                }
            }
        }

        return usageMap.filterValues { it > 0L }
    }

    fun fetchAppUsageTodayTillNow(usageStatsManager: UsageStatsManager): Map<String, Long> {
        val midNightCal = Calendar.getInstance()
        midNightCal[Calendar.HOUR_OF_DAY] = 0
        midNightCal[Calendar.MINUTE] = 0
        midNightCal[Calendar.SECOND] = 0
        midNightCal[Calendar.MILLISECOND] = 0

        val start = midNightCal.timeInMillis
        val end = System.currentTimeMillis()
        return fetchUsageInMs(usageStatsManager, start, end).mapValues { it.value / 1000 }
    }
}
