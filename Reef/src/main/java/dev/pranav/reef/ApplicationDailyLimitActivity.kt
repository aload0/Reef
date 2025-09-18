package dev.pranav.reef

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.pranav.reef.databinding.ActivityDailyLimitBinding
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import java.util.concurrent.TimeUnit

class ApplicationDailyLimitActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDailyLimitBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()
        super.onCreate(savedInstanceState)

        binding = ActivityDailyLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(binding.root)

        val packageName = intent.getStringExtra("package_name") ?: return
        val application = packageManager.getApplicationInfo(packageName, 0)

        binding.apply {
            appIcon.setImageDrawable(packageManager.getApplicationIcon(application))
            appName.text = packageManager.getApplicationLabel(application)

            toolbar.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save -> {
                        val limitInMinutes = timeSlider.value.toInt()
                        AppLimits.setLimit(packageName, limitInMinutes)
                        AppLimits.saveLimits()
                        finishAfterTransition()
                        true
                    }

                    else -> false
                }
            }

            // Limits are stored in milliseconds, but we want to show minutes in the UI
            val existingLimit = AppLimits.getLimit(packageName) / 60000

            val initialMinutes = if (existingLimit > 0) existingLimit else 60
            timeSlider.value = initialMinutes.toFloat()

            timeDisplayText.text = formatMinutes(timeSlider.value)

            timeSlider.addOnChangeListener { _, value, _ ->
                timeDisplayText.text = formatMinutes(value)
            }

            removeLimitButton.setOnClickListener {
                AppLimits.removeLimit(packageName)
                AppLimits.saveLimits()
                finishAfterTransition()
            }
        }
    }

    /**
     * Helper function to convert a float value of total minutes
     * into a human-readable string like "1h 30m".
     */
    private fun formatMinutes(totalMinutes: Float): String {
        val minutesLong = totalMinutes.toLong()
        if (minutesLong == 0L) {
            // Special case for 0 to show "No Limit" or "0m"
            return "0m"
        }

        val hours = TimeUnit.MINUTES.toHours(minutesLong)
        val remainingMinutes = minutesLong % 60

        val hoursString = if (hours > 0) "${hours}h " else ""
        val minutesString = if (remainingMinutes > 0) "${remainingMinutes}m" else ""

        // If hours > 0 and minutes == 0, we don't want a trailing space
        return (hoursString + minutesString).trim()
    }
}
