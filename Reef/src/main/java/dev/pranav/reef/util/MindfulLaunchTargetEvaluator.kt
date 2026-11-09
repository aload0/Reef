package dev.pranav.reef.util

// Gemini selection for MindfulLaunch appears as BARD_PACKAGE,
// but Gemini activity shows as Google umbrella app. Gemini can
// be instead intercepted through activity filter isGeminiAssistantActivity 
object MindfulLaunchTargetEvaluator {
    private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val BARD_PACKAGE = "com.google.android.apps.bard"
    private const val GOOGLE_ASSISTANT_ACTIVITY_PREFIX = "com.google.android.apps.search.assistant"

    fun isExtraMindfulAppService(
        packageName: String?,
        className: String?
    ): Boolean {
        val app = packageName ?: return false

        if (!MindfulLaunchManager.isMindfulApp(BARD_PACKAGE)) return false

        if (app == GOOGLE_APP_PACKAGE) {
            return isGeminiAssistantActivity(className)
        }

        return false
    }

    fun isGeminiAssistantActivity(className: String?): Boolean {
        val activity = className.orEmpty()
        return activity.startsWith(GOOGLE_ASSISTANT_ACTIVITY_PREFIX, ignoreCase = true) ||
            activity.contains("search.assistant", ignoreCase = true) ||
            activity.contains("robin", ignoreCase = true)
    }
}
