package com.clearshield.adblocker.data.db.dao

import androidx.room.*
import com.clearshield.adblocker.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    // Filter Lists
    @Query("SELECT * FROM filter_lists ORDER BY id ASC")
    fun getAllFilterLists(): Flow<List<FilterListEntity>>

    @Query("SELECT * FROM filter_lists WHERE isEnabled = 1")
    suspend fun getEnabledFilterLists(): List<FilterListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilterList(list: FilterListEntity): Long

    @Update
    suspend fun updateFilterList(list: FilterListEntity)

    @Delete
    suspend fun deleteFilterList(list: FilterListEntity)

    // Allowlist
    @Query("SELECT * FROM allowlist ORDER BY createdAt DESC")
    fun getAllAllowlist(): Flow<List<AllowlistEntity>>

    @Query("SELECT * FROM allowlist WHERE isEnabled = 1")
    suspend fun getEnabledAllowlist(): List<AllowlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowlist(item: AllowlistEntity): Long

    @Update
    suspend fun updateAllowlist(item: AllowlistEntity)

    @Delete
    suspend fun deleteAllowlist(item: AllowlistEntity)

    // Custom Blocklist
    @Query("SELECT * FROM custom_blocklist ORDER BY createdAt DESC")
    fun getAllCustomBlocklist(): Flow<List<CustomBlockEntity>>

    @Query("SELECT * FROM custom_blocklist WHERE isEnabled = 1")
    suspend fun getEnabledCustomBlocklist(): List<CustomBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomBlock(item: CustomBlockEntity): Long

    @Update
    suspend fun updateCustomBlock(item: CustomBlockEntity)

    @Delete
    suspend fun deleteCustomBlock(item: CustomBlockEntity)
}

@Dao
interface AppFilterDao {
    @Query("SELECT * FROM app_filters ORDER BY appName ASC")
    fun getAllAppFilters(): Flow<List<AppFilterEntity>>

    @Query("SELECT * FROM app_filters WHERE isProtected = 0")
    suspend fun getDisallowedApps(): List<AppFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppFilters(apps: List<AppFilterEntity>)

    @Update
    suspend fun updateAppFilter(app: AppFilterEntity)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM daily_stats WHERE dateString = :dateString LIMIT 1")
    suspend fun getStatsForDate(dateString: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats ORDER BY dateString DESC LIMIT 7")
    fun getRecentStats(): Flow<List<DailyStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStats(stats: DailyStatsEntity)

    @Query("DELETE FROM daily_stats")
    suspend fun clearStats()
}
