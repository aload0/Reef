package dev.pranav.reef.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

object AppLimits {
    private lateinit var sharedPreferences: SharedPreferences
    private val appLimits = mutableMapOf<String, Long>()
    private lateinit var usageStatsManager: UsageStatsManager

    fun setLimit(packageName: String, limit: Int) {
        // limit is in minutes, convert to milliseconds
        appLimits[packageName] = limit * 60 * 1000L
    }

    fun getLimit(packageName: String): Long {
        return appLimits[packageName] ?: 0L
    }

    fun removeLimit(packageName: String) {
        appLimits.remove(packageName)
    }

    fun clearLimits() {
        appLimits.clear()
    }

    fun getLimits(): Map<String, Long> {
        return appLimits.toMap()
    }

    fun hasLimit(packageName: String): Boolean {
        return appLimits.containsKey(packageName)
    }

    fun getUsageTime(packageName: String, usageStatsManager: UsageStatsManager): Long {
        return getUsageStats(usageStatsManager).find { it.packageName == packageName }?.totalTimeInForeground
            ?: 0L
    }

    fun getRawUsageStats(usageStatsManager: UsageStatsManager): List<UsageStats> {
        val endTime = System.currentTimeMillis()
        val startTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.systemDefault())
            .toInstant()
            .toEpochMilli()

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
    }

    fun getUsageStats(usageStatsManager: UsageStatsManager): List<AppUsageStats> {
        val endTime = System.currentTimeMillis()
        val startTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.systemDefault())
            .toInstant()
            .toEpochMilli()

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ).map { AppUsageStats(it.packageName, it.totalTimeInForeground) }
            .groupBy { it.packageName }
            .map { (_, statsList) ->
                statsList.reduce { acc, stats ->
                    acc.apply { totalTimeInForeground += stats.totalTimeInForeground }
                }
            }.sortedByDescending { it.totalTimeInForeground }
    }

    data class AppUsageStats(val packageName: String, var totalTimeInForeground: Long)

    fun saveLimits() {
        sharedPreferences.edit().apply {
            appLimits.forEach { (packageName, limit) ->
                putLong(packageName, limit)
            }
            apply()
        }
    }

    fun loadLimits(context: Context) {
        usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        sharedPreferences = context.getSharedPreferences("app_limits", Context.MODE_PRIVATE).apply {
            all.forEach { (packageName, limit) ->
                if (limit is Long) {
                    appLimits[packageName as String] = limit
                }
            }
        }
    }
}

object Whitelist {
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("whitelist", Context.MODE_PRIVATE)

        if (sharedPreferences.all.isEmpty()) {
            whitelistAll(allowedApps)
        }

        // Whitelist all installed input methods (keyboards)
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val inputMethods = inputMethodManager.enabledInputMethodList
        inputMethods.forEach { imi ->
            whitelist(imi.packageName)
        }

        // Whitelist the default SMS app
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        if (defaultSmsPackage != null) {
            whitelist(defaultSmsPackage)
        }

        // Whitelist the default Phone app
        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
        val defaultPhonePackage = telecomManager.defaultDialerPackage
        if (defaultPhonePackage != null) {
            whitelist(defaultPhonePackage)
        }

        // Whitelist the default assistant app
        val intentAssist = android.content.Intent(android.content.Intent.ACTION_ASSIST).apply {
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        val resolveInfoAssist = context.packageManager.resolveActivity(intentAssist, 0)
        val defaultAssistPackage = resolveInfoAssist?.activityInfo?.packageName
        if (defaultAssistPackage != null) {
            whitelist(defaultAssistPackage)
        }

        // Whitelist the default launcher
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        val defaultLauncherPackage = resolveInfo?.activityInfo?.packageName
        if (defaultLauncherPackage != null) {
            whitelist(defaultLauncherPackage)
        }

        // Whitelist apps with SYSTEM_ALERT_WINDOW permission
        context.packageManager.getPackagesHoldingPermissions(
            arrayOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
            0
        ).forEach { pkg ->
            whitelist(pkg.packageName)
        }
    }

    fun isWhitelisted(packageName: String): Boolean {
        return sharedPreferences.getBoolean(packageName, false)
    }

    fun whitelist(packageName: String) {
        sharedPreferences.edit { putBoolean(packageName, true) }
    }

    fun whitelistAll(set: Set<String>) {
        set.forEach { whitelist(it) }
    }

    fun unwhitelist(packageName: String) {
        sharedPreferences.edit { putBoolean(packageName, false) }
    }

    fun load(context: Context) {
        sharedPreferences = context.getSharedPreferences("whitelist", Context.MODE_PRIVATE)
    }

    val allowedApps = hashSetOf(
        "com.android.systemui",
        "com.android.settings",
        "dev.pranav.reef",
        "com.android.calculator2",
        "com.android.mms",
        "com.android.phone",
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.deskclock",
        "com.google.android.calendar",
        "com.google.android.keep",

        "com.google.android.apps.docs",
        "com.google.android.apps.drive",
        "com.google.android.apps.sheets",
        "com.google.android.apps.slides",
        "com.google.android.apps.maps",
        "com.google.android.apps.photos",
        "com.google.android.apps.photosgo",
        "com.google.android.apps.authenticator2",
        "com.google.android.apps.paidtasks",
        "com.google.android.apps.docs.editor.docs",
        "com.google.android.apps.docs.editor.sheets",
        "com.google.android.apps.classroom",
        "com.google.android.apps.giant",
        "com.google.android.apps.tachyon",
        "com.google.android.webview",
        "com.google.android.packageinstaller",
        "com.google.android.gms",

        "net.osmand",
        "com.fsck.k9",
        "com.google.android.apps.wellbeing",
        "com.android.documentsui",
        "bin.mt.plus.canary",
        "com.sadellie.calculator",
        "com.lineageos.aperture.dev",
        "com.lineageos.aperture",
        "com.shazam.android",
        "com.synapsetech.compass",
        "hr.dtekac.prognoza",
        "me.jmh.authenticatorpro",

        "com.microsoft.office.officehubrow",
        "com.slack",
        "com.google.android.gm",
        "com.google.android.apps.meet",
        "com.microsoft.teams",
        "com.paypal.android.p2pmobile",
        "com.google.android.apps.chromecast.app",
        "com.google.android.apps.nbu.paisa.user",
        "com.whatsapp"
    )
}
