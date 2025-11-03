package dev.pranav.reef.util

import android.app.usage.UsageStatsManager
import android.util.Log
import androidx.core.content.edit

object RoutineLimits {
    private const val ROUTINE_LIMITS_KEY = "routine_limits"
    private const val ACTIVE_ROUTINE_KEY = "active_routine_id"
    private const val ROUTINE_START_TIME_KEY = "routine_start_time"
    private val routineLimits = mutableMapOf<String, Long>()
    
    // Track when reminders were sent for routine limits
    private val routineReminderSentMap = mutableMapOf<String, Long>()
    private val routineGracePeriodStartMap = mutableMapOf<String, Long>()

    fun setRoutineLimits(limits: Map<String, Int>, routineId: String) {
        // Clear existing routine limits
        routineLimits.clear()

        Log.d("RoutineLimits", "Setting routine limits for routine: $routineId")

        // Set new limits (convert minutes to milliseconds)
        limits.forEach { (packageName, minutes) ->
            routineLimits[packageName] = minutes * 60 * 1000L
            Log.d(
                "RoutineLimits",
                "Set limit for $packageName: ${minutes}m (${minutes * 60 * 1000L}ms)"
            )
        }

        // Save to preferences
        saveRoutineLimits()

        // Mark this routine as active and record start time
        prefs.edit {
            putString(ACTIVE_ROUTINE_KEY, routineId)
            putLong(ROUTINE_START_TIME_KEY, System.currentTimeMillis())
        }
        Log.d(
            "RoutineLimits",
            "Marked routine $routineId as active, start time: ${System.currentTimeMillis()}"
        )
    }

    fun clearRoutineLimits() {
        Log.d("RoutineLimits", "Clearing all routine limits")
        routineLimits.clear()
        saveRoutineLimits()
        prefs.edit {
            remove(ACTIVE_ROUTINE_KEY)
            remove(ROUTINE_START_TIME_KEY)
        }
    }

    fun getRoutineLimit(packageName: String): Long {
        val limit = routineLimits[packageName] ?: 0L
        Log.d("RoutineLimits", "Getting routine limit for $packageName: $limit ms")
        return limit
    }

    fun hasRoutineLimit(packageName: String): Boolean {
        val hasLimit = routineLimits.containsKey(packageName)

        return hasLimit
    }

    fun getRoutineUsageTime(packageName: String, usageStatsManager: UsageStatsManager): Long {
        val routineStartTime = prefs.getLong(ROUTINE_START_TIME_KEY, 0L)
        if (routineStartTime == 0L) {
            Log.d("RoutineLimits", "No routine start time found, returning 0")
            return 0L
        }

        val endTime = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(routineStartTime, endTime)
        var totalUsage = 0L
        var lastResumeTime: Long? = null
        val event = android.app.usage.UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.packageName == packageName) {
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastResumeTime = event.timeStamp
                    }

                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val startTime = lastResumeTime
                        if (startTime != null) {
                            val duration = event.timeStamp - startTime
                            totalUsage += duration
                            lastResumeTime = null
                        }
                    }
                }
            }
        }

        lastResumeTime?.let {
            totalUsage += (endTime - it)
        }

        Log.d(
            "RoutineLimits",
            "Usage time for $packageName since routine start ($routineStartTime to $endTime): $totalUsage ms"
        )

        return totalUsage
    }

    fun getRoutineLimits(): Map<String, Long> {
        return routineLimits.toMap()
    }

    fun getActiveRoutineId(): String? {
        return prefs.getString(ACTIVE_ROUTINE_KEY, null)
    }

    fun isRoutineActive(): Boolean {
        return getActiveRoutineId() != null && routineLimits.isNotEmpty()
    }

    private fun saveRoutineLimits() {
        // Clear existing routine limits
        val keys = prefs.all.keys.filter { it.startsWith("routine_limit_") }
        prefs.edit {
            keys.forEach { remove(it) }
        }

        // Save new limits
        prefs.edit {
            routineLimits.forEach { (packageName, limit) ->
                putLong("routine_limit_$packageName", limit)
            }
        }
    }

    fun loadRoutineLimits() {
        routineLimits.clear()
        val allPrefs = prefs.all

        Log.d("RoutineLimits", "Loading routine limits from preferences...")

        allPrefs.forEach { (key, value) ->
            if (key.startsWith("routine_limit_") && value is Long) {
                val packageName = key.removePrefix("routine_limit_")
                routineLimits[packageName] = value
                Log.d("RoutineLimits", "Loaded limit for $packageName: $value ms")
            }
        }

        Log.d("RoutineLimits", "Loaded ${routineLimits.size} routine limits")

        validateRoutineSession()
    }

    /**
     * Validates that the stored routine session is still valid.
     * Clears limits if the routine is stale or no longer valid.
     */
    private fun validateRoutineSession() {
        val routineStartTime = prefs.getLong(ROUTINE_START_TIME_KEY, 0L)
        val activeRoutineId = prefs.getString(ACTIVE_ROUTINE_KEY, null)

        if (routineStartTime == 0L || activeRoutineId == null) {
            Log.d("RoutineLimits", "No active routine session to validate")
            return
        }

        Log.d("RoutineLimits", "Validating routine session for routine: $activeRoutineId")

        val routine = RoutineManager.getRoutines().find { it.id == activeRoutineId }

        if (routine == null || !routine.isEnabled) {
            Log.w(
                "RoutineLimits",
                "Active routine not found or disabled, clearing routine limits"
            )
            clearRoutineLimits()
            return
        }

        val timeSinceStart = System.currentTimeMillis() - routineStartTime
        val maxDuration =
            dev.pranav.reef.routine.RoutineScheduleCalculator.getMaxRoutineDuration(routine.schedule)

        if (timeSinceStart > maxDuration) {
            Log.w(
                "RoutineLimits",
                "Routine start time is from a previous session (${timeSinceStart}ms old, max: ${maxDuration}ms), clearing"
            )
            clearRoutineLimits()
        } else {
            Log.d(
                "RoutineLimits",
                "Routine session is valid (${timeSinceStart}ms old, max: ${maxDuration}ms)"
            )
        }
    }
    
    fun hasRoutineReminderBeenSent(packageName: String): Boolean {
        val lastSent = routineReminderSentMap[packageName] ?: return false
        val routineStartTime = prefs.getLong(ROUTINE_START_TIME_KEY, 0L)
        // Reminder is valid if sent during this routine session
        return lastSent >= routineStartTime
    }
    
    fun markRoutineReminderSent(packageName: String) {
        routineReminderSentMap[packageName] = System.currentTimeMillis()
    }
    
    fun clearRoutineReminderSent(packageName: String) {
        routineReminderSentMap.remove(packageName)
    }
    
    fun isInRoutineGracePeriod(packageName: String): Boolean {
        val graceStart = routineGracePeriodStartMap[packageName] ?: return false
        val elapsed = System.currentTimeMillis() - graceStart
        return elapsed < GRACE_PERIOD_MS
    }
    
    fun startRoutineGracePeriod(packageName: String) {
        routineGracePeriodStartMap[packageName] = System.currentTimeMillis()
    }
    
    fun hasRoutineGracePeriodStarted(packageName: String): Boolean {
        val graceStart = routineGracePeriodStartMap[packageName] ?: return false
        val routineStartTime = prefs.getLong(ROUTINE_START_TIME_KEY, 0L)
        // Grace period is valid if started during this routine session
        return graceStart >= routineStartTime
    }
    
    fun clearRoutineGracePeriod(packageName: String) {
        routineGracePeriodStartMap.remove(packageName)
    }
    
    fun getRemainingRoutineGracePeriod(packageName: String): Long {
        val graceStart = routineGracePeriodStartMap[packageName] ?: return 0L
        val elapsed = System.currentTimeMillis() - graceStart
        val remaining = GRACE_PERIOD_MS - elapsed
        return if (remaining > 0) remaining else 0L
    }
}
