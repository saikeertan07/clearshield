package com.clearshield.adblocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.clearshield.adblocker.data.db.AppDatabase
import com.clearshield.adblocker.data.repository.AppFilterRepository
import com.clearshield.adblocker.data.repository.FilterRepository
import com.clearshield.adblocker.data.repository.SettingsRepository
import com.clearshield.adblocker.data.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClearShieldApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "clearshield_vpn_status"
        lateinit var instance: ClearShieldApp
            private set
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val filterRepository: FilterRepository by lazy {
        FilterRepository(this, database.filterDao())
    }

    val appFilterRepository: AppFilterRepository by lazy {
        AppFilterRepository(this, database.appFilterDao())
    }

    val statsRepository: StatsRepository by lazy {
        StatsRepository(database.statsDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ClearShieldApp", "Uncaught exception on thread ${thread.name}", throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }

        try {
            com.clearshield.adblocker.service.VpnStateHolder.setActiveUpstream(settingsRepository.upstreamDns.value)
        } catch (t: Throwable) {
            android.util.Log.e("ClearShieldApp", "Error reading upstream DNS setting", t)
        }

        createNotificationChannel()

        // Asynchronously load default filter lists and compile rules in background safely
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                filterRepository.initDefaultFilterListsIfEmpty()
                filterRepository.reloadAndCompileAllRules()
            } catch (t: Throwable) {
                android.util.Log.e("ClearShieldApp", "Error loading initial filters in background", t)
            }
        }

        scheduleFilterListUpdates()
    }

    private fun scheduleFilterListUpdates() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.clearshield.adblocker.work.FilterUpdateWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                com.clearshield.adblocker.work.FilterUpdateWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            android.util.Log.i("ClearShieldApp", "Scheduled 24h filter list auto-update periodic work")
        } catch (t: Throwable) {
            android.util.Log.e("ClearShieldApp", "Failed to schedule filter list auto-update", t)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = getString(R.string.vpn_notification_channel)
                val descriptionText = getString(R.string.vpn_notification_channel_desc)
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    setShowBadge(false)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            android.util.Log.w("ClearShieldApp", "Could not create notification channel: ${e.message}")
        }
    }
}
