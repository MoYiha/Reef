package dev.pranav.reef.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import java.util.Date

/**
 * Manages scheduling of routines using Android's AlarmManager.
 * Handles scheduling activation and deactivation alarms.
 */
object RoutineScheduler {
    private const val TAG = "RoutineScheduler"

    /**
     * Schedule all enabled routines.
     */
    fun scheduleAllRoutines(context: Context) {
        val routines = dev.pranav.reef.util.RoutineManager.getRoutines().filter { it.isEnabled }
        routines.forEach { routine ->
            scheduleRoutine(context, routine)
        }
    }

    /**
     * Schedule a single routine. Determines if it should be active now,
     * and schedules appropriate activation/deactivation alarms.
     */
    fun scheduleRoutine(context: Context, routine: Routine) {
        if (!routine.isEnabled || routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
            return
        }

        if (RoutineScheduleCalculator.isRoutineActiveNow(routine)) {
            // Activate immediately and schedule deactivation
            RoutineExecutor.activateRoutine(context, routine)

            if (routine.schedule.endTime != null) {
                scheduleDeactivation(context, routine)
            }
        } else {
            // Schedule future activation and deactivation
            scheduleActivation(context, routine)

            if (routine.schedule.endTime != null) {
                scheduleDeactivation(context, routine)
            }
        }
    }

    /**
     * Schedule routine activation alarm.
     */
    fun scheduleActivation(context: Context, routine: Routine) {
        scheduleAlarm(context, routine, isActivation = true)
    }

    /**
     * Schedule routine deactivation alarm.
     */
    fun scheduleDeactivation(context: Context, routine: Routine) {
        scheduleAlarm(context, routine, isActivation = false)
    }

    /**
     * Cancel all alarms for a routine.
     */
    fun cancelRoutine(context: Context, routineId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel both activation and deactivation alarms
        listOf(true, false).forEach { isActivation ->
            val pendingIntent =
                createPendingIntent(context, routineId, isActivation, PendingIntent.FLAG_NO_CREATE)
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }

        Log.d(TAG, "Cancelled all alarms for routine: $routineId")
    }

    private fun scheduleAlarm(context: Context, routine: Routine, isActivation: Boolean) {
        val triggerTime = RoutineScheduleCalculator.calculateNextTriggerTime(
            routine.schedule,
            useStartTime = isActivation
        ) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(
            context,
            routine.id,
            isActivation,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
            ?: run {
                Log.e(TAG, "Failed to create PendingIntent for routine ${routine.name}")
                return
            }

        try {
            scheduleExactAlarm(alarmManager, triggerTime, pendingIntent)
            logScheduled(routine, isActivation, triggerTime)
        } catch (e: SecurityException) {
            scheduleInexactAlarm(alarmManager, triggerTime, pendingIntent)
            logScheduled(routine, isActivation, triggerTime, inexact = true)
        }
    }

    private fun scheduleExactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                scheduleInexactAlarm(alarmManager, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun scheduleInexactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    private fun createPendingIntent(
        context: Context,
        routineId: String,
        isActivation: Boolean,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, RoutineActivationReceiver::class.java).apply {
            putExtra(RoutineActivationReceiver.EXTRA_ROUTINE_ID, routineId)
            putExtra(RoutineActivationReceiver.EXTRA_IS_ACTIVATION, isActivation)
        }

        val requestCode = getRequestCode(routineId, isActivation)
        val allFlags = flags or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(context, requestCode, intent, allFlags)
    }

    private fun getRequestCode(routineId: String, isActivation: Boolean): Int {
        return if (isActivation) routineId.hashCode() else routineId.hashCode() + 1
    }

    private fun logScheduled(
        routine: Routine,
        isActivation: Boolean,
        triggerTime: Long,
        inexact: Boolean = false
    ) {
        val action = if (isActivation) "activation" else "deactivation"
        val exactness = if (inexact) " (inexact)" else ""
        Log.d(TAG, "Scheduled ${routine.name} $action$exactness for ${Date(triggerTime)}")
    }
}
