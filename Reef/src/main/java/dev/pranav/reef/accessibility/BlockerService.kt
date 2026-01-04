package dev.pranav.reef.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.R
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.NotificationHelper.createNotificationChannel
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

@SuppressLint("AccessibilityPolicy")
class BlockerService: AccessibilityService() {

    override fun onServiceConnected() {
        if (!isPrefsInitialized) {
            prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        }

        createNotificationChannel()
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (keyguardManager.isKeyguardLocked) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) return

        val pkg = event.packageName?.toString() ?: return
        Log.d(
            "BlockerService",
            "$pkg, ${Whitelist.isWhitelisted(pkg)}, ${UsageTracker.shouldBlock(this, pkg)}"
        )

        if (pkg == packageName) return
        if (Whitelist.isWhitelisted(pkg)) return

        if (prefs.getBoolean("focus_mode", false)) {
            Log.d("BlockerService", "Blocking $pkg in focus mode $event")
            performGlobalAction(GLOBAL_ACTION_HOME)
            showFocusModeNotification(pkg)
            return
        }

        val blockReason = UsageTracker.checkBlockReason(this, pkg)
        if (blockReason == UsageTracker.BlockReason.NONE) return

        Log.d("BlockerService", "Blocking $pkg in focus mode $event")

        performGlobalAction(GLOBAL_ACTION_HOME)

        showBlockedNotification(pkg, blockReason)
    }

    @SuppressLint("MissingPermission")
    private fun showBlockedNotification(pkg: String, reason: UsageTracker.BlockReason) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled().not()) {
            Log.w("BlockerService", "Notifications disabled by user")
            return
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }

        val contentText = when (reason) {
            UsageTracker.BlockReason.ROUTINE_LIMIT -> getString(
                R.string.blocked_by_routine,
                appName
            )

            else -> getString(R.string.reached_limit, appName)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setContentTitle(getString(R.string.app_blocked))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
            .notify(pkg.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun showFocusModeNotification(pkg: String) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled().not()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setContentTitle(getString(R.string.distraction_blocked))
            .setContentText(getString(R.string.you_were_using, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
            .notify(pkg.hashCode(), notification)
    }

    override fun onInterrupt() {}
}
