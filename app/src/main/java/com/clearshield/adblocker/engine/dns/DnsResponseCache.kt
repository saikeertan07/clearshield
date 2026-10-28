package com.clearshield.adblocker.engine.dns

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Composite DNS Cache Key matching Section 3:
 * Normalized QNAME + QTYPE + QCLASS + Upstream Resolver Identity
 */
data class DnsCacheKey(
    val qName: String,
    val qType: Int,
    val qClass: Int,
    val upstreamServer: String
) {
    companion object {
        fun create(rawQName: String, qType: Int, qClass: Int, upstreamServer: String): DnsCacheKey {
            var n = rawQName.trim().lowercase()
            while (n.startsWith(".")) n = n.substring(1)
            while (n.endsWith(".")) n = n.substring(0, n.length - 1)
            if (n.contains("..")) {
                n = n.replace(Regex("\\.+"), ".")
            }
            return DnsCacheKey(
                qName = n,
                qType = qType,
                qClass = qClass,
                upstreamServer = upstreamServer.trim()
            )
        }
    }
}

/**
 * Cache entry storing response data and answer section TTL offsets for dynamic decrementing.
 */
data class DnsCacheEntry(
    val responseBytes: ByteArray,
    val originalId: Int,
    val creationTimeMs: Long,
    val minTtlSeconds: Long,
    val ttlByteOffsets: List<Int>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DnsCacheEntry
        return originalId == other.originalId &&
                creationTimeMs == other.creationTimeMs &&
                minTtlSeconds == other.minTtlSeconds &&
                responseBytes.contentEquals(other.responseBytes)
    }

    override fun hashCode(): Int {
        var result = originalId
        result = 31 * result + creationTimeMs.hashCode()
        result = 31 * result + minTtlSeconds.hashCode()
        result = 31 * result + responseBytes.contentHashCode()
        return result
    }
}

/**
 * Bounded, thread-safe LRU DNS response cache with accurate TTL extraction & decrementing.
 * Adheres strictly to RFC 1035 TTL expiration semantics and Google Play / Android production standards.
 */
class DnsResponseCache(
    private val maxEntries: Int = 512,
    private val minAllowedTtlSeconds: Long = 5L,
    private val maxAllowedTtlSeconds: Long = 86400L, // 24 hours cap
    private val enableNegativeCaching: Boolean = false,
    private val defaultNegativeTtlSeconds: Long = 60L,
    private val maxNegativeTtlSeconds: Long = 300L // 5 minutes max for NXDOMAIN/NODATA (RFC 2308)
) {
    companion object {
        private const val TAG = "DnsResponseCache"
    }

    private val lock = Any()
    // LinkedHashMap with accessOrder = true provides true LRU eviction
    private val cache = object : LinkedHashMap<DnsCacheKey, DnsCacheEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DnsCacheKey, DnsCacheEntry>?): Boolean {
            return size > maxEntries
        }
    }

    val size: Int
        get() = synchronized(lock) { cache.size }

    /**
     * Attempts to retrieve a cached DNS response for the given key.
     * If valid and unexpired:
     * - Rewrites transaction ID to match current query
     * - Decrements all answer TTLs by elapsed seconds
     * - Returns a valid DNS packet
     * If expired:
     * - Evicts entry from cache and returns null
     */
    fun get(key: DnsCacheKey, currentQueryId: Int): ByteArray? {
        synchronized(lock) {
            val entry = cache[key] ?: return null
            val now = System.currentTimeMillis()
            val elapsedSeconds = (now - entry.creationTimeMs) / 1000L

            if (elapsedSeconds >= entry.minTtlSeconds) {
                // Expired: evict immediately
                cache.remove(key)
                return null
            }

            // Valid cached response: clone and adjust
            val cachedData = entry.responseBytes.copyOf()
            val buf = ByteBuffer.wrap(cachedData).order(ByteOrder.BIG_ENDIAN)

            // 1. Rewrite transaction ID
            buf.putShort(0, currentQueryId.toShort())

            // 2. Decrement each answer record's TTL
            for (offset in entry.ttlByteOffsets) {
                if (offset + 4 <= cachedData.size) {
                    val origTtl = buf.getInt(offset).toLong() and 0xFFFFFFFFL
                    val newTtl = (origTtl - elapsedSeconds).coerceIn(1L, origTtl)
                    buf.putInt(offset, newTtl.toInt())
                }
            }

            return cachedData
        }
    }

    /**
     * Inspects and stores a successful upstream DNS response into the cache.
     * Strictly rejects malformed, non-NOERROR, non-standard, or zero-TTL responses.
     */
    fun put(key: DnsCacheKey, responseData: ByteArray) {
        if (responseData.size < 12) return

        val parseResult = extractTtlsAndOffsets(responseData) ?: return
        val (minTtl, ttlOffsets) = parseResult

        // Do not cache responses with TTL <= 0
        if (minTtl < minAllowedTtlSeconds) {
            return
        }

        val effectiveTtl = minTtl.coerceAtMost(maxAllowedTtlSeconds)
        val origId = ((responseData[0].toInt() and 0xFF) shl 8) or (responseData[1].toInt() and 0xFF)

        val entry = DnsCacheEntry(
            responseBytes = responseData.copyOf(),
            originalId = origId,
            creationTimeMs = System.currentTimeMillis(),
            minTtlSeconds = effectiveTtl,
            ttlByteOffsets = ttlOffsets
        )

        synchronized(lock) {
            cache[key] = entry
        }
    }

    /**
     * Clears all cached entries (invoked when upstream server changes, allowlist changes, etc.)
     */
    fun invalidateAll() {
        synchronized(lock) {
            cache.clear()
        }
        Log.d(TAG, "DNS Response Cache invalidated.")
    }

    /**
     * Extracts minimum answer TTL and byte offsets of all TTL fields.
     * Supports both positive NOERROR caching and RFC 2308 negative caching (NXDOMAIN/NODATA).
     */
    private fun extractTtlsAndOffsets(data: ByteArray): Pair<Long, List<Int>>? {
        try {
            if (data.size < 12) return null
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            buf.position(2)
            val flags = buf.short.toInt() and 0xFFFF
            val isResponse = (flags and 0x8000) != 0
            val rcode = flags and 0x000F

            if (!isResponse) return null

            val qdCount = buf.short.toInt() and 0xFFFF
            val anCount = buf.short.toInt() and 0xFFFF
            val nsCount = buf.short.toInt() and 0xFFFF
            val arCount = buf.short.toInt() and 0xFFFF

            // Skip Question Section
            for (i in 0 until qdCount) {
                if (!skipDomainName(buf)) return null
                if (buf.remaining() < 4) return null
                buf.position(buf.position() + 4) // Skip QTYPE + QCLASS
            }

            // Positive response caching (NOERROR with answers)
            if (rcode == 0 && anCount > 0) {
                var minTtl = Long.MAX_VALUE
                val ttlOffsets = mutableListOf<Int>()

                // Parse Answer Section
                for (i in 0 until anCount) {
                    if (!skipDomainName(buf)) return null
                    if (buf.remaining() < 10) return null // TYPE (2) + CLASS (2) + TTL (4) + RDLENGTH (2)

                    buf.position(buf.position() + 4) // Skip TYPE + CLASS

                    val ttlOffset = buf.position()
                    val ttl = buf.int.toLong() and 0xFFFFFFFFL
                    ttlOffsets.add(ttlOffset)
                    if (ttl < minTtl) {
                        minTtl = ttl
                    }

                    val rdLength = buf.short.toInt() and 0xFFFF
                    if (buf.remaining() < rdLength) return null
                    buf.position(buf.position() + rdLength)
                }

                return if (minTtl != Long.MAX_VALUE && ttlOffsets.isNotEmpty()) {
                    Pair(minTtl, ttlOffsets)
                } else null
            }

            // RFC 2308 Negative Caching: NXDOMAIN (rcode=3) or NODATA (rcode=0, anCount=0)
            if (enableNegativeCaching && (rcode == 3 || (rcode == 0 && anCount == 0))) {
                var soaTtl: Long? = null
                var soaMinimum: Long? = null
                val ttlOffsets = mutableListOf<Int>()

                // Scan Authority Section for SOA record (TYPE 6)
                for (i in 0 until nsCount) {
                    if (!skipDomainName(buf)) break
                    if (buf.remaining() < 10) break
                    val type = buf.short.toInt() and 0xFFFF
                    val clazz = buf.short.toInt() and 0xFFFF
                    val ttlOffset = buf.position()
                    val ttl = buf.int.toLong() and 0xFFFFFFFFL
                    val rdLength = buf.short.toInt() and 0xFFFF

                    if (type == 6 /* SOA */ && buf.remaining() >= rdLength) {
                        soaTtl = ttl
                        ttlOffsets.add(ttlOffset)
                        val rdataStart = buf.position()
                        // SOA RDATA: MNAME, RNAME, SERIAL (4), REFRESH (4), RETRY (4), EXPIRE (4), MINIMUM (4)
                        if (skipDomainName(buf) && skipDomainName(buf) && buf.remaining() >= 20) {
                            buf.position(buf.position() + 16) // skip SERIAL, REFRESH, RETRY, EXPIRE
                            soaMinimum = buf.int.toLong() and 0xFFFFFFFFL
                        }
                        buf.position(rdataStart + rdLength)
                        break
                    } else {
                        if (buf.remaining() < rdLength) break
                        buf.position(buf.position() + rdLength)
                    }
                }

                // Per RFC 2308: negative TTL is min(SOA.TTL, SOA.MINIMUM), capped to bounded window
                val effectiveTtl = when {
                    soaTtl != null && soaMinimum != null -> minOf(soaTtl, soaMinimum)
                    soaTtl != null -> soaTtl
                    else -> defaultNegativeTtlSeconds
                }.coerceIn(minAllowedTtlSeconds, maxNegativeTtlSeconds)

                return Pair(effectiveTtl, ttlOffsets)
            }

            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun skipDomainName(buf: ByteBuffer): Boolean {
        var hops = 0
        while (buf.hasRemaining() && hops < 64) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) return true
            if ((len and 0xC0) == 0xC0) {
                // Pointer: 1 more byte
                if (!buf.hasRemaining()) return false
                buf.get()
                return true
            } else if ((len and 0xC0) == 0) {
                // Label: skip len bytes
                if (buf.remaining() < len) return false
                buf.position(buf.position() + len)
            } else {
                return false
            }
            hops++
        }
        return false
    }
}
