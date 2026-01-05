package dev.pranav.reef.util

import android.app.usage.UsageEvents
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
        val usageMap = mutableMapOf<String, Long>()

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
        val usageMap = mutableMapOf<String, Long>()
        val packageForegroundTime = mutableMapOf<String, Long>()
        var currentForegroundPackage: String? = null
        var foregroundStartTime: Long = 0

        val lookbackStart = start - (24 * 60 * 60 * 1000)

        runCatching {
            val usageEvents = usageStatsManager.queryEvents(lookbackStart, end)

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                if (event.timeStamp < start) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                    ) {
                        currentForegroundPackage = event.packageName
                        foregroundStartTime = start
                    }
                    continue
                }

                if (targetPackage != null && event.packageName != targetPackage) {
                    if (currentForegroundPackage == targetPackage &&
                        (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED))
                    ) {
                        val duration = event.timeStamp - foregroundStartTime
                        if (duration > 0) {
                            packageForegroundTime[targetPackage] =
                                packageForegroundTime.getOrDefault(targetPackage, 0L) + duration
                        }
                        currentForegroundPackage = null
                    }
                    continue
                }

                val packageName = event.packageName
                val timestamp = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (currentForegroundPackage != null && currentForegroundPackage != packageName) {
                            val duration = timestamp - foregroundStartTime
                            if (duration > 0 && foregroundStartTime >= start) {
                                packageForegroundTime[currentForegroundPackage!!] =
                                    packageForegroundTime.getOrDefault(
                                        currentForegroundPackage!!,
                                        0L
                                    ) + duration
                            }
                        }
                        currentForegroundPackage = packageName
                        foregroundStartTime = timestamp
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (currentForegroundPackage == packageName) {
                            val duration = timestamp - foregroundStartTime
                            if (duration > 0 && foregroundStartTime >= start) {
                                packageForegroundTime[packageName] =
                                    packageForegroundTime.getOrDefault(packageName, 0L) + duration
                            }
                            currentForegroundPackage = null
                        }
                    }

                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (currentForegroundPackage != null) {
                            foregroundStartTime = timestamp
                        }
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        if (currentForegroundPackage != null) {
                            val duration = timestamp - foregroundStartTime
                            if (duration > 0 && foregroundStartTime >= start) {
                                packageForegroundTime[currentForegroundPackage!!] =
                                    packageForegroundTime.getOrDefault(
                                        currentForegroundPackage!!,
                                        0L
                                    ) + duration
                            }
                            currentForegroundPackage = null
                        }
                    }
                }
            }

            if (currentForegroundPackage != null && foregroundStartTime >= start) {
                val duration = end - foregroundStartTime
                if (duration > 0) {
                    packageForegroundTime[currentForegroundPackage!!] =
                        packageForegroundTime.getOrDefault(
                            currentForegroundPackage!!,
                            0L
                        ) + duration
                }
            }
        }

        packageForegroundTime.forEach { (pkg, time) ->
            usageMap[pkg] = time
        }

        return usageMap.filterValues { it > 0L }
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

    fun getDailyUsage(usageStatsManager: UsageStatsManager, packageName: String): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        return fetchUsageInMs(usageStatsManager, startOfDay, now, packageName)[packageName] ?: 0L
    }

    fun getTodayUsageMap(usageStatsManager: UsageStatsManager): Map<String, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return fetchUsageInMs(usageStatsManager, cal.timeInMillis, System.currentTimeMillis())
    }
}
