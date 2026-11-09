package dev.pranav.reef.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulLaunchTargetEvaluatorTest {
    @Test
    fun googleAppOnlyTriggersForGeminiAssistantActivity() {
        assertTrue(
            MindfulLaunchTargetEvaluator.isExtraMindfulAppService(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
            )
        )

        assertFalse(
            MindfulLaunchTargetEvaluator.isExtraMindfulAppService(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.search.launcher.SearchActivity"
            )
        )
    }

    @Test
    fun bardIsRecognizedAndOtherAppsStayBlocked() {
        assertTrue(
            MindfulLaunchTargetEvaluator.isExtraMindfulAppService(
                packageName = "com.google.android.apps.bard",
                className = "com.google.android.apps.bard.MainActivity"
            )
        )

        assertFalse(
            MindfulLaunchTargetEvaluator.isExtraMindfulAppService(
                packageName = "com.example.app",
                className = "com.example.app.MainActivity"
            )
        )
    }
}
