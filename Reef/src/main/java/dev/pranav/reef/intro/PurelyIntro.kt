package dev.pranav.reef.intro

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroPageTransformerType
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import dev.pranav.reef.R
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.prefs
import dev.pranav.reef.util.showAccessibilityDialog
import dev.pranav.reef.util.showUsageAccessDialog

class PurelyIntro : AppIntro2() {
    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply dynamic colors to the entire activity, if available
        DynamicColors.applyToActivityIfAvailable(this)

        isIndicatorEnabled = true
        isWizardMode = true
        isSystemBackButtonLocked = true
        setImmersiveMode()

        // Use M3 color tokens for indicators
        setIndicatorColor(
            selectedIndicatorColor = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorPrimaryFixed, null
            ),
            unselectedIndicatorColor = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceVariant, null
            )
        )
        setTransformer(AppIntroPageTransformerType.Fade)

        addSlide(
            DetailsFragment(
                title = getString(R.string.app_name),
                description = getString(R.string.app_description),
                imageRes = R.drawable.mobile_illustration
            )
        )
        addSlide(
            DetailsFragment(
                title = getString(R.string.accessibility_service),
                description = getString(R.string.accessibility_service_description),
                listener = { showAccessibilityDialog() },
                isTaskCompleted = { isAccessibilityServiceEnabledForBlocker() }
            )
        )
        val statsResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    goToNextSlide(false)
                }
            }
        addSlide(
            DetailsFragment(
                title = getString(R.string.app_usage_statistics),
                description = getString(R.string.app_usage_statistics_description),
                listener = {
                    showUsageAccessDialog(onAgreeClick = {
                        runOnUiThread {
                            statsResult.launch(
                                Intent(
                                    Settings.ACTION_USAGE_ACCESS_SETTINGS
                                )
                            )
                        }
                    })
                },
                isTaskCompleted = {
                    val mode =
                        (getSystemService(APP_OPS_SERVICE) as AppOpsManager).let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                @Suppress("DEPRECATION")
                                it.unsafeCheckOpNoThrow(
                                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                                    Process.myUid(),
                                    packageName
                                )
                            } else {
                                it.checkOpNoThrow(
                                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                                    Process.myUid(),
                                    packageName
                                )
                            }
                        }
                    val granted = if (mode == AppOpsManager.MODE_DEFAULT) {
                        checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        mode == AppOpsManager.MODE_ALLOWED
                    }
                    granted
                }
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val result =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted) {
                        finish()
                    }
                    createNotificationChannel()
                    goToNextSlide(false)
                }

            addSlide(
                DetailsFragment(
                    title = getString(R.string.notification_permission),
                    description = getString(R.string.notification_permission_description),
                    listener = {
                        runOnUiThread {
                            result.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    isTaskCompleted = {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    }),
            )
        }
        createNotificationChannel()

        addSlide(
            DetailsFragment(
                title = getString(R.string.battery_optimization_exception),
                description = getString(R.string.battery_optimization_exception_description),
                listener = {
                    runOnUiThread {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    createNotificationChannel()
                    prefs.edit().putBoolean("first_run", false).apply()
                },
                isTaskCompleted = {
                    return@DetailsFragment (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(
                        packageName
                    ).also { if (it) prefs.edit().putBoolean("first_run", false).apply() }
                }
            )
        )
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }

    private fun createNotificationChannel() {
        val descriptionText = "Shows reminders for screen time and when apps are blocked."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, "Content Blocker", importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
