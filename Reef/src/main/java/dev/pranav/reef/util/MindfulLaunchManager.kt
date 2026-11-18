package dev.pranav.reef.util

import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MindfulLaunchManager {

    private const val PHONE_UNLOCK_KEY = "mindful_launch_phone_unlock"

    fun isEnabled(): Boolean {
        return prefs.getBoolean("mindful_launch_enabled", false)
    }

    fun getDurationSeconds(pkg: String? = null): Int {
        val defaultDuration = prefs.getInt("mindful_launch_duration", 10)
        val appKey = pkg?.let(::durationKey)
        return if (appKey != null && prefs.contains(appKey)) {
            prefs.getInt(appKey, defaultDuration)
        } else {
            defaultDuration
        }
    }

    fun getWarningMessage(pkg: String? = null): String {
        val defaultMessage = prefs.getString("mindful_launch_warning", "") ?: ""
        val appKey = pkg?.let(::warningMessageKey)
        return if (appKey != null && prefs.contains(appKey)) {
            prefs.getString(appKey, defaultMessage) ?: defaultMessage
        } else {
            defaultMessage
        }
    }

    fun isLimitEnabled(pkg: String? = null): Boolean {
        val defaultEnabled = prefs.getBoolean("mindful_launch_limit_enabled", false)
        val appKey = pkg?.let(::limitEnabledKey)
        return if (appKey != null && prefs.contains(appKey)) {
            prefs.getBoolean(appKey, defaultEnabled)
        } else {
            defaultEnabled
        }
    }

    fun getLimitCount(pkg: String? = null): Int {
        val defaultLimit = prefs.getInt("mindful_launch_limit_count", 5)
        val appKey = pkg?.let(::limitCountKey)
        return if (appKey != null && prefs.contains(appKey)) {
            prefs.getInt(appKey, defaultLimit)
        } else {
            defaultLimit
        }
    }

    fun hasAppOverrides(pkg: String): Boolean {
        return prefs.contains(durationKey(pkg))
    }

    fun setAppOverrides(
        pkg: String,
        durationSeconds: Int,
        warningMessage: String,
        limitEnabled: Boolean,
        limitCount: Int
    ) {
        prefs.edit {
            putInt(durationKey(pkg), durationSeconds.coerceIn(5, 300))
            putString(warningMessageKey(pkg), warningMessage)
            putBoolean(limitEnabledKey(pkg), limitEnabled)
            putInt(limitCountKey(pkg), limitCount.coerceIn(1, 100))
        }
    }

    fun clearAppOverrides(pkg: String) {
        prefs.edit {
            remove(durationKey(pkg))
            remove(warningMessageKey(pkg))
            remove(limitEnabledKey(pkg))
            remove(limitCountKey(pkg))
        }
    }

    fun getMindfulApps(): Set<String> {
        return prefs.getStringSet("mindful_launch_apps", emptySet()) ?: emptySet()
    }

    fun setMindfulApps(apps: Set<String>) {
        prefs.edit {
            putStringSet("mindful_launch_apps", apps)
        }
    }

    fun isMindfulApp(pkg: String): Boolean {
        return getMindfulApps().contains(pkg)
    }

    fun isPhoneUnlockEnabled(): Boolean {
        return prefs.getBoolean(PHONE_UNLOCK_KEY, false)
    }

    fun setPhoneUnlockEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(PHONE_UNLOCK_KEY, enabled)
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun getDailyLaunchCount(pkg: String): Int {
        val dateStr = getTodayDateString()
        return prefs.getInt("mindful_launch_count_${pkg}_${dateStr}", 0)
    }

    fun incrementDailyLaunchCount(pkg: String) {
        val dateStr = getTodayDateString()
        val currentCount = getDailyLaunchCount(pkg)
        prefs.edit {
            putInt("mindful_launch_count_${pkg}_${dateStr}", currentCount + 1)
        }
    }

    fun isLaunchLimitReached(pkg: String): Boolean {
        if (!isLimitEnabled(pkg)) return false
        return getDailyLaunchCount(pkg) >= getLimitCount(pkg)
    }

    fun isCurrentlyUnlocked(pkg: String): Boolean {
        val unlockedUntil = prefs.getLong("mindful_launch_unlocked_until_$pkg", 0L)
        return System.currentTimeMillis() < unlockedUntil
    }

    fun unlockApp(pkg: String, durationMinutes: Int) {
        val unlockedUntil = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        prefs.edit {
            putLong("mindful_launch_unlocked_until_$pkg", unlockedUntil)
        }
        incrementDailyLaunchCount(pkg)
    }

    private fun durationKey(pkg: String) = "mindful_launch_duration_$pkg"

    private fun warningMessageKey(pkg: String) = "mindful_launch_warning_$pkg"

    private fun limitEnabledKey(pkg: String) = "mindful_launch_limit_enabled_$pkg"

    private fun limitCountKey(pkg: String) = "mindful_launch_limit_count_$pkg"
}
