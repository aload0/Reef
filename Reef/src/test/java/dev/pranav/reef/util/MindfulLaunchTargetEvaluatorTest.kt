package dev.pranav.reef.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            "com.google.android.apps.bard",
            MindfulLaunchTargetEvaluator.getTargetPackage(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.search.assistant.surfaces.voice.robin.ui.floaty.activity.FloatyActivity"
            )
        )

        assertEquals(
            "com.google.android.apps.bard",
            MindfulLaunchTargetEvaluator.getTargetPackage(
                packageName = "com.google.android.googlequicksearchbox",
                className = "com.google.android.apps.bard.shellapp.BardEntryPointActivity"
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

    @Test
    fun foregroundClassNamePreservesGeminiActivityAcrossDialogsAndResetsOnNavigation() {
        val robinActivity =
            "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
        val googlePkg = MindfulLaunchTargetEvaluator.GOOGLE_APP_PACKAGE

        // A generic dialog or view in the same package must not overwrite the Robin activity.
        assertEquals(
            robinActivity,
            MindfulLaunchTargetEvaluator.resolveForegroundClassName(
                currentPackage = googlePkg,
                newPackage = googlePkg,
                currentClassName = robinActivity,
                newClassName = "android.widget.FrameLayout"
            )
        )

        // Navigating to a Google Search activity inside the same package updates the class name.
        assertEquals(
            "com.google.android.apps.search.launcher.SearchActivity",
            MindfulLaunchTargetEvaluator.resolveForegroundClassName(
                currentPackage = googlePkg,
                newPackage = googlePkg,
                currentClassName = robinActivity,
                newClassName = "com.google.android.apps.search.launcher.SearchActivity"
            )
        )

        // Switching to a different app resets the tracked class name.
        assertEquals(
            "com.example.MainActivity",
            MindfulLaunchTargetEvaluator.resolveForegroundClassName(
                currentPackage = googlePkg,
                newPackage = "com.example.app",
                currentClassName = robinActivity,
                newClassName = "com.example.MainActivity"
            )
        )
    }

    @Test
    fun contentEventDoesNotDebounceWindowStateOrDifferentTargetPackage() {
        // A content event for Google Search must not debounce a Gemini window event 50ms later.
        assertFalse(
            MindfulLaunchTargetEvaluator.shouldDebounceAppCheck(
                lastCheckedTargetPackage = MindfulLaunchTargetEvaluator.GOOGLE_APP_PACKAGE,
                lastCheckedAtMs = 1000L,
                lastCheckedWasWindowState = false,
                targetPackage = MindfulLaunchTargetEvaluator.BARD_PACKAGE,
                nowMs = 1050L,
                isWindowStateChanged = true,
                debounceMs = 500L
            )
        )

        // A content event must not debounce a window-state event even for the same target package.
        assertFalse(
            MindfulLaunchTargetEvaluator.shouldDebounceAppCheck(
                lastCheckedTargetPackage = MindfulLaunchTargetEvaluator.BARD_PACKAGE,
                lastCheckedAtMs = 1000L,
                lastCheckedWasWindowState = false,
                targetPackage = MindfulLaunchTargetEvaluator.BARD_PACKAGE,
                nowMs = 1050L,
                isWindowStateChanged = true,
                debounceMs = 500L
            )
        )

        // A content event following a window-state event for the same target within 500ms - is debounced.
        assertTrue(
            MindfulLaunchTargetEvaluator.shouldDebounceAppCheck(
                lastCheckedTargetPackage = MindfulLaunchTargetEvaluator.BARD_PACKAGE,
                lastCheckedAtMs = 1000L,
                lastCheckedWasWindowState = true,
                targetPackage = MindfulLaunchTargetEvaluator.BARD_PACKAGE,
                nowMs = 1050L,
                isWindowStateChanged = false,
                debounceMs = 500L
            )
        )
    }
}
