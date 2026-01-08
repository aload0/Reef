package dev.pranav.reef.routine

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.ScreenUsageHelper
import dev.pranav.reef.util.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

/**
 * Single, simple routine system. Supports MULTIPLE active routines simultaneously.
 */
object Routines {
    private const val TAG = "Routines"
    private const val ROUTINES_KEY = "routines"
    private const val ACTIVE_SESSIONS_KEY = "active_routine_sessions"

    const val ACTION_CHANGED = "dev.pranav.reef.ROUTINE_CHANGED"

    data class ActiveSession(
        val routineId: String,
        val startTime: Long,
        val limits: Map<String, Long>
    )

    fun getAll(): List<Routine> {
        val json = prefs.getString(ROUTINES_KEY, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).mapNotNull { parseRoutine(arr.getJSONObject(it)) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Routine? = getAll().find { it.id == id }

    fun save(routine: Routine, context: Context) {
        val routines = getAll().toMutableList()
        val index = routines.indexOfFirst { it.id == routine.id }

        if (index >= 0) routines[index] = routine
        else routines.add(routine)

        saveAll(routines)

        // If this routine has an active session, update its limits
        val sessions = getActiveSessions().toMutableList()
        val sessionIndex = sessions.indexOfFirst { it.routineId == routine.id }
        if (sessionIndex >= 0) {
            val oldSession = sessions[sessionIndex]
            val newLimits = routine.limits.associate { it.packageName to it.limitMinutes * 60_000L }
            sessions[sessionIndex] = oldSession.copy(limits = newLimits)
            saveActiveSessions(sessions)
            broadcast(context)
        }
    }

    fun saveAll(routines: List<Routine>, context: Context) {
        saveAll(routines)
        broadcast(context)
    }

    fun delete(id: String, context: Context) {
        stopSession(context, id)

        val routines = getAll().filterNot { it.id == id }
        saveAll(routines)
    }

    fun toggle(id: String, context: Context) {
        Log.d(TAG, "=== TOGGLE START ===")
        Log.d(TAG, "Toggle called for routine ID: $id")

        val routine = get(id)
        if (routine == null) {
            Log.e(TAG, "Routine not found: $id")
            return
        }

        val updated = routine.copy(isEnabled = !routine.isEnabled)

        Log.d(TAG, "Routine: ${routine.name}")
        Log.d(TAG, "Old state: isEnabled=${routine.isEnabled}")
        Log.d(TAG, "New state: isEnabled=${updated.isEnabled}")
        Log.d(TAG, "Schedule type: ${updated.schedule.type}")

        val routines = getAll().toMutableList()
        val index = routines.indexOfFirst { it.id == id }
        if (index >= 0) routines[index] = updated
        saveAll(routines)
        Log.d(TAG, "Saved updated routine to storage")

        val hasActiveSession = getActiveSessions().any { it.routineId == id }
        Log.d(TAG, "Has active session: $hasActiveSession")

        if (routine.isEnabled && hasActiveSession) {
            Log.d(TAG, "Branch: Deactivating (was ON, now OFF)")
            stopSession(context, id)

            if (updated.schedule.type != RoutineSchedule.ScheduleType.MANUAL) {
                RoutineScheduler.cancelRoutine(context, id)
            }
        } else if (updated.isEnabled) {
            // Turning ON
            when (updated.schedule.type) {
                RoutineSchedule.ScheduleType.MANUAL -> {
                    Log.d(TAG, "Branch: Activating manual routine")
                    startSession(context, updated)
                }

                RoutineSchedule.ScheduleType.DAILY, RoutineSchedule.ScheduleType.WEEKLY -> {
                    val shouldBeActiveNow = RoutineScheduler.isRoutineActiveNow(updated)
                    Log.d(
                        TAG,
                        "Branch: Scheduled routine, should be active now: $shouldBeActiveNow"
                    )

                    if (shouldBeActiveNow) {
                        startSession(context, updated)
                    }

                    RoutineScheduler.scheduleRoutine(context, updated)
                }
            }
        } else {
            Log.d(TAG, "Branch: No action")
        }

        Log.d(TAG, "=== TOGGLE END ===")
    }

    private fun saveAll(routines: List<Routine>) {
        val json = JSONArray().apply {
            routines.forEach { put(routineToJson(it)) }
        }
        prefs.edit { putString(ROUTINES_KEY, json.toString()) }
    }

    fun startSession(context: Context, routine: Routine) {
        Log.d(TAG, "Starting session for: ${routine.name}")

        val sessions = getActiveSessions().toMutableList()

        // Remove existing session for this routine if any
        sessions.removeAll { it.routineId == routine.id }

        val newSession = ActiveSession(
            routineId = routine.id,
            startTime = System.currentTimeMillis(),
            limits = routine.limits.associate { it.packageName to it.limitMinutes * 60_000L }
        )
        sessions.add(newSession)

        saveActiveSessions(sessions)

        Log.d(TAG, "Started session for ${routine.name} with ${routine.limits.size} limits")
        Log.d(TAG, "Total active sessions: ${sessions.size}")

        broadcast(context)
        NotificationHelper.showRoutineActivatedNotification(context, routine)
    }

    fun stopSession(context: Context, routineId: String) {
        Log.d(TAG, "Stopping session for routine: $routineId")

        val routine = get(routineId)
        val sessions = getActiveSessions().toMutableList()
        val removed = sessions.removeAll { it.routineId == routineId }

        if (removed) {
            saveActiveSessions(sessions)
            Log.d(TAG, "Stopped session. Remaining active sessions: ${sessions.size}")

            broadcast(context)
            routine?.let { NotificationHelper.showRoutineDeactivatedNotification(context, it) }
        } else {
            Log.d(TAG, "No active session found for routine: $routineId")
        }
    }

    fun getActiveSessions(): List<ActiveSession> {
        val json = prefs.getString(ACTIVE_SESSIONS_KEY, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).mapNotNull {
                    try {
                        val obj = arr.getJSONObject(it)
                        val limitsJson = obj.getJSONObject("limits")
                        val limits = mutableMapOf<String, Long>()
                        limitsJson.keys().forEach { key ->
                            limits[key] = limitsJson.getLong(key)
                        }

                        ActiveSession(
                            routineId = obj.getString("routineId"),
                            startTime = obj.getLong("startTime"),
                            limits = limits
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveActiveSessions(sessions: List<ActiveSession>) {
        val json = JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject().apply {
                    put("routineId", session.routineId)
                    put("startTime", session.startTime)
                    put("limits", JSONObject().apply {
                        session.limits.forEach { (pkg, limit) ->
                            put(pkg, limit)
                        }
                    })
                })
            }
        }
        prefs.edit { putString(ACTIVE_SESSIONS_KEY, json.toString()) }
    }

    /**
     * Get the strictest limit for a package across ALL active routines.
     * Returns null if no routine is limiting this package.
     */
    fun getLimitMs(packageName: String): Long? {
        val sessions = getActiveSessions()

        if (sessions.isEmpty()) {
            Log.d(TAG, "getLimitMs($packageName): No active sessions")
            return null
        }

        Log.d(TAG, "getLimitMs($packageName): Checking ${sessions.size} active sessions")

        var strictestLimit: Long? = null

        sessions.forEach { session ->
            val routine = get(session.routineId)
            if (routine == null) {
                Log.d(TAG, "  Session routine ${session.routineId} not found, skipping")
                return@forEach
            }

            val maxDuration = RoutineScheduler.getMaxRoutineDuration(routine.schedule)
            if (System.currentTimeMillis() - session.startTime > maxDuration) {
                Log.d(TAG, "  Session for ${routine.name} expired")
                return@forEach
            }

            // Check if this session has a limit for this package
            val limit = session.limits[packageName]
            if (limit != null) {
                Log.d(TAG, "  ${routine.name} limits $packageName to ${limit}ms")

                // Take the strictest (lowest) limit
                strictestLimit = if (strictestLimit == null) limit else minOf(strictestLimit, limit)
            }
        }

        Log.d(TAG, "getLimitMs($packageName): strictest limit = $strictestLimit")
        return strictestLimit
    }

    /**
     * Get usage for a package during active routine sessions.
     * Returns the maximum usage across all active sessions (most restrictive).
     */
    fun getUsageMs(context: Context, packageName: String): Long {
        val sessions = getActiveSessions()
        if (sessions.isEmpty()) return 0L

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as android.app.usage.UsageStatsManager

        // For each session that limits this package, calculate usage since its start
        var maxUsage = 0L

        sessions.forEach { session ->
            if (session.limits.containsKey(packageName)) {
                val usage = ScreenUsageHelper.fetchUsageInMs(
                    usm, session.startTime, System.currentTimeMillis(), packageName
                )[packageName] ?: 0L

                if (usage > maxUsage) {
                    maxUsage = usage
                }
            }
        }

        return maxUsage
    }

    private fun broadcast(context: Context) {
        val sessions = getActiveSessions()
        val sessionsJson = JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject().apply {
                    put("routineId", session.routineId)
                    put("startTime", session.startTime)
                    put("limits", JSONObject().apply {
                        session.limits.forEach { (pkg, limit) ->
                            put(pkg, limit)
                        }
                    })
                })
            }
        }.toString()

        context.sendBroadcast(Intent(ACTION_CHANGED).apply {
            setPackage(context.packageName)
            putExtra("sessions", sessionsJson)
        })
    }

    fun createDefaults(): List<Routine> = listOf(
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

    private fun parseRoutine(json: JSONObject): Routine? = try {
        val scheduleJson = json.getJSONObject("schedule")
        val schedule = RoutineSchedule(
            type = RoutineSchedule.ScheduleType.valueOf(scheduleJson.getString("type")),
            timeHour = scheduleJson.optInt("timeHour").takeIf { scheduleJson.has("timeHour") },
            timeMinute = scheduleJson.optInt("timeMinute")
                .takeIf { scheduleJson.has("timeMinute") },
            endTimeHour = scheduleJson.optInt("endTimeHour")
                .takeIf { scheduleJson.has("endTimeHour") },
            endTimeMinute = scheduleJson.optInt("endTimeMinute")
                .takeIf { scheduleJson.has("endTimeMinute") },
            daysOfWeek = scheduleJson.optJSONArray("daysOfWeek")?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    try {
                        DayOfWeek.valueOf(arr.getString(it))
                    } catch (_: Exception) {
                        null
                    }
                }.toSet()
            } ?: emptySet(),
            isRecurring = scheduleJson.optBoolean("isRecurring", true)
        )

        val limits = json.getJSONArray("limits").let { arr ->
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { limitJson ->
                    Routine.AppLimit(
                        packageName = limitJson.getString("packageName"),
                        limitMinutes = limitJson.getInt("limitMinutes")
                    )
                }
            }
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

    private fun routineToJson(routine: Routine) = JSONObject().apply {
        put("id", routine.id)
        put("name", routine.name)
        put("isEnabled", routine.isEnabled)

        put("schedule", JSONObject().apply {
            val s = routine.schedule
            put("type", s.type.name)
            s.timeHour?.let { put("timeHour", it) }
            s.timeMinute?.let { put("timeMinute", it) }
            s.endTimeHour?.let { put("endTimeHour", it) }
            s.endTimeMinute?.let { put("endTimeMinute", it) }
            put("daysOfWeek", JSONArray().apply {
                s.daysOfWeek.forEach { put(it.name) }
            })
            put("isRecurring", s.isRecurring)
        })

        put("limits", JSONArray().apply {
            routine.limits.forEach { limit ->
                put(JSONObject().apply {
                    put("packageName", limit.packageName)
                    put("limitMinutes", limit.limitMinutes)
                })
            }
        })
    }
}
