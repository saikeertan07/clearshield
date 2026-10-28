package com.clearshield.adblocker

import com.clearshield.adblocker.engine.dns.DnsCacheKey
import com.clearshield.adblocker.engine.dns.DnsResponseCache
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsResponseCacheTest {

    private fun buildMockDnsResponse(
        id: Int = 0x1234,
        qName: String = "example.com",
        qType: Int = 1,
        qClass: Int = 1,
        ttlSeconds: Int = 60,
        rcode: Int = 0
    ): ByteArray {
        val labels = qName.split('.')
        val buf = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)

        // Header (12 bytes)
        buf.putShort(id.toShort())
        val flags = 0x8180 or (rcode and 0x0F) // QR=1, RD=1, RA=1
        buf.putShort(flags.toShort())
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(1.toShort()) // ANCOUNT = 1
        buf.putShort(0.toShort()) // NSCOUNT = 0
        buf.putShort(0.toShort()) // ARCOUNT = 0

        // Question
        for (l in labels) {
            buf.put(l.length.toByte())
            buf.put(l.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0.toByte())
        buf.putShort(qType.toShort())
        buf.putShort(qClass.toShort())

        // Answer
        // Name compression pointer to question offset 12 (0xC00C)
        buf.put(0xC0.toByte())
        buf.put(12.toByte())
        buf.putShort(qType.toShort())
        buf.putShort(qClass.toShort())
        buf.putInt(ttlSeconds) // 4 bytes TTL
        buf.putShort(4.toShort()) // RDLENGTH = 4 (IPv4)
        buf.put(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)) // 93.184.216.34

        val result = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, result, 0, result.size)
        return result
    }

    @Test
    fun testCacheHitWithTransactionIdRewrite() {
        val cache = DnsResponseCache(maxEntries = 10)
        val key = DnsCacheKey("example.com", 1, 1, "1.1.1.1")

        val rawResponse = buildMockDnsResponse(id = 0x1234, ttlSeconds = 120)
        cache.put(key, rawResponse)

        assertEquals(1, cache.size)

        // Query with new transaction ID 0x5678
        val cached = cache.get(key, currentQueryId = 0x5678)
        assertNotNull("Cached response should be returned", cached)

        // Check that transaction ID was rewritten to 0x5678
        val parsedBuf = ByteBuffer.wrap(cached!!).order(ByteOrder.BIG_ENDIAN)
        val returnedId = parsedBuf.short.toInt() and 0xFFFF
        assertEquals(0x5678, returnedId)
    }

    @Test
    fun testCacheMiss() {
        val cache = DnsResponseCache(maxEntries = 10)
        val key = DnsCacheKey("unknown.org", 1, 1, "1.1.1.1")
        assertNull(cache.get(key, 0x1111))
    }

    @Test
    fun testNonZeroRcodeNotCached() {
        val cache = DnsResponseCache(maxEntries = 10)
        val key = DnsCacheKey("nxdomain.org", 1, 1, "1.1.1.1")

        // RCODE = 3 (NXDOMAIN)
        val nxResponse = buildMockDnsResponse(rcode = 3)
        cache.put(key, nxResponse)

        // Must NOT cache error responses
        assertEquals(0, cache.size)
        assertNull(cache.get(key, 0x1234))
    }

    @Test
    fun testNegativeCachingNxDomainWhenEnabled() {
        val cache = DnsResponseCache(maxEntries = 10, enableNegativeCaching = true)
        val key = DnsCacheKey("nxdomain.org", 1, 1, "1.1.1.1")

        val nxResponse = buildMockDnsResponse(id = 0x1111, rcode = 3)
        cache.put(key, nxResponse)

        assertEquals("Negative response must be cached when enableNegativeCaching is true", 1, cache.size)
        val cached = cache.get(key, 0x2222)
        assertNotNull(cached)

        val returnedId = ((cached!![0].toInt() and 0xFF) shl 8) or (cached[1].toInt() and 0xFF)
        assertEquals(0x2222, returnedId)
    }

    @Test
    fun testInvalidateAll() {
        val cache = DnsResponseCache(maxEntries = 10)
        val key1 = DnsCacheKey("a.com", 1, 1, "1.1.1.1")
        val key2 = DnsCacheKey("b.com", 1, 1, "1.1.1.1")

        cache.put(key1, buildMockDnsResponse(qName = "a.com"))
        cache.put(key2, buildMockDnsResponse(qName = "b.com"))
        assertEquals(2, cache.size)

        cache.invalidateAll()
        assertEquals(0, cache.size)
        assertNull(cache.get(key1, 0x1234))
    }

    @Test
    fun testCacheKeySeparationByQType() {
        val cache = DnsResponseCache(maxEntries = 10)
        val keyA = DnsCacheKey.create("example.com", 1, 1, "1.1.1.1:53") // Type A
        val keyAaaa = DnsCacheKey.create("example.com", 28, 1, "1.1.1.1:53") // Type AAAA

        val respA = buildMockDnsResponse(qName = "example.com", qType = 1)
        val respAaaa = buildMockDnsResponse(qName = "example.com", qType = 28)

        cache.put(keyA, respA)
        cache.put(keyAaaa, respAaaa)

        assertEquals(2, cache.size)
        assertNotNull(cache.get(keyA, 0x1111))
        assertNotNull(cache.get(keyAaaa, 0x2222))
    }

    @Test
    fun testCacheKeySeparationByUpstream() {
        val cache = DnsResponseCache(maxEntries = 10)
        val key1 = DnsCacheKey.create("example.com", 1, 1, "1.1.1.1:53")
        val key2 = DnsCacheKey.create("example.com", 1, 1, "8.8.8.8:53")

        cache.put(key1, buildMockDnsResponse(qName = "example.com"))

        assertEquals(1, cache.size)
        assertNotNull("Should hit cache for 1.1.1.1", cache.get(key1, 0x1234))
        assertNull("Should miss cache for different upstream 8.8.8.8", cache.get(key2, 0x1234))
    }

    @Test
    fun testNormalizedQNameMatching() {
        val cache = DnsResponseCache(maxEntries = 10)
        val keyUpper = DnsCacheKey.create("EXAMPLE.COM.", 1, 1, "1.1.1.1:53")
        val keyLower = DnsCacheKey.create("example.com", 1, 1, "1.1.1.1:53")

        cache.put(keyUpper, buildMockDnsResponse(qName = "example.com"))

        assertEquals(1, cache.size)
        assertNotNull("Lower-case query must hit entry stored with uppercase/trailing dot", cache.get(keyLower, 0x5555))
    }

    @Test
    fun testTtlExpiryEviction() {
        // Cache with minAllowedTtlSeconds = 1 for testing
        val cache = DnsResponseCache(maxEntries = 10, minAllowedTtlSeconds = 1)
        val key = DnsCacheKey.create("short-ttl.com", 1, 1, "1.1.1.1:53")

        // Put with TTL = 1 second
        cache.put(key, buildMockDnsResponse(qName = "short-ttl.com", ttlSeconds = 1))
        assertEquals(1, cache.size)

        // Immediate retrieval should succeed
        assertNotNull(cache.get(key, 0x1234))

        // Wait 1.2 seconds to expire
        Thread.sleep(1200)

        // Retrieval after expiration should return null and evict
        assertNull("Expired entry must return null", cache.get(key, 0x5678))
        assertEquals("Expired entry must be evicted from cache", 0, cache.size)
    }

    @Test
    fun testCacheDoesNotStoreZeroTtl() {
        val cache = DnsResponseCache(maxEntries = 10, minAllowedTtlSeconds = 5)
        val key = DnsCacheKey.create("zero-ttl.com", 1, 1, "1.1.1.1:53")

        // TTL = 0 seconds (transient response)
        cache.put(key, buildMockDnsResponse(qName = "zero-ttl.com", ttlSeconds = 0))
        assertEquals("Zero-TTL response must not be cached", 0, cache.size)
        assertNull(cache.get(key, 0x9999))
    }

    @Test
    fun testBoundedCapacityLruEviction() {
        val capacity = 5
        val cache = DnsResponseCache(maxEntries = capacity)

        for (i in 1..8) {
            val key = DnsCacheKey.create("domain$i.com", 1, 1, "1.1.1.1:53")
            cache.put(key, buildMockDnsResponse(qName = "domain$i.com", ttlSeconds = 300))
        }

        assertEquals("Cache size must never exceed configured maximum capacity", capacity, cache.size)

        // Oldest entries (domain1, domain2, domain3) should have been evicted
        val oldKey = DnsCacheKey.create("domain1.com", 1, 1, "1.1.1.1:53")
        assertNull("Eldest entry must be evicted under capacity limit", cache.get(oldKey, 0x1111))

        // Newest entry (domain8) must exist
        val newKey = DnsCacheKey.create("domain8.com", 1, 1, "1.1.1.1:53")
        assertNotNull("Recent entry must be present", cache.get(newKey, 0x8888))
    }

    @Test
    fun testConcurrentCacheAccess() {
        val cache = DnsResponseCache(maxEntries = 100)
        val threads = 10
        val operationsPerThread = 100
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val latch = java.util.concurrent.CountDownLatch(threads)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)

        for (t in 0 until threads) {
            executor.submit {
                try {
                    for (op in 0 until operationsPerThread) {
                        val domain = "concurrent${(t * operationsPerThread + op) % 20}.com"
                        val key = DnsCacheKey.create(domain, 1, 1, "1.1.1.1:53")
                        cache.put(key, buildMockDnsResponse(qName = domain, ttlSeconds = 120))
                        val hit = cache.get(key, op)
                        if (hit == null) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("No concurrency errors should occur during multithreaded cache operations", 0, errors.get())
        assertTrue("Cache size should be bounded by max entries", cache.size <= 100)
    }

    @Test
    fun testConsecutiveDotsKeyNormalizationAndTtlBounds() {
        val keyWithDots = DnsCacheKey.create("cdn..example...com.", 1, 1, "8.8.8.8")
        val cleanKey = DnsCacheKey.create("cdn.example.com", 1, 1, "8.8.8.8")
        assertEquals("Consecutive and trailing dots must normalize to identical cache keys", cleanKey, keyWithDots)

        val cache = DnsResponseCache(maxEntries = 10)
        cache.put(cleanKey, buildMockDnsResponse(qName = "cdn.example.com", ttlSeconds = 60))

        val hit = cache.get(keyWithDots, 0x5555)
        assertNotNull("Query with consecutive dots should hit normalized cache key", hit)
    }
}
