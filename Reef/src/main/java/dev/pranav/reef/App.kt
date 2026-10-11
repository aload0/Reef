package dev.pranav.reef

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.pranav.reef.receivers.DailySummaryScheduler
import dev.pranav.reef.services.routines.RoutineAlarmScheduler
import dev.pranav.reef.services.routines.RoutineSessionManager
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.FocusStats
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.ReefWorker
import dev.pranav.reef.util.WebsiteBlocklist
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.prefs
import java.util.concurrent.TimeUnit

class App : Application(), Configuration.Provider {
    @Volatile
    private var initializedAfterUnlock = false

    @Volatile
    private var unlockReceiverRegistered = false

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        setupCrashHandler()

        val userManager = getSystemService(UserManager::class.java)
        if (userManager?.isUserUnlocked == false) {
            Log.i("ReefApp", "Deferring initialization until the user is unlocked")
            registerUnlockReceiver()
            return
        }

        initializeAfterUnlock()
    }

    fun initializeAfterUnlock(): Boolean {
        if (initializedAfterUnlock) return true

        val userManager = getSystemService(UserManager::class.java)
        if (!userManager.isUserUnlocked) {
            Log.i("ReefApp", "Deferring initialization until the user is unlocked")
            registerUnlockReceiver()
            return false
        }

        synchronized(this) {
            if (initializedAfterUnlock) return true

            setupSafePreferences()

            AppLimits.init(this)
            Whitelist.init(this)
            FocusStats.init(this)
            WebsiteBlocklist.init(this)

            scheduleWatcher(this)

            RoutineSessionManager.evaluateAndSync(this)
            NotificationHelper.syncRoutineNotification(this)
            RoutineAlarmScheduler.scheduleAll(this, dev.pranav.reef.routine.Routines.getAll())

            if (prefs.getBoolean("daily_summary", false)) {
                DailySummaryScheduler.scheduleDailySummary(this)
            }

            initializedAfterUnlock = true
        }

        return true
    }

    private fun setupSafePreferences() {
        val deviceContext = createDeviceProtectedStorageContext()
        val userManager = getSystemService(UserManager::class.java)

        if (userManager?.isUserUnlocked == true) {
            try {
                deviceContext.moveSharedPreferencesFrom(this, "prefs")
            } catch (e: IllegalStateException) {
                Log.w(
                    "ReefApp",
                    "Credential-protected prefs migration failed while unlocked; continuing with device-protected storage",
                    e
                )
            }
        }

        prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiverRegistered) return

        unlockReceiverRegistered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    unregisterReceiver(this)
                    unlockReceiverRegistered = false
                    initializeAfterUnlock()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            Log.e("ReefApp", "CRITICAL CRASH: $stackTrace")

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val currentTime = System.currentTimeMillis()

            // Alarm to show DebugActivity with error message
            val debugIntent = Intent(this, DebugActivity::class.java).apply {
                putExtra("error", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val debugPendingIntent = PendingIntent.getActivity(
                this,
                112,
                debugIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.set(AlarmManager.RTC_WAKEUP, currentTime + 1500, debugPendingIntent)

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var colorScheme: ColorScheme
    }
}


fun scheduleWatcher(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<ReefWorker>(
        15, TimeUnit.MINUTES,
        5, TimeUnit.MINUTES
    ).setConstraints(Constraints.NONE).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "ReefSafetyNet",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
