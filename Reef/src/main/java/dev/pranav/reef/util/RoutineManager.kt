package dev.pranav.reef.util

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import java.time.DayOfWeek
import java.util.UUID

object RoutineManager {
    private const val ROUTINES_KEY = "routines"
    private val gson = Gson() // Simple Gson instance - no custom adapters needed

    fun getRoutines(): List<Routine> {
        val json = prefs.getString(ROUTINES_KEY, "[]") ?: "[]"
        val type = object : TypeToken<List<Routine>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveRoutines(routines: List<Routine>, context: Context? = null) {
        val json = gson.toJson(routines)
        prefs.edit { putString(ROUTINES_KEY, json) }

        // Reschedule all routines when saved
        context?.let { ctx ->
            RoutineScheduler.scheduleAllRoutines(ctx)
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
            routines[index] = oldRoutine.copy(isEnabled = !oldRoutine.isEnabled)

            context?.let { ctx ->
                if (oldRoutine.isEnabled) {
                    // Was enabled, now disabled - cancel scheduling
                    RoutineScheduler.cancelRoutine(ctx, routineId)
                } else {
                    // Was disabled, now enabled - schedule it
                    RoutineScheduler.scheduleRoutine(ctx, routines[index])
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
