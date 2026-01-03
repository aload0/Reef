package dev.pranav.reef.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import dev.pranav.reef.accessibility.BlockerService
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.routine.RoutineScheduler
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs


class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.isAccessibilityServiceEnabledForBlocker()) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("BootReceiver", "Device boot completed, rescheduling routines")

                // Initialize prefs if not already done
                if (!isPrefsInitialized) {
                    prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                }

                // Reschedule all enabled routines
                RoutineScheduler.scheduleAllRoutines(context)

                // Reschedule daily summary if enabled
                if (prefs.getBoolean("daily_summary", false)) {
                    DailySummaryScheduler.scheduleNextDailySummary(context)
                }

                val accessibilityIntent = Intent(context, BlockerService::class.java)
                context.startService(accessibilityIntent)

                if (prefs.getBoolean("focus_mode", false)) {
                    val serviceIntent = Intent(context, FocusModeService::class.java)
                    context.startService(serviceIntent)
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Package replaced, rescheduling routines")

                // Initialize prefs if not already done
                if (!isPrefsInitialized) {
                    prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                }

                // Show donate dialog
                prefs.edit {
                    putBoolean("show_dialog", true)
                }

                // Reschedule all enabled routines
                RoutineScheduler.scheduleAllRoutines(context)

                // Reschedule daily summary if enabled
                if (prefs.getBoolean("daily_summary", false)) {
                    DailySummaryScheduler.scheduleNextDailySummary(context)
                }

                val accessibilityIntent = Intent(context, BlockerService::class.java)
                context.startService(accessibilityIntent)

                if (prefs.getBoolean("focus_mode", false)) {
                    val serviceIntent = Intent(context, FocusModeService::class.java)
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
