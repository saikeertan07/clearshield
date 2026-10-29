package com.clearshield.adblocker

import com.clearshield.adblocker.engine.dns.DnsPacket
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketTest {

    @Test
    fun testParseStandardDnsQuery() {
        // Construct a DNS query for "google.com" Type A
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x1234.toShort()) // ID = 0x1234
        buf.putShort(0x0100.toShort()) // Standard query, RD=1
        buf.putShort(1.toShort())      // QDCOUNT = 1
        buf.putShort(0.toShort())      // ANCOUNT = 0
        buf.putShort(0.toShort())      // NSCOUNT = 0
        buf.putShort(0.toShort())      // ARCOUNT = 0

        // QNAME: 6google3com0
        buf.put(6.toByte())
        buf.put("google".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())

        buf.putShort(1.toShort()) // QTYPE = A (1)
        buf.putShort(1.toShort()) // QCLASS = IN (1)

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val packet = DnsPacket.parse(raw)
        assertNotNull(packet)
        assertEquals(0x1234, packet!!.header.id)
        assertEquals(1, packet.questions.size)
        assertEquals("google.com", packet.questions[0].qName)
        assertEquals(DnsPacket.TYPE_A, packet.questions[0].qType)
    }

    @Test
    fun testBuildBlockedResponseWithZeroIp() {
        // Query for ads.example.com
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x5678.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // 3ads7example3com0
        buf.put(3.toByte())
        buf.put("ads".toByteArray(Charsets.US_ASCII))
        buf.put(7.toByte())
        buf.put("example".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(1.toShort()) // Type A
        buf.putShort(1.toShort()) // Class IN

        val rawQuery = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, rawQuery, 0, rawQuery.size)

        val query = DnsPacket.parse(rawQuery)
        assertNotNull(query)

        val responseBytes = DnsPacket.buildBlockedResponse(query!!, asNxDomain = false)
        val response = DnsPacket.parse(responseBytes)
        assertNotNull(response)

        assertEquals(0x5678, response!!.header.id)
        assertTrue(response.header.qr) // Response flag set
        assertEquals(0, response.header.rcode) // NoError
    }

    @Test
    fun testBuildBlockedResponseWithNxDomain() {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0xABCD.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(3.toByte())
        buf.put("bad".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val rawQuery = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, rawQuery, 0, rawQuery.size)
        val query = DnsPacket.parse(rawQuery)!!

        val responseBytes = DnsPacket.buildBlockedResponse(query, asNxDomain = true)
        val response = DnsPacket.parse(responseBytes)
        assertNotNull(response)
        assertEquals(3, response!!.header.rcode) // NXDOMAIN
    }

    @Test
    fun testBlockedResponseForHttpsTypeGivesNoData() {
        // Query for HTTPS type (65)
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x7777.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(3.toByte())
        buf.put("ads".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(DnsPacket.TYPE_HTTPS.toShort()) // Type HTTPS (65)
        buf.putShort(1.toShort())

        val rawQuery = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, rawQuery, 0, rawQuery.size)
        val query = DnsPacket.parse(rawQuery)!!

        val responseBytes = DnsPacket.buildBlockedResponse(query, asNxDomain = false)
        val response = DnsPacket.parse(responseBytes)
        assertNotNull(response)
        assertEquals(0, response!!.header.rcode) // NOERROR
        assertEquals(0, response.header.anCount) // NODATA (ANCOUNT = 0)
    }

    @Test
    fun testBlockedResponseForAaaaType() {
        // Query for AAAA type (28)
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x8888.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(3.toByte())
        buf.put("ads".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(DnsPacket.TYPE_AAAA.toShort()) // Type AAAA (28)
        buf.putShort(1.toShort())

        val rawQuery = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, rawQuery, 0, rawQuery.size)
        val query = DnsPacket.parse(rawQuery)!!

        val responseBytes = DnsPacket.buildBlockedResponse(query, asNxDomain = false)
        val response = DnsPacket.parse(responseBytes)
        assertNotNull(response)
        assertEquals(0, response!!.header.rcode)
        assertEquals(1, response.header.anCount)
    }

    @Test
    fun testCompressionPointerLoopProtection() {
        // Construct packet where question has compression pointer looping to itself
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x9999.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Pointer at offset 12 pointing to offset 12
        buf.put(0xC0.toByte())
        buf.put(12.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        // Must reject cleanly without infinite loop or crash
        val parsed = DnsPacket.parse(raw)
        assertNull("Cyclical pointer packet must be safely rejected", parsed)
    }

    @Test
    fun testOutOfBoundsCompressionPointer() {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0xAAAA.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Pointer at offset 12 pointing to offset 100 (beyond packet length)
        buf.put(0xC0.toByte())
        buf.put(100.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val parsed = DnsPacket.parse(raw)
        assertNull("Out-of-bounds pointer packet must be safely rejected", parsed)
    }

    @Test
    fun testMalformedShortPacket() {
        val shortBytes = byteArrayOf(0x01, 0x02, 0x03)
        assertNull("Packet shorter than 12 bytes header must return null", DnsPacket.parse(shortBytes))
    }

    @Test
    fun testOversizedLabelRejected() {
        val buf = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0xBBBB.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Label length = 64 (RFC 1035 specifies max 63)
        buf.put(64.toByte())
        for (i in 0 until 64) {
            buf.put('a'.code.toByte())
        }
        buf.put(0.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val parsed = DnsPacket.parse(raw)
        assertNull("Labels exceeding 63 bytes must be rejected", parsed)
    }

    @Test
    fun testMultiHopPointerLoopRejected() {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0xCCCC.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Pointer at offset 12 pointing to offset 14
        buf.put(0xC0.toByte())
        buf.put(14.toByte())
        // Pointer at offset 14 pointing to offset 12 (indirect cyclical loop)
        buf.put(0xC0.toByte())
        buf.put(12.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val parsed = DnsPacket.parse(raw)
        assertNull("Indirect multi-hop pointer loops must be rejected", parsed)
    }

    @Test
    fun testValidCompressionPointerResolution() {
        // Build packet with 2 questions where Q2 points to domain in Q1
        val buf = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0xDDDD.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(2.toShort()) // 2 questions
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Q1 at offset 12: "example.com"
        buf.put(7.toByte())
        buf.put("example".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        // Q2 at offset 29: pointer to offset 12 (0xC00C)
        buf.put(0xC0.toByte())
        buf.put(12.toByte())
        buf.putShort(28.toShort()) // AAAA
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val parsed = DnsPacket.parse(raw)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.questions.size)
        assertEquals("example.com", parsed.questions[0].qName)
        assertEquals("example.com", parsed.questions[1].qName)
        assertEquals(28, parsed.questions[1].qType)
    }

    @Test
    fun testBlockedResponseWithExtendedDnsError() {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x1234.toShort())
        buf.putShort(0x0100.toShort()) // RD = 1
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(3.toByte())
        buf.put("ads".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(DnsPacket.TYPE_A.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)
        val query = DnsPacket.parse(raw)!!

        val responseBytes = DnsPacket.buildBlockedResponse(
            query = query,
            asNxDomain = false,
            includeEde = true,
            edeText = "Filtered by ClearShield"
        )

        val parsed = DnsPacket.parse(responseBytes)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.header.anCount)
        assertEquals(1, parsed.header.arCount)
    }

    @Test
    fun testRootZoneQueryParsing() {
        val buf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x5678.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        buf.put(0.toByte())
        buf.putShort(DnsPacket.TYPE_NS.toShort())
        buf.putShort(1.toShort())

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)

        val parsed = DnsPacket.parse(raw)
        assertNotNull("Root zone query must parse successfully", parsed)
        assertEquals(1, parsed!!.questions.size)
        assertEquals(".", parsed.questions[0].qName)
        assertEquals(DnsPacket.TYPE_NS, parsed.questions[0].qType)
    }
}
