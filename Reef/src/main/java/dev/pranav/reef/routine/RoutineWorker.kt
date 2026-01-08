package dev.pranav.reef.routine

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

class RoutineWorker(
    private val context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!isPrefsInitialized) {
            prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }

        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()
        val isActivation = inputData.getBoolean(KEY_IS_ACTIVATION, true)

        val routine = Routines.get(routineId)
        if (routine == null || !routine.isEnabled) {
            Log.w(TAG, "Routine $routineId not found or disabled")
            return Result.success()
        }

        if (isActivation) {
            Routines.startSession(context, routine)

            if (routine.schedule.isRecurring) {
                RoutineScheduler.scheduleActivation(context, routine)
            }
        } else {
            Routines.stopSession(context, routineId)

            if (routine.schedule.isRecurring) {
                RoutineScheduler.scheduleRoutine(context, routine)
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "RoutineWorker"
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_IS_ACTIVATION = "is_activation"

        fun getActivationWorkName(routineId: String) = "routine_activation_$routineId"
        fun getDeactivationWorkName(routineId: String) = "routine_deactivation_$routineId"
    }
}
