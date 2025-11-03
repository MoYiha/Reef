package dev.pranav.reef.routine

import android.content.Context
import android.util.Log
import dev.pranav.reef.data.Routine
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.RoutineLimits

/**
 * Handles the actual execution of routine activation and deactivation.
 * This is the single source of truth for what happens when a routine starts or stops.
 */
object RoutineExecutor {
    private const val TAG = "RoutineExecutor"

    fun activateRoutine(context: Context, routine: Routine) {
        Log.d(TAG, "Activating routine: ${routine.name}")

        val limitsMap = routine.limits.associate { it.packageName to it.limitMinutes }
        RoutineLimits.setRoutineLimits(limitsMap, routine.id)

        NotificationHelper.showRoutineActivatedNotification(context, routine)
    }

    fun deactivateRoutine(context: Context, routine: Routine) {
        Log.d(TAG, "Deactivating routine: ${routine.name}")

        RoutineLimits.clearRoutineLimits()

        NotificationHelper.showRoutineDeactivatedNotification(context, routine)
    }
}

