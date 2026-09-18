package com.clearshield.adblocker.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.clearshield.adblocker.data.db.dao.AppFilterDao
import com.clearshield.adblocker.data.db.entities.AppFilterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isProtected: Boolean,
    val isSystemApp: Boolean
)

class AppFilterRepository(
    private val context: Context,
    private val appFilterDao: AppFilterDao
) {
    fun getAllAppFilters(): Flow<List<AppFilterEntity>> = appFilterDao.getAllAppFilters()

    suspend fun getDisallowedPackages(): List<String> = withContext(Dispatchers.IO) {
        appFilterDao.getDisallowedApps().map { it.packageName }
    }

    suspend fun toggleAppProtection(packageName: String, appName: String, currentProtected: Boolean) = withContext(Dispatchers.IO) {
        val updated = AppFilterEntity(
            packageName = packageName,
            appName = appName,
            isProtected = !currentProtected
        )
        appFilterDao.updateAppFilter(updated)
    }

    suspend fun loadInstalledApplications(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val currentOverrides = appFilterDao.getDisallowedApps().associateBy { it.packageName }
        val list = mutableListOf<InstalledAppInfo>()
        val seenPackages = mutableSetOf<String>()

        // Query launcher activities via scoped queries instead of requiring QUERY_ALL_PACKAGES
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        for (resolveInfo in resolveInfos) {
            val app = resolveInfo.activityInfo?.applicationInfo ?: continue
            if (app.packageName == context.packageName) continue
            if (!seenPackages.add(app.packageName)) continue

            val name = try {
                resolveInfo.loadLabel(pm).toString()
            } catch (_: Exception) {
                app.packageName
            }
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isProtected = currentOverrides[app.packageName]?.isProtected ?: true

            list.add(
                InstalledAppInfo(
                    packageName = app.packageName,
                    appName = if (name.isNotBlank()) name else app.packageName,
                    isProtected = isProtected,
                    isSystemApp = isSystem
                )
            )
        }

        // Fallback to getInstalledApplications if queryIntentActivities returned nothing
        if (list.isEmpty()) {
            val installedApps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                emptyList()
            }
            for (app in installedApps) {
                if (app.packageName == context.packageName) continue
                if (!seenPackages.add(app.packageName)) continue

                val name = try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    app.packageName
                }
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isProtected = currentOverrides[app.packageName]?.isProtected ?: true

                list.add(
                    InstalledAppInfo(
                        packageName = app.packageName,
                        appName = if (name.isNotBlank()) name else app.packageName,
                        isProtected = isProtected,
                        isSystemApp = isSystem
                    )
                )
            }
        }

        list.sortBy { it.appName.lowercase() }
        list
    }
}
