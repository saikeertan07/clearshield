package com.clearshield.adblocker

import com.clearshield.adblocker.engine.dns.DnsCacheKey
import com.clearshield.adblocker.engine.dns.DnsPacket
import com.clearshield.adblocker.engine.dns.DnsResponseCache
import com.clearshield.adblocker.engine.dns.IpPacket
import com.clearshield.adblocker.engine.filter.DomainTrie
import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.engine.filter.FilterRule
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class PerformanceBenchmarkTest {

    private fun buildMockDnsResponse(
        id: Int = 0x1234,
        qName: String = "example.com",
        qType: Int = 1,
        qClass: Int = 1,
        ttlSeconds: Int = 60
    ): ByteArray {
        val labels = qName.split('.')
        val buf = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)

        // Header (12 bytes)
        buf.putShort(id.toShort())
        val flags = 0x8180 // QR=1, RD=1, RA=1
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
        buf.putShort(0xC00C.toShort()) // Pointer to Question Name
        buf.putShort(qType.toShort())
        buf.putShort(qClass.toShort())
        buf.putInt(ttlSeconds)
        buf.putShort(4.toShort()) // RDLENGTH = 4
        buf.put(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)) // 93.184.216.34

        val result = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, result, 0, result.size)
        return result
    }

    @Test
    fun testFilterList70kLoadingAndMemoryBenchmark() {
        val assetFile = File("src/main/assets/default_filters.txt")
        if (!assetFile.exists()) {
            println("Benchmark warning: default_filters.txt not found at relative path")
            return
        }

        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val startLoad = System.nanoTime()
        val trie = DomainTrie()
        var ruleCount = 0

        assetFile.forEachLine { line ->
            val rule = FilterRule.parse(line)
            if (rule != null) {
                trie.insert(rule)
                ruleCount++
            }
        }
        val endLoad = System.nanoTime()
        val loadDurationMs = (endLoad - startLoad) / 1_000_000.0

        System.gc()
        Thread.sleep(100)
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val memDeltaMb = (memAfter - memBefore) / (1024.0 * 1024.0)

        println("=== 70,000 RULE BENCHMARK ===")
        println("Rules parsed and inserted: $ruleCount (Trie size: ${trie.size})")
        println("Load duration: ${String.format("%.2f", loadDurationMs)} ms")
        println("Estimated heap consumption: ${String.format("%.2f", memDeltaMb)} MB")

        assertTrue("Should have loaded > 60,000 rules", trie.size > 60_000)
        assertTrue("70k rules should load within 3,000 ms on modern JVM", loadDurationMs < 3000.0)

        // Throughput benchmark over 10,000 queries
        val testDomains = listOf(
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "tracker.facebook.com",
            "telemetry.app-measurement.com",
            "sub.deep.tracker.doubleclick.net",
            "wikipedia.org",
            "github.com",
            "developer.android.com",
            "google.com",
            "stackoverflow.com"
        )

        val iterations = 10_000
        val startLookup = System.nanoTime()
        for (i in 0 until iterations) {
            val d = testDomains[i % testDomains.size]
            trie.match(d)
        }
        val endLookup = System.nanoTime()
        val avgLookupNs = (endLookup - startLookup).toDouble() / iterations
        val avgLookupUs = avgLookupNs / 1000.0

        println("10,000 Lookups total time: ${(endLookup - startLookup) / 1_000_000.0} ms")
        println("Average lookup latency: ${String.format("%.3f", avgLookupUs)} microseconds (${String.format("%.1f", avgLookupNs)} ns)")

        assertTrue("Average trie lookup latency must be under 50 microseconds", avgLookupUs < 50.0)
    }

    @Test
    fun testDnsCacheThroughputAndLatency() {
        val cache = DnsResponseCache(maxEntries = 5000)

        // Populate 100 cache entries
        for (i in 0 until 100) {
            val d = "domain$i.com"
            val key = DnsCacheKey(d, 1, 1, "1.1.1.1")
            val resp = buildMockDnsResponse(id = i, qName = d, ttlSeconds = 300)
            cache.put(key, resp)
        }

        val iterations = 20_000
        val startHit = System.nanoTime()
        for (i in 0 until iterations) {
            val d = "domain${i % 100}.com"
            val key = DnsCacheKey(d, 1, 1, "1.1.1.1")
            cache.get(key, currentQueryId = i and 0xFFFF)
        }
        val endHit = System.nanoTime()
        val avgHitNs = (endHit - startHit).toDouble() / iterations
        val avgHitUs = avgHitNs / 1000.0

        println("Cache Hit Latency (20,000 iterations): ${String.format("%.3f", avgHitUs)} microseconds (${String.format("%.1f", avgHitNs)} ns)")
        assertTrue("DNS cache hit must complete within 20 microseconds", avgHitUs < 20.0)

        // Cache Miss Latency
        val startMiss = System.nanoTime()
        for (i in 0 until 5000) {
            val key = DnsCacheKey("nonexistent$i.org", 1, 1, "1.1.1.1")
            cache.get(key, currentQueryId = 1)
        }
        val endMiss = System.nanoTime()
        val avgMissUs = ((endMiss - startMiss).toDouble() / 5000) / 1000.0
        println("Cache Miss Latency (5,000 iterations): ${String.format("%.3f", avgMissUs)} microseconds")
        assertTrue("DNS cache miss must complete within 10 microseconds", avgMissUs < 10.0)
    }

    @Test
    fun testDnsPacketFuzzSafety() {
        val rng = Random(42)
        var safelyHandled = 0

        // 1,000 mutated/random byte arrays
        for (i in 0 until 1000) {
            val len = rng.nextInt(512)
            val bytes = ByteArray(len)
            rng.nextBytes(bytes)

            try {
                // Must either parse successfully or return null; NEVER throw an uncaught exception
                val result = DnsPacket.parse(bytes)
                safelyHandled++
            } catch (e: Throwable) {
                fail("DnsPacket.parse threw uncaught exception on fuzz iteration $i: ${e.message}")
            }
        }
        assertEquals("All 1,000 fuzz packets must be safely handled without throwing", 1000, safelyHandled)
    }

    @Test
    fun testIpPacketFuzzSafety() {
        val rng = Random(123)
        var safelyHandled = 0

        for (i in 0 until 1000) {
            val len = rng.nextInt(256)
            val bytes = ByteArray(len)
            rng.nextBytes(bytes)

            try {
                val result = IpPacket.parse(bytes)
                safelyHandled++
            } catch (e: Throwable) {
                fail("IpPacket.parse threw uncaught exception on fuzz iteration $i: ${e.message}")
            }
        }
        assertEquals("All 1,000 fuzz IP packets must be safely handled without throwing", 1000, safelyHandled)
    }
}
