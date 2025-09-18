package dev.pranav.reef

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.databinding.ActivityTimerBinding
import dev.pranav.reef.util.AndroidUtilities
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import dev.pranav.reef.util.prefs

class TimerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        binding = ActivityTimerBinding.inflate(layoutInflater)

        val enter = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
            addTarget(binding.root)
        }
        window.enterTransition = enter

        window.allowEnterTransitionOverlap = true

        applyDefaults()
        super.onCreate(savedInstanceState)

        applyWindowInsets(binding.root)

        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                timerReceiver, IntentFilter("dev.pranav.reef.TIMER_UPDATED"), RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                timerReceiver,
                IntentFilter("dev.pranav.reef.TIMER_UPDATED")
            )
        }

        if (intent.hasExtra("left")) {
            val left = intent.getStringExtra("left")
            binding.timer.text = left
            binding.start.visibility = View.GONE
            binding.picker.visibility = View.GONE
            binding.timer.visibility = View.VISIBLE
        }
        binding.picker.minValue = 1
        binding.picker.maxValue = 180
        binding.picker.value = 1

        binding.start.setOnClickListener {
            startService()
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (FocusModeService.isRunning) {
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    startActivity(intent)
                } else {
                    finish()
                }
            }
        })
    }

    private fun startService() {
        prefs.edit().apply {
            putBoolean("focus_mode", true)
            putLong("focus_time", binding.picker.value * 60 * 1000L)
            apply()
        }

        val intent = Intent(this, FocusModeService::class.java)
        startForegroundService(intent)
        binding.start.visibility = View.GONE
        binding.picker.visibility = View.GONE
        binding.timer.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(timerReceiver)
        prefs.edit { putBoolean("focus_mode", false) }
    }

    private val timerReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val left = intent.getStringExtra("left")
            binding.timer.text = left

            if (left == "00:00") {
                binding.start.visibility = View.VISIBLE
                binding.picker.visibility = View.VISIBLE
                binding.timer.visibility = View.GONE
                val androidUtilities = AndroidUtilities()

                androidUtilities.vibrate(context, 500)
            }
        }
    }
}
