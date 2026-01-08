package dev.pranav.reef

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.google.android.material.color.DynamicColors
import dev.pranav.reef.accessibility.BlockerService
import dev.pranav.reef.receivers.DailySummaryScheduler
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.ReefWorker
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.prefs
import java.util.concurrent.TimeUnit

class App: Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        setupSafePreferences()

        AppLimits.init(this)
        Whitelist.init(this)

        scheduleWatcher(this)

        if (prefs.getBoolean("daily_summary", false)) {
            DailySummaryScheduler.scheduleDailySummary(this)
        }

        setupCrashHandler()
    }

    private fun setupSafePreferences() {
        val deviceContext = createDeviceProtectedStorageContext()

        deviceContext.moveSharedPreferencesFrom(this, "prefs")

        prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ReefApp", "CRITICAL CRASH: ${throwable.message}")
            throwable.printStackTrace()

            val intent = Intent(this, BlockerService::class.java)
            val pendingIntent = PendingIntent.getService(
                this,
                111,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1500,
                pendingIntent
            )

            defaultHandler?.uncaughtException(thread, throwable)
        }
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
