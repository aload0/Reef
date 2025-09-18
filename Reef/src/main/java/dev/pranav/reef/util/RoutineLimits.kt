package dev.pranav.reef.util

import android.util.Log
import androidx.core.content.edit

object RoutineLimits {
    private const val ROUTINE_LIMITS_KEY = "routine_limits"
    private const val ACTIVE_ROUTINE_KEY = "active_routine_id"
    private val routineLimits = mutableMapOf<String, Long>()

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

        // Mark this routine as active
        prefs.edit { putString(ACTIVE_ROUTINE_KEY, routineId) }
        Log.d("RoutineLimits", "Marked routine $routineId as active")
    }

    fun clearRoutineLimits() {
        Log.d("RoutineLimits", "Clearing all routine limits")
        routineLimits.clear()
        saveRoutineLimits()
        prefs.edit { remove(ACTIVE_ROUTINE_KEY) }
    }

    fun getRoutineLimit(packageName: String): Long {
        val limit = routineLimits[packageName] ?: 0L
        Log.d("RoutineLimits", "Getting routine limit for $packageName: $limit ms")
        return limit
    }

    fun hasRoutineLimit(packageName: String): Boolean {
        val hasLimit = routineLimits.containsKey(packageName)
        Log.d("RoutineLimits", "Checking routine limit for $packageName: $hasLimit")
        return hasLimit
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

        allPrefs.forEach { (key, value) ->
            if (key.startsWith("routine_limit_") && value is Long) {
                val packageName = key.removePrefix("routine_limit_")
                routineLimits[packageName] = value
            }
        }
    }
}
