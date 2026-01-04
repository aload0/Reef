package dev.pranav.reef.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*

@Composable
fun SettingsContent(
    onSoundPicker: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<SettingsScreenRoute>(SettingsScreenRoute.Main) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState != SettingsScreenRoute.Main) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(100)
                ) + fadeIn(animationSpec = tween(100)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(100)
                        ) + fadeOut(animationSpec = tween(100))
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(100)
                ) + fadeIn(animationSpec = tween(100)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(100)
                        ) + fadeOut(animationSpec = tween(100))
            }
        },
        label = "settings_screen_transition"
    ) { screen ->
        when (screen) {
            SettingsScreenRoute.Main -> MainSettingsContent(
                onNavigate = { currentScreen = it }
            )

            SettingsScreenRoute.Pomodoro -> PomodoroSettingsContent(
                onBackPressed = { currentScreen = SettingsScreenRoute.Main },
                onSoundPicker = onSoundPicker
            )

            SettingsScreenRoute.Notifications -> NotificationSettingsContent(
                onBackPressed = { currentScreen = SettingsScreenRoute.Main }
            )
        }
    }
}
