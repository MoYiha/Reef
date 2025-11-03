package dev.pranav.reef.util

import android.content.Context
import androidx.core.content.edit
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.routine.RoutineScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

object RoutineManager {
    private const val ROUTINES_KEY = "routines"

    fun getRoutines(): List<Routine> {
        val json = prefs.getString(ROUTINES_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            val routines = mutableListOf<Routine>()

            for (i in 0 until jsonArray.length()) {
                val routineJson = jsonArray.getJSONObject(i)
                val routine = parseRoutine(routineJson)
                if (routine != null) {
                    routines.add(routine)
                }
            }
            routines
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRoutine(json: JSONObject): Routine? {
        return try {
            val scheduleJson = json.getJSONObject("schedule")
            val schedule = RoutineSchedule(
                type = RoutineSchedule.ScheduleType.valueOf(scheduleJson.getString("type")),
                timeHour = if (scheduleJson.has("timeHour")) scheduleJson.getInt("timeHour") else null,
                timeMinute = if (scheduleJson.has("timeMinute")) scheduleJson.getInt("timeMinute") else null,
                endTimeHour = if (scheduleJson.has("endTimeHour")) scheduleJson.getInt("endTimeHour") else null,
                endTimeMinute = if (scheduleJson.has("endTimeMinute")) scheduleJson.getInt("endTimeMinute") else null,
                daysOfWeek = parseDaysOfWeek(scheduleJson.optJSONArray("daysOfWeek")),
                isRecurring = scheduleJson.optBoolean("isRecurring", true)
            )

            val limitsArray = json.getJSONArray("limits")
            val limits = mutableListOf<Routine.AppLimit>()
            for (i in 0 until limitsArray.length()) {
                val limitJson = limitsArray.getJSONObject(i)
                limits.add(
                    Routine.AppLimit(
                        packageName = limitJson.getString("packageName"),
                        limitMinutes = limitJson.getInt("limitMinutes")
                    )
                )
            }

            Routine(
                id = json.getString("id"),
                name = json.getString("name"),
                isEnabled = json.getBoolean("isEnabled"),
                schedule = schedule,
                limits = limits
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDaysOfWeek(jsonArray: JSONArray?): Set<DayOfWeek> {
        if (jsonArray == null) return emptySet()
        val days = mutableSetOf<DayOfWeek>()
        for (i in 0 until jsonArray.length()) {
            try {
                days.add(DayOfWeek.valueOf(jsonArray.getString(i)))
            } catch (_: Exception) {
                // Skip invalid days
            }
        }
        return days
    }

    fun saveRoutines(routines: List<Routine>, context: Context? = null) {
        val jsonArray = JSONArray()
        routines.forEach { routine ->
            jsonArray.put(routineToJson(routine))
        }
        prefs.edit { putString(ROUTINES_KEY, jsonArray.toString()) }

        context?.let { ctx ->
            RoutineScheduler.scheduleAllRoutines(ctx)
        }
    }

    private fun routineToJson(routine: Routine): JSONObject {
        return JSONObject().apply {
            put("id", routine.id)
            put("name", routine.name)
            put("isEnabled", routine.isEnabled)

            val schedule = routine.schedule
            val scheduleJson = JSONObject().apply {
                put("type", schedule.type.name)
                schedule.timeHour?.let { put("timeHour", it) }
                schedule.timeMinute?.let { put("timeMinute", it) }
                schedule.endTimeHour?.let { put("endTimeHour", it) }
                schedule.endTimeMinute?.let { put("endTimeMinute", it) }

                val daysArray = JSONArray()
                schedule.daysOfWeek.forEach { daysArray.put(it.name) }
                put("daysOfWeek", daysArray)

                put("isRecurring", schedule.isRecurring)
            }
            put("schedule", scheduleJson)

            val limitsArray = JSONArray()
            routine.limits.forEach { limit ->
                limitsArray.put(JSONObject().apply {
                    put("packageName", limit.packageName)
                    put("limitMinutes", limit.limitMinutes)
                })
            }
            put("limits", limitsArray)
        }
    }

    fun addRoutine(routine: Routine, context: Context? = null) {
        val routines = getRoutines().toMutableList()
        routines.add(routine)
        saveRoutines(routines, context)
    }

    fun updateRoutine(updatedRoutine: Routine, context: Context? = null) {
        val routines = getRoutines().toMutableList()
        val index = routines.indexOfFirst { it.id == updatedRoutine.id }
        if (index != -1) {
            routines[index] = updatedRoutine
            saveRoutines(routines, context)
        }
    }

    fun deleteRoutine(routineId: String, context: Context? = null) {
        context?.let { ctx ->
            RoutineScheduler.cancelRoutine(ctx, routineId)
        }
        val routines = getRoutines().toMutableList()
        routines.removeAll { it.id == routineId }
        saveRoutines(routines, context)
    }

    fun toggleRoutine(routineId: String, context: Context? = null) {
        val routines = getRoutines().toMutableList()
        val index = routines.indexOfFirst { it.id == routineId }
        if (index != -1) {
            val oldRoutine = routines[index]
            val newRoutine = oldRoutine.copy(isEnabled = !oldRoutine.isEnabled)
            routines[index] = newRoutine

            context?.let { ctx ->
                if (oldRoutine.isEnabled) {
                    // Toggling OFF
                    if (oldRoutine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
                        // For manual routines, just deactivate
                        val activeRoutineId = RoutineLimits.getActiveRoutineId()
                        if (activeRoutineId == routineId) {
                            RoutineLimits.clearRoutineLimits()
                            NotificationHelper.showRoutineDeactivatedNotification(ctx, oldRoutine)
                        }
                    } else {
                        // For scheduled routines, cancel alarms and clear if active
                        RoutineScheduler.cancelRoutine(ctx, routineId)
                        val activeRoutineId = RoutineLimits.getActiveRoutineId()
                        if (activeRoutineId == routineId) {
                            RoutineLimits.clearRoutineLimits()
                        }
                    }
                } else {
                    // Toggling ON
                    if (newRoutine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
                        // For manual routines, activate immediately
                        dev.pranav.reef.routine.RoutineExecutor.activateRoutine(ctx, newRoutine)
                    } else {
                        // For scheduled routines, schedule alarms
                        RoutineScheduler.scheduleRoutine(ctx, newRoutine)
                    }
                }
            }

            saveRoutines(routines, context)
        }
    }

    fun createDefaultRoutines(): List<Routine> {
        return listOf(
            Routine(
                id = UUID.randomUUID().toString(),
                name = "Weekend Digital Detox",
                isEnabled = false,
                schedule = RoutineSchedule(
                    type = RoutineSchedule.ScheduleType.WEEKLY,
                    timeHour = 9,
                    timeMinute = 0,
                    endTimeHour = 18,
                    endTimeMinute = 0,
                    daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                ),
                limits = emptyList()
            ),
            Routine(
                id = UUID.randomUUID().toString(),
                name = "Workday Focus",
                isEnabled = false,
                schedule = RoutineSchedule(
                    type = RoutineSchedule.ScheduleType.WEEKLY,
                    timeHour = 9,
                    timeMinute = 0,
                    endTimeHour = 17,
                    endTimeMinute = 0,
                    daysOfWeek = setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                ),
                limits = emptyList()
            )
        )
    }
}
