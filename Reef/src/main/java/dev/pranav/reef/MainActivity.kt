package dev.pranav.reef

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapes
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.accessibility.getFormattedTime
import dev.pranav.reef.databinding.ActivityMainBinding
import dev.pranav.reef.intro.PurelyIntro
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.checkAndRequestMissingPermissions
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.prefs
import dev.pranav.reef.util.showAccessibilityDialog

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pendingFocusModeStart = false
    private var hasCheckedPermissions = false

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()
        binding = ActivityMainBinding.inflate(layoutInflater)

        val exit = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
            addTarget(binding.root)
        }
        window.exitTransition = exit
        window.sharedElementsUseOverlay = false

        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        val shape: ShapeDrawable = MaterialShapes.createShapeDrawable(MaterialShapes.PIXEL_CIRCLE)
        binding.startFocusMode.background = shape

        val shapeDrawable = MaterialShapes.createShapeDrawable(MaterialShapes.PIXEL_CIRCLE)
        val containerColor = ColorStateList.valueOf(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorTertiary,
                "Error: no colorPrimaryFixed color found"
            )
        )
        shapeDrawable.setTintList(containerColor)
        binding.startFocusMode.background = shapeDrawable

        val rippleColor = ColorStateList.valueOf(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                "Error: no colorOnPrimaryFixed color found"
            )
        ).withAlpha(96)
        val rippleDrawable = RippleDrawable(rippleColor, null, shapeDrawable)
        binding.startFocusMode.foreground = rippleDrawable

        addExceptions()

        if (prefs.getBoolean("first_run", true)) {
            startActivity(Intent(this, PurelyIntro::class.java))
        } else {
            if (FocusModeService.isRunning) {
                Log.d("MainActivity", "Starting timer activity")
                startActivity(Intent(this, TimerActivity::class.java).apply {
                    putExtra(
                        FocusModeService.EXTRA_TIME_LEFT,
                        getFormattedTime(prefs.getLong("focus_time", 10 * 60 * 1000))
                    )
                })
            } else {
                prefs.edit { putBoolean("focus_mode", false) }
            }
        }

        binding.startFocusMode.setOnClickListener {
            if (isAccessibilityServiceEnabledForBlocker()) {
                startActivity(Intent(this, TimerActivity::class.java))
            } else {
                pendingFocusModeStart = true
                showAccessibilityDialog()
            }
        }

        binding.appUsage.setOnClickListener {
            startActivity(Intent(this, AppUsageActivity::class.java))
        }

        binding.routines.setOnClickListener {
            startActivity(Intent(this, RoutinesActivity::class.java))
        }

        binding.whitelistApps.setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }

        binding.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        showDonateDialogIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        if (!hasCheckedPermissions && !prefs.getBoolean("first_run", true)) {
            hasCheckedPermissions = true
            checkAndRequestMissingPermissions()
        }

        if (pendingFocusModeStart && isAccessibilityServiceEnabledForBlocker()) {
            pendingFocusModeStart = false
            startActivity(Intent(this, TimerActivity::class.java))
        }
    }

    private fun showDonateDialogIfNeeded() {
        if (!prefs.getBoolean("donate_dialog_shown", false) && !prefs.getBoolean(
                "first_run",
                true
            )
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Enjoying Reef?")
                .setMessage(
                    """
                        I'm a student who maintains Reef in my personal time, and your support is the only way to sustain continuous improvements.
                        
                        Your support of any amount helps keep this project alive and improving.
                        
                        If Reef helps you, please consider supporting its future.
                    """.trimIndent()
                )
                .setPositiveButton("Support") { _, _ ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://PranavPurwar.github.io/donate.html")
                    )
                    startActivity(intent)
                    prefs.edit { putBoolean("donate_dialog_shown", true) }
                }
                .setNegativeButton("Maybe Later") { _, _ ->
                    prefs.edit { putBoolean("donate_dialog_shown", true) }
                }
                .setCancelable(true)
                .show()
        }
    }

    private fun addExceptions() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)

        packageManager.queryIntentActivities(intent, 0).forEach {
            val packageName = it.activityInfo.packageName
            if (!Whitelist.isWhitelisted(packageName)) {
                Whitelist.whitelist(packageName)
            }
        }
    }
}
