package dev.pranav.reef.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Home: Screen()

    @Serializable
    data object Timer: Screen()

    @Serializable
    data object Usage: Screen()

    @Serializable
    data class DailyLimit(val packageName: String): Screen()

    @Serializable
    data object Routines: Screen()

    @Serializable
    data class CreateRoutine(val routineId: String? = null): Screen()

    @Serializable
    data object Whitelist: Screen()

    @Serializable
    data object Settings: Screen()

    @Serializable
    data object Intro: Screen()
}
