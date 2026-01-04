package dev.pranav.reef.util

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.pranav.reef.PermissionsCheckActivity
import dev.pranav.reef.R


fun Activity.showAccessibilityDialog() {
    if (!isAccessibilityServiceEnabledForBlocker()) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.accessibility_service_name)
            .setMessage(R.string.accessibility_service_description)
            .setPositiveButton(getString(R.string.agree)) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                if (!isAccessibilityServiceEnabledForBlocker()) {
                    Toast.makeText(
                        this,
                        R.string.accessibility_error_toast,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.show()
    }
}

fun Activity.showUsageAccessDialog(onAgreeClick: () -> Unit) {
    MaterialAlertDialogBuilder(this).setTitle(R.string.usage_access)
        .setMessage(R.string.usage_access_description)
        .setPositiveButton(getString(R.string.agree)) { _, _ ->
            onAgreeClick()
        }.setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
            finish()
        }.show()
}

fun Context.isAccessibilityServiceEnabledForBlocker(): Boolean {
    val accessibilityServiceName = "$packageName/$packageName.accessibility.BlockerService"
    val serviceNameUncommon = "$packageName/.accessibility.BlockerService"
    val enabledServices = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return (enabledServices?.contains(accessibilityServiceName) == true) || (enabledServices?.contains(
        serviceNameUncommon
    ) == true)
}

fun Context.hasUsageStatsPermission(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        packageName
    )
    return if (mode == AppOpsManager.MODE_DEFAULT) {
        checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}

fun Context.hasDndPermission(): Boolean {
    val notificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            ?: return false
    return notificationManager.isNotificationPolicyAccessGranted
}

fun Context.hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Not required for older versions
    }
}

fun Context.isBatteryOptimizationDisabled(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

enum class PermissionType {
    ACCESSIBILITY,
    USAGE_STATS,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    DND
}

data class PermissionStatus(
    val type: PermissionType,
    val isGranted: Boolean,
    val title: String,
    val description: String
)

fun Context.checkAllPermissions(): List<PermissionStatus> {
    val permissions = mutableListOf<PermissionStatus>()

    permissions.add(
        PermissionStatus(
            type = PermissionType.ACCESSIBILITY,
            isGranted = isAccessibilityServiceEnabledForBlocker(),
            title = getString(R.string.accessibility_service_name),
            description = getString(R.string.accessibility_service_description)
        )
    )

    permissions.add(
        PermissionStatus(
            type = PermissionType.USAGE_STATS,
            isGranted = hasUsageStatsPermission(),
            title = getString(R.string.usage_access),
            description = getString(R.string.usage_access_description)
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(
            PermissionStatus(
                type = PermissionType.NOTIFICATION,
                isGranted = hasNotificationPermission(),
                title = getString(R.string.notification_permission),
                description = getString(R.string.notification_permission_description)
            )
        )
    }

    permissions.add(
        PermissionStatus(
            type = PermissionType.BATTERY_OPTIMIZATION,
            isGranted = isBatteryOptimizationDisabled(),
            title = getString(R.string.battery_optimization_exception),
            description = getString(R.string.battery_optimization_exception_description)
        )
    )

    permissions.add(
        PermissionStatus(
            type = PermissionType.DND,
            isGranted = hasDndPermission(),
            title = getString(R.string.enable_dnd),
            description = getString(R.string.dnd_description)
        )
    )

    return permissions
}

fun Activity.checkAndRequestMissingPermissions() {
    val allPermissions = checkAllPermissions()
    val missingPermissions = allPermissions.filter { !it.isGranted }

    if (missingPermissions.isNotEmpty()) {
        startActivity(Intent(this, PermissionsCheckActivity::class.java))
    }
}
