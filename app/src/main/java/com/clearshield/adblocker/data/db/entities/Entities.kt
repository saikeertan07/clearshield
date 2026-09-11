package com.clearshield.adblocker.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "filter_lists")
data class FilterListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val ruleCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val description: String = ""
)

@Entity(
    tableName = "allowlist",
    indices = [Index(value = ["domain"], unique = true)]
)
data class AllowlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_blocklist",
    indices = [Index(value = ["domain"], unique = true)]
)
data class CustomBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_filters")
data class AppFilterEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isProtected: Boolean = true
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val dateString: String,
    val totalQueries: Long = 0,
    val blockedQueries: Long = 0,
    val adsBlocked: Long = 0,
    val trackersBlocked: Long = 0
)

data class QueryLogItem(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val domain: String,
    val queryType: String,
    val isBlocked: Boolean,
    val category: String,
    val reason: String
)
