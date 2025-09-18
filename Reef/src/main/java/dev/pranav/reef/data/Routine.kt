package dev.pranav.reef.data

import java.time.DayOfWeek
import java.time.LocalTime

data class Routine(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val schedule: RoutineSchedule,
    val limits: List<AppLimit>
) {
    data class AppLimit(
        val packageName: String,
        val limitMinutes: Int
    )
}

data class RoutineSchedule(
    val type: ScheduleType,
    val timeHour: Int? = null,
    val timeMinute: Int? = null,
    val endTimeHour: Int? = null,
    val endTimeMinute: Int? = null,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val isRecurring: Boolean = true
) {
    enum class ScheduleType {
        DAILY,
        WEEKLY,
        MANUAL
    }

    // Computed property to get LocalTime from hour/minute
    val time: LocalTime?
        get() = if (timeHour != null && timeMinute != null) {
            LocalTime.of(timeHour, timeMinute)
        } else null

    // Computed property to get end LocalTime from hour/minute
    val endTime: LocalTime?
        get() = if (endTimeHour != null && endTimeMinute != null) {
            LocalTime.of(endTimeHour, endTimeMinute)
        } else null
}
