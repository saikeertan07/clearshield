package com.clearshield.adblocker.data.repository

import com.clearshield.adblocker.data.db.dao.StatsDao
import com.clearshield.adblocker.data.db.entities.DailyStatsEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class StatsRepository(private val statsDao: StatsDao) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // In-memory atomic counters for high throughput and zero disk I/O on query hot path
    private val pendingTotal = AtomicLong(0)
    private val pendingBlocked = AtomicLong(0)
    private val pendingAds = AtomicLong(0)
    private val pendingTrackers = AtomicLong(0)

    private var flushJob: Job? = null

    init {
        // Periodic background flusher: commits batched stats every 10 seconds
        flushJob = coroutineScope.launch {
            while (isActive) {
                delay(10000)
                flushPendingStats()
            }
        }
    }

    fun getRecentStats(): Flow<List<DailyStatsEntity>> = statsDao.getRecentStats()

    fun recordQuery(isBlocked: Boolean, isTracker: Boolean) {
        pendingTotal.incrementAndGet()
        if (isBlocked) {
            pendingBlocked.incrementAndGet()
            if (isTracker) {
                pendingTrackers.incrementAndGet()
            } else {
                pendingAds.incrementAndGet()
            }
        }

        // If batch reaches 50 queries, trigger immediate async flush
        if (pendingTotal.get() >= 50) {
            coroutineScope.launch { flushPendingStats() }
        }
    }

    suspend fun flushPendingStats() = withContext(Dispatchers.IO) {
        val total = pendingTotal.getAndSet(0)
        if (total == 0L) return@withContext

        val blocked = pendingBlocked.getAndSet(0)
        val ads = pendingAds.getAndSet(0)
        val trackers = pendingTrackers.getAndSet(0)

        val today = dateFormat.format(Date())
        val current = statsDao.getStatsForDate(today) ?: DailyStatsEntity(dateString = today)
        val updated = current.copy(
            totalQueries = current.totalQueries + total,
            blockedQueries = current.blockedQueries + blocked,
            adsBlocked = current.adsBlocked + ads,
            trackersBlocked = current.trackersBlocked + trackers
        )
        statsDao.insertOrUpdateStats(updated)
    }

    suspend fun clearAllStats() = withContext(Dispatchers.IO) {
        pendingTotal.set(0)
        pendingBlocked.set(0)
        pendingAds.set(0)
        pendingTrackers.set(0)
        statsDao.clearStats()
    }
}
