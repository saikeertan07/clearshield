package com.clearshield.adblocker.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clearshield.adblocker.ClearShieldApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilterUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FilterUpdateWorker"
        const val WORK_NAME = "clearshield_filter_update"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting periodic filter list auto-update...")
        try {
            val app = applicationContext as? ClearShieldApp ?: run {
                Log.w(TAG, "ApplicationContext is not ClearShieldApp")
                return@withContext Result.failure()
            }

            val filterRepository = app.filterRepository
            val enabledLists = app.database.filterDao().getEnabledFilterLists()
            Log.d(TAG, "Found ${enabledLists.size} enabled filter lists to evaluate")

            var updatedCount = 0
            var errorCount = 0

            for (list in enabledLists) {
                // Update remote HTTPS subscription filter lists
                if (list.url.startsWith("https://")) {
                    Log.d(TAG, "Updating filter list: ${list.name} (${list.url})")
                    val result = filterRepository.updateFilterListFromNetwork(list)
                    result.fold(
                        onSuccess = { count ->
                            Log.i(TAG, "Successfully updated ${list.name} with $count rules")
                            updatedCount++
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed updating ${list.name}: ${error.message}")
                            errorCount++
                        }
                    )
                }
            }

            // Always ensure the filter engine has reloaded and compiled all rules
            filterRepository.reloadAndCompileAllRules()
            Log.i(TAG, "Filter list auto-update completed. Updated: $updatedCount, Errors: $errorCount")

            if (errorCount > 0 && updatedCount == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception in filter list auto-update", e)
            Result.retry()
        }
    }
}
