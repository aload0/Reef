package dev.pranav.reef.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dev.pranav.reef.accessibility.BlockerService

object ServiceHealthChecker {
    fun isAccessibilitySettingEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, BlockerService::class.java).flattenToString()
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return settingValue
            .split(":")
            .map { it.trim() }
            .any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    fun isServiceDesynced(context: Context): Boolean {
        return !BlockerService.isConnected && isAccessibilitySettingEnabled(context)
    }
}
