package dev.pranav.reef.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MindfulLaunchTargetEvaluatorTest {
    @Test
    fun googleAppOnlyMapsWhenGeminiAssistantActivityIsPresent() {
        assertEquals(
            "com.google.android.apps.bard",
            MindfulLaunchTargetEvaluator.getTargetPackage(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
            )
        )

        assertEquals(
            "com.google.android.googlequicksearchbox",
            MindfulLaunchTargetEvaluator.getTargetPackage(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.search.launcher.SearchActivity"
            )
        )
    }

    @Test
    fun bardPackageIsPreserved() {
        assertEquals(
            "com.google.android.apps.bard",
            MindfulLaunchTargetEvaluator.getTargetPackage(
                packageName = "com.google.android.apps.bard",
                className = "com.google.android.apps.bard.MainActivity"
            )
        )
    }
}
