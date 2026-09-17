package com.clearshield.adblocker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clearshield.adblocker.data.db.dao.AppFilterDao
import com.clearshield.adblocker.data.db.dao.FilterDao
import com.clearshield.adblocker.data.db.dao.StatsDao
import com.clearshield.adblocker.data.db.entities.AllowlistEntity
import com.clearshield.adblocker.data.db.entities.AppFilterEntity
import com.clearshield.adblocker.data.db.entities.CustomBlockEntity
import com.clearshield.adblocker.data.db.entities.DailyStatsEntity
import com.clearshield.adblocker.data.db.entities.FilterListEntity

@Database(
    entities = [
        FilterListEntity::class,
        AllowlistEntity::class,
        CustomBlockEntity::class,
        AppFilterEntity::class,
        DailyStatsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao
    abstract fun appFilterDao(): AppFilterDao
    abstract fun statsDao(): StatsDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "clearshield.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_stats` (
                        `dateString` TEXT NOT NULL,
                        `totalQueries` INTEGER NOT NULL DEFAULT 0,
                        `blockedQueries` INTEGER NOT NULL DEFAULT 0,
                        `adsBlocked` INTEGER NOT NULL DEFAULT 0,
                        `trackersBlocked` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`dateString`)
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val current = INSTANCE
                if (current != null) return@synchronized current

                val appContext = context.applicationContext
                val db = try {
                    buildDatabase(appContext)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Database initialization failed; attempting recovery", e)
                    try {
                        appContext.deleteDatabase(DB_NAME)
                    } catch (delEx: Exception) {
                        android.util.Log.w(TAG, "Failed to delete corrupted database: ${delEx.message}")
                    }
                    try {
                        buildDatabase(appContext)
                    } catch (e2: Exception) {
                        android.util.Log.e(TAG, "Recovery on disk failed; falling back to in-memory database", e2)
                        Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
                            .fallbackToDestructiveMigration()
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build()
                    }
                }
                INSTANCE = db
                db
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2)
             .fallbackToDestructiveMigration()
             .fallbackToDestructiveMigrationOnDowngrade()
             .build()
        }
    }
}
