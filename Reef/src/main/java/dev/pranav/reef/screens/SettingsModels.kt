package dev.pranav.reef.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

sealed class SettingsScreenRoute {
    data object Main: SettingsScreenRoute()
    data object Pomodoro: SettingsScreenRoute()
    data object Notifications: SettingsScreenRoute()
}

data class SettingsMenuItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val destination: SettingsScreenRoute,
    val tint: Color? = null
)

data class NumberSetting(
    val label: String,
    val value: Int,
    val range: IntRange,
    val suffix: String,
    val onValueChange: (Int) -> Unit
)
