package dev.pranav.reef.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.MindfulLaunchActivity
import dev.pranav.reef.R
import dev.pranav.reef.services.routines.RoutineSessionManager
import dev.pranav.reef.util.BLOCKER_CHANNEL_ID
import dev.pranav.reef.util.FocusStats
import dev.pranav.reef.util.MindfulLaunchManager
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.NotificationHelper.BLOCKER_GROUP_KEY
import dev.pranav.reef.util.NotificationHelper.createNotificationChannel
import dev.pranav.reef.util.NotificationHelper.syncRoutineNotification
import dev.pranav.reef.util.WebsiteBlocklist
import dev.pranav.reef.util.WebsiteLimits
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("AccessibilityPolicy")
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var keyguardManager: KeyguardManager? = null
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private var screenReceiverRegistered = false
    private var lastCheckedPackage: String? = null
    private var lastAppCheckAtMs = 0L
    private var foregroundPackage: String? = null
    private var enforcedForegroundPackage: String? = null

    private var activeBrowserPackage: String? = null
    private var activeBrowserConfig: BrowserConfig? = null
    private val browserPackages = mutableSetOf<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    WebsiteUsageTracker.stopTracking()
                }

                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    // Start tracking again if we are already in a browser with a domain
                    // but we will let onAccessibilityEvent handle the current domain state
                }
            }
        }
    }

    private val websiteLimitPollRunnable = object : Runnable {
        override fun run() {
            try {
                val currentDomain = WebsiteUsageTracker.getCurrentTrackingDomain()
                if (currentDomain != null && WebsiteLimits.hasLimit(
                        currentDomain
                    )
                ) {
                    val limit = WebsiteLimits.getLimit(currentDomain)
                    val usage = WebsiteUsageTracker.getDailyUsage(currentDomain)
                    Log.d("BlockerService", "limit=$limit, usage=$usage for $currentDomain")
                    if (usage >= limit) {
                        WebsiteUsageTracker.stopTracking()
                        activeBrowserConfig?.let { config ->
                            performRedirect(config)
                            showBlockedNotification(
                                currentDomain,
                                UsageTracker.BlockReason.DAILY_LIMIT,
                                isWebsite = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BlockerService", "Website limit poll error", e)
            }
            handler.postDelayed(this, 5000L) // Poll every 5 seconds
        }
    }

    private val routinePollRunnable = object : Runnable {
        override fun run() {
            try {
                RoutineSessionManager.evaluateAndSync(this@BlockerService)
                syncRoutineNotification(this@BlockerService)
            } catch (e: Exception) {
                Log.e("BlockerService", "Routine poll error", e)
            }
            handler.postDelayed(this, ROUTINE_POLL_INTERVAL_MS)
        }
    }

    private data class BrowserConfig(
        val urlBarId: String,
        val suggestionBoxId: String,
        val isSuggestionBoxEqualToGo: Boolean = false,
        val suggestionBoxChildIndex: Int = 0
    )

    private val commonResourceConfigs = listOf(
        Triple("url_bar", "omnibox_suggestions_dropdown", false), // Chrome, Brave, Edge, Vivaldi
        Triple("mozac_browser_toolbar_url_view", "sfcnt", false), // Firefox
        Triple("url_field", "right_state_button", true), // Opera
        Triple("location_bar_edit_text", "location_bar_edit_text", false), // Samsung
        Triple("omnibarTextInput", "omnibarTextInput", false) // DuckDuckGo
    )

    private val redirectUrl = "about:blank"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isBound = true
        lastEventEpochMs = System.currentTimeMillis()
        _connectionState.value = false
        configureService()
        createNotificationChannel()
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        WebsiteUsageTracker.init(this)
        WebsiteLimits.init(this)
        refreshBrowserPackages()

        if (!isPrefsInitialized) {
            val deviceContext = createDeviceProtectedStorageContext()
            prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (!screenReceiverRegistered) {
            registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }

        handler.removeCallbacks(routinePollRunnable)
        handler.removeCallbacks(websiteLimitPollRunnable)
        handler.post(routinePollRunnable)
        handler.post(websiteLimitPollRunnable)
        _connectionState.value = true
    }

    private fun configureService() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lastEventEpochMs = System.currentTimeMillis()
        if (keyguardManager?.isKeyguardLocked == true) return

        val pkg = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (foregroundPackage != pkg) {
                foregroundPackage = pkg
                enforcedForegroundPackage = null
            }

            if (!browserPackages.contains(pkg)) {
                WebsiteUsageTracker.stopTracking()
                activeBrowserPackage = null
                activeBrowserConfig = null
            } else {
                activeBrowserPackage = pkg
            }
        }

        if (pkg == packageName) return

        // Content events can arrive after another app has already taken the foreground.
        // Ignoring those stale events prevents repeatedly forcing Home for the same launch.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            foregroundPackage != null &&
            pkg != foregroundPackage
        ) {
            return
        }

        if (activeBrowserPackage == pkg) {
            val root = rootInActiveWindow ?: event.source ?: return
            val config = findBrowserConfig(root, pkg)
            if (config != null) {
                activeBrowserConfig = config
                val urlBarNode = findUrlBarNode(root, config.urlBarId)
                if (urlBarNode != null) {
                    val url = extractUrlFromNode(urlBarNode)
                    if (url != null) {

                        Log.d("BlockerService", "Found url=$url in node $urlBarNode")
                        val domain = sanitizeUrl(url)

                        if (WebsiteBlocklist.isBlocked(domain)) {
                            WebsiteUsageTracker.stopTracking()
                            performRedirect(config)
                            showBlockedNotification(
                                domain,
                                UsageTracker.BlockReason.DAILY_LIMIT,
                                isWebsite = true
                            )
                            return
                        }

                        if (WebsiteLimits.hasLimit(domain)) {
                            WebsiteUsageTracker.startTracking(domain)
                            val limit = WebsiteLimits.getLimit(domain)
                            val usage = WebsiteUsageTracker.getDailyUsage(domain)
                            if (usage >= limit) {
                                WebsiteUsageTracker.stopTracking()
                                performRedirect(config)
                                showBlockedNotification(
                                    domain,
                                    UsageTracker.BlockReason.DAILY_LIMIT,
                                    isWebsite = true
                                )
                                return
                            }
                        } else {
                            WebsiteUsageTracker.stopTracking()
                        }
                    } else {
                        WebsiteUsageTracker.stopTracking()
                    }
                }
            }
        }

        val shouldCheckApp = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                event.contentChangeTypes !=
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION

            else -> false
        }

        if (shouldCheckApp && shouldHandleAppCheck(pkg)) {
            handleAppBlockCheck(pkg)
        }
    }

    private fun shouldHandleAppCheck(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isDuplicate =
            pkg == lastCheckedPackage && now - lastAppCheckAtMs < APP_CHECK_DEBOUNCE_MS
        if (!isDuplicate) {
            lastCheckedPackage = pkg
            lastAppCheckAtMs = now
        }
        return !isDuplicate
    }

    private fun refreshBrowserPackages() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
            browserPackages.clear()
            for (info in resolveInfos) {
                browserPackages.add(info.activityInfo.packageName)
            }
            // Always include known ones as fallback
            browserPackages.addAll(
                listOf(
                    "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
                    "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser"
                )
            )
        } catch (e: Exception) {
            Log.e("BlockerService", "Error refreshing browsers", e)
        }
    }

    private fun findBrowserConfig(root: AccessibilityNodeInfo, pkg: String): BrowserConfig? {
        // 1. Try common resource names with current package first
        for ((urlRes, suggestRes, isGo) in commonResourceConfigs) {
            val urlId = "$pkg:id/$urlRes"
            val nodes = root.findAccessibilityNodeInfosByViewId(urlId)
            if (!nodes.isNullOrEmpty()) {
                return BrowserConfig(urlId, "$pkg:id/$suggestRes", isGo)
            }
        }

        // 2. Try hardcoded full IDs in priority order (Chrome, Mozilla, Opera)
        val hardcoded = listOf(
            Triple(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/omnibox_suggestions_dropdown",
                false
            ),
            Triple(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/sfcnt",
                false
            ),
            Triple(
                "com.opera.browser:id/url_field",
                "com.opera.browser:id/right_state_button",
                true
            )
        )
        for ((urlId, suggestId, isGo) in hardcoded) {
            val nodes = root.findAccessibilityNodeInfosByViewId(urlId)
            if (!nodes.isNullOrEmpty()) {
                return BrowserConfig(urlId, suggestId, isGo)
            }
        }
        return null
    }

    private fun findUrlBarNode(
        root: AccessibilityNodeInfo,
        fullId: String
    ): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
        return if (!nodes.isNullOrEmpty()) nodes[0] else null
    }

    private fun extractUrlFromNode(node: AccessibilityNodeInfo): String? {
        if (node.isFocused) return null
        val text = node.text?.toString() ?: return null
        if (text.isBlank() || !text.contains('.') || text.contains(' ')) return null
        return text
    }

    private fun sanitizeUrl(url: String): String {
        return url.lowercase()
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .substringBefore('/')
    }

    private fun performRedirect(config: BrowserConfig) {
        val initialRoot = rootInActiveWindow ?: return
        val urlBar = findUrlBarNode(initialRoot, config.urlBarId) ?: return
        urlBar.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        handler.postDelayed({
            val editRoot = rootInActiveWindow ?: return@postDelayed
            val editText = findUrlBarNode(editRoot, config.urlBarId) ?: return@postDelayed
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    redirectUrl
                )
            }
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            handler.postDelayed({
                val finalRoot = rootInActiveWindow ?: return@postDelayed
                performGoAction(finalRoot, config)
            }, 300)
        }, 300)
    }

    private fun performGoAction(root: AccessibilityNodeInfo, config: BrowserConfig) {
        val nodes = root.findAccessibilityNodeInfosByViewId(config.suggestionBoxId) ?: return
        val box = nodes.firstOrNull() ?: return
        if (config.isSuggestionBoxEqualToGo) {
            box.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val child = box.getChild(config.suggestionBoxChildIndex)
            if (child != null) {
                child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    private fun handleAppBlockCheck(pkg: String) {
        if (enforcedForegroundPackage == pkg) return

        if (prefs.getBoolean("focus_mode", false)) {
            if (Whitelist.isWhitelisted(pkg)) return

            FocusStats.recordBlockEvent(pkg, "focus_mode")
            enforcedForegroundPackage = pkg
            performGlobalAction(GLOBAL_ACTION_HOME)
            showFocusModeNotification(pkg)
            return
        }

        if (MindfulLaunchManager.isEnabled() && MindfulLaunchManager.isMindfulApp(pkg)) {
            if (!MindfulLaunchManager.isCurrentlyUnlocked(pkg)) {
                enforcedForegroundPackage = pkg
                val intent = Intent(this, MindfulLaunchActivity::class.java).apply {
                    putExtra("target_package", pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                return
            }
        }

        val blockReason = UsageTracker.checkBlockReason(this, pkg)
        if (blockReason != UsageTracker.BlockReason.NONE) {
            enforcedForegroundPackage = pkg
            performGlobalAction(GLOBAL_ACTION_HOME)
            showBlockedNotification(pkg, blockReason)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showBlockedNotification(
        pkgOrUrl: String,
        reason: UsageTracker.BlockReason,
        isWebsite: Boolean = false
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkgOrUrl, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            pkgOrUrl
        }
        val contentText = when (reason) {
            UsageTracker.BlockReason.ROUTINE_LIMIT -> if (isWebsite) getString(R.string.website_blocked_by_routine) else getString(
                R.string.blocked_by_routine,
                appName
            )

            else -> if (isWebsite) getString(
                R.string.website_reached_limit,
                appName
            ) else getString(R.string.reached_limit, appName)
        }

        val titleText =
            if (isWebsite) getString(R.string.website_blocked) else getString(R.string.app_blocked)

        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(BLOCKER_GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pkgOrUrl.hashCode(), notification)

        val summary = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup(BLOCKER_GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notificationManager.notify(NotificationHelper.BLOCKER_SUMMARY_ID, summary)
    }

    @SuppressLint("MissingPermission")
    private fun showFocusModeNotification(pkg: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (!prefs.getBoolean("focus_reminders", true)) return
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.distraction_blocked))
            .setContentText(getString(R.string.you_were_using, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify("focus_$pkg".hashCode(), notification)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        cleanupConnection()
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        isBound = true
        lastEventEpochMs = System.currentTimeMillis()
    }

    override fun onDestroy() {
        isBound = false
        cleanupConnection()
        super.onDestroy()
    }

    private fun cleanupConnection() {
        _connectionState.value = false
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
            }
            screenReceiverRegistered = false
        }
        handler.removeCallbacks(websiteLimitPollRunnable)
        handler.removeCallbacks(routinePollRunnable)
        foregroundPackage = null
        enforcedForegroundPackage = null
        activeBrowserPackage = null
        activeBrowserConfig = null
        WebsiteUsageTracker.stopTracking()
    }

    companion object {
        private const val ROUTINE_POLL_INTERVAL_MS = 30_000L
        private const val APP_CHECK_DEBOUNCE_MS = 500L

        @Volatile
        var isBound: Boolean = false

        @Volatile
        var lastEventEpochMs: Long = 0L

        private val _connectionState = MutableStateFlow(false)
        val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

        val isConnected: Boolean
            get() = _connectionState.value && isBound
    }
}
