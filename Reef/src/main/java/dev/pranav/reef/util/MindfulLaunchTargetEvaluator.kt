package dev.pranav.reef.util

// Gemini selection for MindfulLaunch appears as BARD_PACKAGE,
// but Gemini activity shows as Google umbrella app. Gemini can
// be instead intercepted through activity filter isGeminiAssistantActivity 
object MindfulLaunchTargetEvaluator {
    const val BARD_PACKAGE = "com.google.android.apps.bard"
    const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val GEMINI_ACTIVITY_KEYWORD = "robin.main.MainActivity"

    fun isGeminiAssistantActivity(className: String?): Boolean {
        return className.orEmpty().contains(GEMINI_ACTIVITY_KEYWORD, ignoreCase = true)
    }

    /**
     * Maps any internal Gemini window (Google App + Robin)
     * to the public Gemini identifier (BARD_PACKAGE).
     */
    fun getTargetPackage(packageName: String, className: String?): String {
        return if (packageName == GOOGLE_APP_PACKAGE && isGeminiAssistantActivity(className)) {
            BARD_PACKAGE
        } else {
            packageName
        }
    }
}
