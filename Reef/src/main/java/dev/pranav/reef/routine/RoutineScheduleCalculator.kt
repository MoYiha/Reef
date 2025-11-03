package dev.pranav.reef.routine

import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Calculates schedule times for routines - when they should activate and deactivate.
 */
object RoutineScheduleCalculator {

    /**
     * Checks if a routine should be active at the current moment.
     */
    fun isRoutineActiveNow(routine: Routine): Boolean {
        val now = LocalDateTime.now()
        val schedule = routine.schedule

        val startTime = schedule.time ?: return false
        val endTime = schedule.endTime ?: return false

        val todayStart = now.withHour(startTime.hour).withMinute(startTime.minute).withSecond(0)
        val todayEnd = now.withHour(endTime.hour).withMinute(endTime.minute).withSecond(0)

        return when (schedule.type) {
            RoutineSchedule.ScheduleType.DAILY -> {
                now.isAfter(todayStart) && now.isBefore(todayEnd)
            }

            RoutineSchedule.ScheduleType.WEEKLY -> {
                val currentDayOfWeek = now.dayOfWeek
                val isCorrectDay = schedule.daysOfWeek.contains(currentDayOfWeek)
                isCorrectDay && now.isAfter(todayStart) && now.isBefore(todayEnd)
            }

            RoutineSchedule.ScheduleType.MANUAL -> false
        }
    }

    /**
     * Calculates the next trigger time for a routine activation or deactivation.
     * Returns null if the routine can't be scheduled (e.g., manual routine).
     */
    fun calculateNextTriggerTime(schedule: RoutineSchedule, useStartTime: Boolean): Long? {
        val now = LocalDateTime.now()
        val time = if (useStartTime) schedule.time else schedule.endTime
        if (time == null) return null

        return when (schedule.type) {
            RoutineSchedule.ScheduleType.DAILY -> calculateDailyTriggerTime(now, time)
            RoutineSchedule.ScheduleType.WEEKLY -> calculateWeeklyTriggerTime(
                now,
                time,
                schedule.daysOfWeek
            )

            RoutineSchedule.ScheduleType.MANUAL -> null
        }
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

    /**
     * Calculates the maximum possible duration of a routine in milliseconds.
     * Used for validating if a routine session is stale.
     */
    fun getMaxRoutineDuration(schedule: RoutineSchedule): Long {
        val startTime = schedule.time
        val endTime = schedule.endTime

        if (startTime == null || endTime == null) {
            return 24 * 60 * 60 * 1000L // Default to 24 hours
        }

        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute

        val durationMinutes = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            // Handle overnight routines
            (24 * 60 - startMinutes) + endMinutes
        }

        return durationMinutes * 60 * 1000L
    }
}

