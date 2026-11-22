package dev.pranav.reef.util

/** Maps Google-app events to Gemini ([BARD_PACKAGE]) by Activity class, and debounces repeated app checks. */
object MindfulLaunchTargetEvaluator {
    const val BARD_PACKAGE = "com.google.android.apps.bard"
    const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

    // "robin" = Gemini's main activity as full-screen or as an overlay on top of another app; "apps.bard" = entry-point activity.
    private val GEMINI_ACTIVITY_HINTS = listOf(
        "assistant.surfaces.voice.robin",
        "apps.bard"
    )

    // Events from these generic UI classes say nothing about the Activity, so we keep the current one.
    private val GENERIC_UI_CLASS_PREFIXES = listOf(
        "android.",
        "androidx.",
        "com.google.android.material."
    )

    fun isGeminiAssistantActivity(className: String?): Boolean {
        val normalized = className.orEmpty()
        return GEMINI_ACTIVITY_HINTS.any { hint -> normalized.contains(hint, ignoreCase = true) }
    }

    fun getTargetPackage(packageName: String, className: String?): String {
        return if (packageName == GOOGLE_APP_PACKAGE && isGeminiAssistantActivity(className)) {
            BARD_PACKAGE
        } else {
            packageName
        }
    }

    /** Next tracked Activity class on a window-state change event; keeps [currentClassName] across blank/generic overlay events. 
    
    Gemini case: keep the previous Activity class. Dialogs inside Gemini would erase the "this is Gemini" info. The function stops that.*/
    fun resolveForegroundClassName(
        currentPackage: String?,
        newPackage: String,
        currentClassName: String?,
        newClassName: String?
    ): String? {
        val candidate = newClassName?.takeIf { it.isNotBlank() }
        if (currentPackage != newPackage) {
            return candidate
        }
        if (candidate == null) {
            return currentClassName
        }
        if (isGeminiAssistantActivity(candidate) || currentClassName == null) {
            return candidate
        }
        val isGenericUi = GENERIC_UI_CLASS_PREFIXES.any { prefix ->
            candidate.startsWith(prefix, ignoreCase = true)
        }
        return if (isGenericUi) currentClassName else candidate
    }

    /** True if this check duplicated the previous shouldDebounceAppCheck for the same target within [debounceMs]. Exception: debounce is skipped if a window-state-change event follows a content-change event. 
    
    Exception Example.
    Google-app Gemini case:

    1. The user unlocks the phone while an App
    is in the foreground.
    2. Unlocking triggers a content-change
    event from the App. It runs a handleAppBlockCheck, and
    lastChecked = App, content is recorded.
    3. About 100 ms later the unlock handler
    asks for its own check with
    isWindowStateChanged = true. It is the
    same target App, within 500 ms.
    4. With the exception, the handleAppBlockCheck is not debounced/skipped because shouldDebounceAppCheck lets it run.
    */
    fun shouldDebounceAppCheck(
        lastCheckedTargetPackage: String?,
        lastCheckedAtMs: Long,
        lastCheckedWasWindowState: Boolean,
        targetPackage: String,
        nowMs: Long,
        isWindowStateChanged: Boolean,
        debounceMs: Long
    ): Boolean {
        val withinWindow =
            targetPackage == lastCheckedTargetPackage && nowMs - lastCheckedAtMs < debounceMs
        if (!withinWindow) return false
        return !(isWindowStateChanged && !lastCheckedWasWindowState)
    }
}
