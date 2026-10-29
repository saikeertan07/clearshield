package com.clearshield.adblocker.data.repository

import android.content.Context
import android.util.Log
import com.clearshield.adblocker.data.db.dao.FilterDao
import com.clearshield.adblocker.data.db.entities.AllowlistEntity
import com.clearshield.adblocker.data.db.entities.CustomBlockEntity
import com.clearshield.adblocker.data.db.entities.FilterListEntity
import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.engine.filter.FilterRule
import com.clearshield.adblocker.engine.filter.RuleType
import com.clearshield.adblocker.engine.filter.ThreatCategory
import com.clearshield.adblocker.service.ClearShieldVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class FilterRepository(
    private val context: Context,
    private val filterDao: FilterDao
) {
    companion object {
        private const val TAG = "FilterRepository"

        fun sanitizeDomainInput(raw: String): String {
            var clean = raw.trim().lowercase()
            clean = clean.removePrefix("https://").removePrefix("http://")
            clean = clean.removePrefix("@@||").removePrefix("||").removePrefix("@@")
            clean = clean.removeSuffix("^")
            if (clean.contains("/")) clean = clean.substringBefore("/")
            if (clean.contains(":") && !clean.contains("::")) clean = clean.substringBefore(":")
            clean = clean.trimStart('*', '.').trimEnd('.')
            if (clean.contains("..")) {
                clean = clean.replace(Regex("\\.+"), ".")
            }
            return clean
        }
    }

    private val filterDir: File
        get() = File(context.filesDir, "filters").apply { if (!exists()) mkdirs() }

    fun getAllFilterLists(): Flow<List<FilterListEntity>> = filterDao.getAllFilterLists()
    fun getAllAllowlist(): Flow<List<AllowlistEntity>> = filterDao.getAllAllowlist()
    fun getAllCustomBlocklist(): Flow<List<CustomBlockEntity>> = filterDao.getAllCustomBlocklist()

    suspend fun initDefaultFilterListsIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val lists = filterDao.getEnabledFilterLists()
            if (lists.isEmpty()) {
                val defaultList = FilterListEntity(
                    name = "ClearShield Core Ad & Tracker Blocklist",
                    url = "bundled://default_filters.txt",
                    isEnabled = true,
                    description = "High impact ad networks, analytics beacons, and tracking telemetry",
                    ruleCount = 0
                )
                val peterLowe = FilterListEntity(
                    name = "Peter Lowe's Ad & Tracking Server List",
                    url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                    isEnabled = true,
                    description = "Curated list of ad-serving and tracking servers (Hosts format)",
                    ruleCount = 0
                )
                val adguard = FilterListEntity(
                    name = "AdGuard DNS Filter",
                    url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
                    isEnabled = false,
                    description = "Standard DNS filter containing ad networks and mobile trackers",
                    ruleCount = 0
                )
                filterDao.insertFilterList(defaultList)
                filterDao.insertFilterList(peterLowe)
                filterDao.insertFilterList(adguard)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing default filter lists", t)
        }
    }

    suspend fun reloadAndCompileAllRules() = withContext(Dispatchers.IO) {
        val explicitAllow = mutableListOf<FilterRule>()
        val customBlock = mutableListOf<FilterRule>()
        val defaultBlock = mutableListOf<FilterRule>()
        val subscriptionBlock = mutableListOf<FilterRule>()

        // 1. Tier 1: Load Allowlist from DB (highest priority)
        try {
            val allowItems = filterDao.getEnabledAllowlist()
            for (item in allowItems) {
                val clean = sanitizeDomainInput(item.domain)
                if (clean.isNotEmpty()) {
                    val rule = FilterRule(
                        domain = clean,
                        type = RuleType.ALLOW,
                        matchSubdomains = true,
                        category = ThreatCategory.GENERAL,
                        rawRule = "@@||$clean^"
                    )
                    explicitAllow.add(rule)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading allowlist from DB", t)
        }

        // 2. Tier 2: Load Custom Blocklist from DB
        try {
            val customBlocks = filterDao.getEnabledCustomBlocklist()
            for (item in customBlocks) {
                val clean = sanitizeDomainInput(item.domain)
                if (clean.isNotEmpty()) {
                    val rule = FilterRule(
                        domain = clean,
                        type = RuleType.BLOCK,
                        matchSubdomains = true,
                        category = ThreatCategory.GENERAL,
                        rawRule = "||$clean^"
                    )
                    customBlock.add(rule)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading custom blocklist from DB", t)
        }

        // 3. Tier 4: Load bundled asset core filters
        try {
            context.assets.open("default_filters.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    FilterRule.parse(line)?.let { defaultBlock.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled asset filters", e)
        }

        // 4. Tier 3: Load cached downloaded filter subscriptions from persistent filesDir
        try {
            val enabledLists = filterDao.getEnabledFilterLists()
            for (listEntity in enabledLists) {
                if (listEntity.url.startsWith("bundled://")) continue
                val cacheFile = File(filterDir, "filter_list_${listEntity.id}.txt")
                if (cacheFile.exists()) {
                    try {
                        cacheFile.bufferedReader().forEachLine { line ->
                            FilterRule.parse(line)?.let { subscriptionBlock.add(it) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading cached filter list ${listEntity.id}", e)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading cached filter lists", t)
        }

        FilterEngine.instance.loadTiers(
            explicitAllow = explicitAllow,
            customBlock = customBlock,
            subscriptionBlock = subscriptionBlock,
            defaultBlock = defaultBlock
        )
        ClearShieldVpnService.invalidateDnsCache()
    }

    /**
     * Downloads and parses a filter list with atomic replacement and automatic rollback.
     * If the network fails or returns invalid content, the existing known-good cache is retained.
     */
    suspend fun updateFilterListFromNetwork(listEntity: FilterListEntity): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (listEntity.url.startsWith("bundled://")) {
                var count = 0
                context.assets.open("default_filters.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                        if (FilterRule.parse(line) != null) count++
                    }
                }
                val updated = listEntity.copy(ruleCount = count, lastUpdated = System.currentTimeMillis())
                filterDao.updateFilterList(updated)
                reloadAndCompileAllRules()
                return@withContext Result.success(count)
            }

            if (!listEntity.url.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("Insecure filter subscription rejected: HTTPS is strictly required."))
            }

            val url = URL(listEntity.url)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ClearShield-AdBlocker/1.0")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}"))
            }

            val targetFile = File(filterDir, "filter_list_${listEntity.id}.txt")
            val tempFile = File(filterDir, "filter_list_${listEntity.id}.tmp")
            var count = 0
            val uniqueDomains = HashSet<String>()
            val maxBytes = 10L * 1024 * 1024 // 10 MB maximum download size
            var totalBytes = 0L

            try {
                conn.inputStream.bufferedReader().use { reader ->
                    tempFile.bufferedWriter().use { writer ->
                        reader.forEachLine { line ->
                            totalBytes += line.length + 1
                            if (totalBytes > maxBytes) {
                                throw IllegalStateException("Filter list exceeded 10MB limit")
                            }
                            val rule = FilterRule.parse(line)
                            if (rule != null && uniqueDomains.add(rule.domain)) {
                                count++
                                writer.write(line)
                                writer.newLine()
                            }
                        }
                    }
                }

                // Validation: Only commit if valid rules were found
                if (count > 0 && tempFile.length() > 0) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)

                    val updated = listEntity.copy(ruleCount = count, lastUpdated = System.currentTimeMillis())
                    filterDao.updateFilterList(updated)
                    reloadAndCompileAllRules()
                    Result.success(count)
                } else {
                    tempFile.delete()
                    Result.failure(Exception("No valid rules found in download"))
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download filter list: ${listEntity.url}", e)
            Result.failure(e)
        }
    }

    suspend fun addAllowlist(domain: String) = withContext(Dispatchers.IO) {
        val clean = sanitizeDomainInput(domain)
        if (clean.isNotEmpty()) {
            filterDao.insertAllowlist(AllowlistEntity(domain = clean))
            reloadAndCompileAllRules()
        }
    }

    suspend fun toggleAllowlist(item: AllowlistEntity) = withContext(Dispatchers.IO) {
        filterDao.updateAllowlist(item.copy(isEnabled = !item.isEnabled))
        reloadAndCompileAllRules()
    }

    suspend fun removeAllowlist(item: AllowlistEntity) = withContext(Dispatchers.IO) {
        filterDao.deleteAllowlist(item)
        reloadAndCompileAllRules()
    }

    suspend fun addCustomBlock(domain: String) = withContext(Dispatchers.IO) {
        val clean = sanitizeDomainInput(domain)
        if (clean.isNotEmpty()) {
            filterDao.insertCustomBlock(CustomBlockEntity(domain = clean))
            reloadAndCompileAllRules()
        }
    }

    suspend fun toggleCustomBlock(item: CustomBlockEntity) = withContext(Dispatchers.IO) {
        filterDao.updateCustomBlock(item.copy(isEnabled = !item.isEnabled))
        reloadAndCompileAllRules()
    }

    suspend fun removeCustomBlock(item: CustomBlockEntity) = withContext(Dispatchers.IO) {
        filterDao.deleteCustomBlock(item)
        reloadAndCompileAllRules()
    }

    suspend fun toggleFilterList(list: FilterListEntity) = withContext(Dispatchers.IO) {
        filterDao.updateFilterList(list.copy(isEnabled = !list.isEnabled))
        reloadAndCompileAllRules()
    }

    suspend fun addFilterList(name: String, url: String) = withContext(Dispatchers.IO) {
        val entity = FilterListEntity(
            name = name,
            url = url,
            isEnabled = true,
            ruleCount = 0
        )
        val id = filterDao.insertFilterList(entity)
        updateFilterListFromNetwork(entity.copy(id = id))
    }

    suspend fun removeFilterList(list: FilterListEntity) = withContext(Dispatchers.IO) {
        val cacheFile = File(filterDir, "filter_list_${list.id}.txt")
        if (cacheFile.exists()) cacheFile.delete()
        filterDao.deleteFilterList(list)
        reloadAndCompileAllRules()
    }
}
