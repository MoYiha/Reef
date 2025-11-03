package dev.pranav.reef.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.pranav.reef.util.RoutineManager

/**
 * BroadcastReceiver that handles scheduled routine activation/deactivation events.
 */
class RoutineActivationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val isActivation = intent.getBooleanExtra(EXTRA_IS_ACTIVATION, true)

        val routine = RoutineManager.getRoutines().find { it.id == routineId }
        if (routine == null || !routine.isEnabled) {
            Log.w(TAG, "Routine $routineId not found or disabled")
            return
        }

        if (isActivation) {
            RoutineExecutor.activateRoutine(context, routine)

            // Schedule next occurrence if recurring
            if (routine.schedule.isRecurring) {
                RoutineScheduler.scheduleActivation(context, routine)
            }
        } else {
            RoutineExecutor.deactivateRoutine(context, routine)

            // Schedule next occurrence if recurring
            if (routine.schedule.isRecurring) {
                RoutineScheduler.scheduleRoutine(context, routine)
            }
        }
    }

    companion object {
        private const val TAG = "RoutineActivationReceiver"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_IS_ACTIVATION = "is_activation"
    }
}

