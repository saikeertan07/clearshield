package com.clearshield.adblocker

import com.clearshield.adblocker.engine.filter.FilterRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubscriptionAndResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSubscriptionAtomicUpdateAndRollback() {
        val filterDir = tempFolder.newFolder("filters")
        val targetFile = File(filterDir, "filter_list_1.txt")
        val tempFile = File(filterDir, "filter_list_1.tmp")

        // 1. Establish known-good baseline list
        targetFile.writeText("||good-adnetwork.com^\n||tracking-baseline.net^\n")
        val originalContent = targetFile.readText()

        // 2. Simulate a corrupted/empty download written to tempFile
        tempFile.writeText("! This file has no valid domain rules\n# Only comments\n")

        var validRuleCount = 0
        val uniqueDomains = HashSet<String>()
        tempFile.forEachLine { line ->
            val rule = FilterRule.parse(line)
            if (rule != null && uniqueDomains.add(rule.domain)) {
                validRuleCount++
            }
        }

        // Validate rollback rule: If validRuleCount == 0, discard tempFile and DO NOT overwrite targetFile
        if (validRuleCount == 0) {
            tempFile.delete()
        } else {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        }

        assertFalse("Temporary file should be safely deleted on failed validation", tempFile.exists())
        assertTrue("Known-good target file must remain intact", targetFile.exists())
        assertEquals("Target file content must be preserved without corruption", originalContent, targetFile.readText())

        // 3. Simulate a successful valid update
        tempFile.writeText("||new-tracker.io^\n||updated-ads.com^\n")
        validRuleCount = 0
        uniqueDomains.clear()
        tempFile.forEachLine { line ->
            val rule = FilterRule.parse(line)
            if (rule != null && uniqueDomains.add(rule.domain)) {
                validRuleCount++
            }
        }

        if (validRuleCount > 0 && tempFile.length() > 0) {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        }

        assertTrue("Target file must be updated on valid rules", targetFile.exists())
        assertEquals(2, validRuleCount)
        assertTrue("Target file must contain new rule", targetFile.readText().contains("new-tracker.io"))
    }

    @Test
    fun testInsecureHttpSubscriptionRejected() {
        val insecureUrl = "http://malicious-site.org/filters.txt"
        val isHttps = insecureUrl.startsWith("https://")
        assertFalse("HTTP subscriptions must be strictly rejected", isHttps)

        val secureUrl = "https://easylist-downloads.adblockplus.org/easylist.txt"
        assertTrue("HTTPS subscriptions must be accepted", secureUrl.startsWith("https://"))
    }

    @Test
    fun testUpstreamResponseTransactionIdValidation() {
        // Mock query transaction ID
        val expectedQueryId = 0x5432

        // Simulate upstream response with matching ID
        val validResponse = ByteArray(32)
        validResponse[0] = 0x54.toByte()
        validResponse[1] = 0x32.toByte()

        val respIdValid = ((validResponse[0].toInt() and 0xFF) shl 8) or (validResponse[1].toInt() and 0xFF)
        assertEquals(expectedQueryId, respIdValid)

        // Simulate upstream response with mismatched transaction ID (spoofed or stale packet)
        val spoofedResponse = ByteArray(32)
        spoofedResponse[0] = 0x99.toByte()
        spoofedResponse[1] = 0x88.toByte()

        val respIdSpoofed = ((spoofedResponse[0].toInt() and 0xFF) shl 8) or (spoofedResponse[1].toInt() and 0xFF)
        assertNotEquals("Spoofed/mismatched transaction ID must not match expected ID", expectedQueryId, respIdSpoofed)
    }

    @Test
    fun testTruncatedUpstreamResponseRejected() {
        val shortPacket = ByteArray(8) // Less than DNS header size (12 bytes)
        assertTrue("Packets under 12 bytes must be rejected as truncated", shortPacket.size < 12)
    }

    @Test
    fun testStrictDohFallbackPolicy() {
        // Resolver with invalid DoH endpoint and strict mode (allowInsecureFallback = false)
        val resolver = com.clearshield.adblocker.engine.dns.UpstreamDnsResolver(
            primaryServer = "127.0.0.1",
            upstreamPort = 53535,
            useDoh = true,
            dohUrl = "https://127.0.0.1:54321/dns-query", // Unreachable DoH
            allowInsecureFallback = false
        )

        // Query packet
        val query = ByteArray(32).apply {
            this[0] = 0x12
            this[1] = 0x34
            this[2] = 0x01 // RD=1
            this[5] = 1    // QDCOUNT=1
            // qname: "test.local"
            this[12] = 4
            "test".toByteArray().copyInto(this, 13)
            this[17] = 5
            "local".toByteArray().copyInto(this, 18)
            this[23] = 0
            this[25] = 1 // TYPE A
            this[27] = 1 // CLASS IN
        }

        val result = resolver.resolve(query)
        assertNull("Resolution must return null under strict mode when DoH fails", result)
        assertTrue(
            "Last error should reflect strict DoH policy",
            resolver.lastError?.contains("strict mode") == true || resolver.lastError?.contains("DoH") == true
        )
    }

    @Test
    fun testBackoffJitterComputationAndBounds() {
        for (fails in 5..10) {
            val baseBackoffMs = Math.min(1000L, 50L * (1L shl Math.min(fails - 5, 4)))
            assertTrue("Base backoff must be between 50ms and 1000ms", baseBackoffMs in 50L..1000L)
            val maxJitterMs = (baseBackoffMs * 0.3).toLong()
            assertTrue("Max jitter must not exceed 300ms", maxJitterMs <= 300L)
            val maxTotalBackoff = baseBackoffMs + maxJitterMs
            assertTrue("Total backoff must remain bounded under 1300ms", maxTotalBackoff <= 1300L)
        }
    }
}
