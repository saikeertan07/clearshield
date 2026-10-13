package com.clearshield.adblocker

import com.clearshield.adblocker.engine.dns.IpPacket
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IpPacketTest {

    private fun createSampleIpUdpPacket(
        srcIp: ByteArray = byteArrayOf(10, 233.toByte(), 1, 2),
        dstIp: ByteArray = byteArrayOf(10, 233.toByte(), 1, 1),
        srcPort: Int = 54321,
        dstPort: Int = 53,
        payload: ByteArray = "test payload".toByteArray(),
        isFragmented: Boolean = false
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipTotalLen = 20 + udpLen
        val buf = ByteBuffer.allocate(ipTotalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header (20 bytes)
        buf.put(0x45.toByte()) // Version 4, IHL 5
        buf.put(0.toByte())    // TOS
        buf.putShort(ipTotalLen.toShort())
        buf.putShort(0x1234.toShort()) // ID

        if (isFragmented) {
            buf.putShort(0x2000.toShort()) // More Fragments (MF) set
        } else {
            buf.putShort(0x4000.toShort()) // Don't Fragment (DF)
        }

        buf.put(64.toByte()) // TTL
        buf.put(17.toByte()) // Protocol UDP
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.put(srcIp)
        buf.put(dstIp)

        // Compute and insert IP checksum
        val ipChecksum = IpPacket.computeOneComplementChecksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        // UDP Header (8 bytes)
        buf.position(20)
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0.toShort()) // UDP Checksum placeholder
        buf.put(payload)

        // Compute and insert UDP checksum
        val udpChecksum = IpPacket.computeUdpChecksum(srcIp, dstIp, buf.array(), 20, udpLen)
        buf.putShort(26, udpChecksum.toShort())

        return buf.array()
    }

    @Test
    fun testParseValidUdpPacket() {
        val raw = createSampleIpUdpPacket()
        val packet = IpPacket.parse(raw)

        assertNotNull("Packet should be successfully parsed", packet)
        assertEquals(4, packet!!.version)
        assertEquals(20, packet.headerLength)
        assertEquals(raw.size, packet.totalLength)
        assertEquals(17, packet.protocol)
        assertArrayEquals(byteArrayOf(10, 233.toByte(), 1, 2), packet.srcIp)
        assertArrayEquals(byteArrayOf(10, 233.toByte(), 1, 1), packet.dstIp)

        val udp = packet.udpHeader
        assertNotNull("UDP Header should be parsed", udp)
        assertEquals(54321, udp!!.srcPort)
        assertEquals(53, udp.dstPort)
        assertEquals("test payload", String(udp.payload))
    }

    @Test
    fun testFragmentedPacketIgnored() {
        val raw = createSampleIpUdpPacket(isFragmented = true)
        val packet = IpPacket.parse(raw)

        assertNotNull("IP header is valid", packet)
        assertNull("UDP header must not be parsed for fragmented packets", packet!!.udpHeader)
    }

    @Test
    fun testTruncatedPacketRejected() {
        // Less than 20 bytes
        val shortData = ByteArray(15)
        assertNull(IpPacket.parse(shortData))

        // Total length in header greater than buffer size
        val raw = createSampleIpUdpPacket()
        raw[2] = 0x05 // Corrupt total length to 0x0500 (1280 bytes)
        raw[3] = 0x00
        assertNull(IpPacket.parse(raw))
    }

    @Test
    fun testBuildResponsePacketReversesEndpoints() {
        val queryRaw = createSampleIpUdpPacket(
            srcIp = byteArrayOf(10, 233.toByte(), 1, 2),
            dstIp = byteArrayOf(10, 233.toByte(), 1, 1),
            srcPort = 45000,
            dstPort = 53
        )
        val queryPacket = IpPacket.parse(queryRaw)!!

        val responsePayload = "dns-response-mock".toByteArray()
        val responseBytes = IpPacket.buildResponsePacket(queryPacket, responsePayload)

        val parsedResponse = IpPacket.parse(responseBytes)
        assertNotNull(parsedResponse)

        // Endpoints should be reversed
        assertArrayEquals(byteArrayOf(10, 233.toByte(), 1, 1), parsedResponse!!.srcIp)
        assertArrayEquals(byteArrayOf(10, 233.toByte(), 1, 2), parsedResponse.dstIp)

        val respUdp = parsedResponse.udpHeader
        assertNotNull(respUdp)
        assertEquals(53, respUdp!!.srcPort)
        assertEquals(45000, respUdp.dstPort)
        assertEquals("dns-response-mock", String(respUdp.payload))
    }

    @Test
    fun testChecksumCalculations() {
        val raw = createSampleIpUdpPacket()
        val ipChecksum = IpPacket.computeOneComplementChecksum(raw, 0, 20)
        // Checksum over the entire IPv4 header including its own checksum should evaluate to 0 (or 0xFFFF)
        assertEquals(0, ipChecksum)
    }

    private fun createSampleIpv6UdpPacket(
        srcIp: ByteArray = ByteArray(16).apply { this[15] = 2 },
        dstIp: ByteArray = ByteArray(16).apply { this[15] = 1 },
        srcPort: Int = 54321,
        dstPort: Int = 53,
        payload: ByteArray = "test ipv6 payload".toByteArray(),
        withHopByHop: Boolean = false,
        isContinuationFragment: Boolean = false
    ): ByteArray {
        val udpLen = 8 + payload.size
        val extLen = if (withHopByHop) 8 else if (isContinuationFragment) 8 else 0
        val payloadLen = extLen + udpLen
        val totalLen = 40 + payloadLen
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000)
        buf.putShort(payloadLen.toShort())

        if (withHopByHop) {
            buf.put(0.toByte()) // Next Header = Hop-by-Hop (0)
        } else if (isContinuationFragment) {
            buf.put(44.toByte()) // Next Header = Fragment (44)
        } else {
            buf.put(17.toByte()) // Next Header = UDP (17)
        }

        buf.put(64.toByte()) // Hop Limit
        buf.put(srcIp)
        buf.put(dstIp)

        if (withHopByHop) {
            buf.put(17.toByte()) // Next Header = UDP (17)
            buf.put(0.toByte()) // Hdr Ext Len = 0 (total 8 bytes)
            buf.put(ByteArray(6))
        } else if (isContinuationFragment) {
            buf.put(17.toByte())
            buf.put(0.toByte())
            buf.putShort((100 shl 3).toShort()) // Non-zero fragment offset
            buf.putInt(0x12345678)
        }

        val udpStart = buf.position()
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0.toShort())
        buf.put(payload)

        val udpChecksum = IpPacket.computeIpv6UdpChecksum(srcIp, dstIp, buf.array(), udpStart, udpLen)
        buf.putShort(udpStart + 6, udpChecksum.toShort())

        return buf.array()
    }

    @Test
    fun testParseValidIpv6UdpPacket() {
        val raw = createSampleIpv6UdpPacket()
        val packet = IpPacket.parse(raw)

        assertNotNull("IPv6 packet should be parsed", packet)
        assertEquals(6, packet!!.version)
        assertEquals(40, packet.headerLength)
        assertEquals(raw.size, packet.totalLength)
        assertEquals(17, packet.protocol)
        assertEquals(16, packet.srcIp.size)
        assertEquals(16, packet.dstIp.size)
        assertEquals(2, packet.srcIp[15].toInt())
        assertEquals(1, packet.dstIp[15].toInt())

        val udp = packet.udpHeader
        assertNotNull("UDP Header should be parsed", udp)
        assertEquals(54321, udp!!.srcPort)
        assertEquals(53, udp.dstPort)
        assertEquals("test ipv6 payload", String(udp.payload))
    }

    @Test
    fun testParseIpv6WithHopByHopExtensionHeader() {
        val raw = createSampleIpv6UdpPacket(withHopByHop = true)
        val packet = IpPacket.parse(raw)

        assertNotNull("IPv6 packet with Hop-by-Hop should be parsed", packet)
        assertEquals(6, packet!!.version)
        val udp = packet.udpHeader
        assertNotNull("UDP header following extension header must be found", udp)
        assertEquals(54321, udp!!.srcPort)
        assertEquals(53, udp.dstPort)
        assertEquals("test ipv6 payload", String(udp.payload))
    }

    @Test
    fun testIpv6ContinuationFragmentIgnored() {
        val raw = createSampleIpv6UdpPacket(isContinuationFragment = true)
        val packet = IpPacket.parse(raw)

        assertNotNull("IPv6 packet parsed", packet)
        assertNull("UDP header must not be parsed for continuation fragment", packet!!.udpHeader)
    }

    @Test
    fun testBuildIpv6ResponsePacketReversesEndpointsAndSetsMandatoryChecksum() {
        val queryRaw = createSampleIpv6UdpPacket(srcPort = 44555, dstPort = 53)
        val queryPacket = IpPacket.parse(queryRaw)!!

        val responsePayload = "ipv6-dns-answer".toByteArray()
        val responseBytes = IpPacket.buildResponsePacket(queryPacket, responsePayload)

        val parsed = IpPacket.parse(responseBytes)
        assertNotNull("Response should be valid IPv6 packet", parsed)
        assertEquals(6, parsed!!.version)
        assertEquals(1, parsed.srcIp[15].toInt()) // Swapped: was dst
        assertEquals(2, parsed.dstIp[15].toInt()) // Swapped: was src

        val udp = parsed.udpHeader
        assertNotNull(udp)
        assertEquals(53, udp!!.srcPort)
        assertEquals(44555, udp.dstPort)
        assertEquals("ipv6-dns-answer", String(udp.payload))
        assertNotEquals("IPv6 UDP checksum must not be 0", 0, udp.checksum)
    }

    private fun createSampleIpv4TcpSynPacket(
        srcPort: Int = 41234,
        dstPort: Int = 53,
        seqNumber: Long = 1000L
    ): ByteArray {
        val totalLen = 40
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x45.toByte())
        buf.put(0.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0x5678.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(6.toByte()) // Protocol TCP (6)
        buf.putShort(0.toShort())
        buf.put(byteArrayOf(10, 0, 0, 2))
        buf.put(byteArrayOf(10, 0, 0, 1))

        val ipChecksum = IpPacket.computeOneComplementChecksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        buf.position(20)
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putInt(seqNumber.toInt())
        buf.putInt(0) // ACK
        buf.putShort(((5 shl 12) or 0x02).toShort()) // Data offset 5 (20 bytes), SYN flag (0x02)
        buf.putShort(65535.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        return buf.array()
    }

    @Test
    fun testBuildIpv4TcpRstPacketReversesEndpointsAndSetsRstAck() {
        val raw = createSampleIpv4TcpSynPacket(srcPort = 51234, dstPort = 53, seqNumber = 5000L)
        val incoming = IpPacket.parse(raw)
        assertNotNull("Incoming TCP packet parsed", incoming)
        assertEquals(6, incoming!!.protocol)
        assertNotNull("TCP header present", incoming.tcpHeader)
        assertEquals(51234, incoming.tcpHeader!!.srcPort)
        assertEquals(53, incoming.tcpHeader!!.dstPort)

        val rstPacketBytes = IpPacket.buildTcpRstPacket(incoming)
        assertNotNull("TCP RST packet generated", rstPacketBytes)

        val parsedRst = IpPacket.parse(rstPacketBytes!!)
        assertNotNull("TCP RST packet parsed", parsedRst)
        assertEquals(4, parsedRst!!.version)
        assertEquals(6, parsedRst.protocol)
        assertArrayEquals(incoming.dstIp, parsedRst.srcIp)
        assertArrayEquals(incoming.srcIp, parsedRst.dstIp)

        val tcp = parsedRst.tcpHeader
        assertNotNull(tcp)
        assertEquals(53, tcp!!.srcPort)
        assertEquals(51234, tcp.dstPort)
        assertEquals("RST+ACK flag should be set (0x14)", 0x14, tcp.flags and 0x14)
        assertEquals("ACK number must equal SEQ + 1 for SYN", 5001L, tcp.ackNumber)
    }

    @Test
    fun testBuildIpv6TcpRstPacket() {
        val totalLen = 60 // 40-byte IPv6 + 20-byte TCP
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(0x60000000)
        buf.putShort(20.toShort()) // Payload length (TCP header = 20)
        buf.put(6.toByte()) // Next Header = TCP (6)
        buf.put(64.toByte())
        val srcIp = ByteArray(16).apply { this[15] = 2 }
        val dstIp = ByteArray(16).apply { this[15] = 1 }
        buf.put(srcIp)
        buf.put(dstIp)

        buf.putShort(54321.toShort())
        buf.putShort(53.toShort())
        buf.putInt(7777)
        buf.putInt(0)
        buf.putShort(((5 shl 12) or 0x02).toShort()) // SYN
        buf.putShort(65535.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        val incoming = IpPacket.parse(buf.array())
        assertNotNull("Incoming IPv6 TCP packet parsed", incoming)
        assertEquals(6, incoming!!.version)
        assertEquals(6, incoming.protocol)

        val rstBytes = IpPacket.buildTcpRstPacket(incoming)
        assertNotNull("IPv6 TCP RST packet generated", rstBytes)

        val parsedRst = IpPacket.parse(rstBytes!!)
        assertNotNull("IPv6 TCP RST packet parsed", parsedRst)
        assertEquals(6, parsedRst!!.version)
        assertEquals(6, parsedRst.protocol)
        assertEquals(53, parsedRst.tcpHeader!!.srcPort)
        assertEquals(54321, parsedRst.tcpHeader!!.dstPort)
        assertEquals(7778L, parsedRst.tcpHeader!!.ackNumber)
    }

    @Test
    fun testDualStackIpv6SubnetEnclosureRegression() {
        val ipv6Addr = java.net.Inet6Address.getByName(com.clearshield.adblocker.service.ClearShieldVpnService.VPN_IPV6_ADDRESS).address
        val ipv6Dns = java.net.Inet6Address.getByName(com.clearshield.adblocker.service.ClearShieldVpnService.VPN_IPV6_DNS).address

        // Verify both address and DNS reside within the same /64 subnet prefix (first 8 bytes match)
        for (i in 0 until 8) {
            assertEquals("Byte $i of IPv6 interface address and DNS must share /64 subnet", ipv6Addr[i], ipv6Dns[i])
        }
    }

    private fun createSampleDnsQuery(
        domain: String = "analytics.tracker.com",
        qType: Int = com.clearshield.adblocker.engine.dns.DnsPacket.TYPE_AAAA,
        id: Int = 0x1234
    ): ByteArray {
        val buf = ByteBuffer.allocate(64 + domain.length).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(id.toShort())
        buf.putShort(0x0100.toShort()) // RD=1
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        for (part in domain.split('.')) {
            buf.put(part.length.toByte())
            buf.put(part.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0.toByte())
        buf.putShort(qType.toShort())
        buf.putShort(1.toShort()) // IN

        val raw = ByteArray(buf.position())
        System.arraycopy(buf.array(), 0, raw, 0, raw.size)
        return raw
    }

    @Test
    fun testIpv6DnsQueryParsingAndBlockedResponseSynthesis() {
        val dnsQueryPayload = createSampleDnsQuery(
            domain = "analytics.tracker.com",
            qType = com.clearshield.adblocker.engine.dns.DnsPacket.TYPE_AAAA
        )

        val rawIpv6 = createSampleIpv6UdpPacket(
            srcIp = ByteArray(16).apply { this[15] = 42 },
            dstIp = ByteArray(16).apply { this[15] = 1 },
            srcPort = 59876,
            dstPort = 53,
            payload = dnsQueryPayload
        )

        val parsedPacket = IpPacket.parse(rawIpv6)
        assertNotNull("Incoming IPv6 DNS packet should parse", parsedPacket)
        assertEquals(6, parsedPacket!!.version)
        assertEquals(17, parsedPacket.protocol)

        val parsedDns = com.clearshield.adblocker.engine.dns.DnsPacket.parse(parsedPacket.udpHeader!!.payload)
        assertNotNull("DNS query payload must parse", parsedDns)
        assertEquals("analytics.tracker.com", parsedDns!!.questions.first().qName)

        val blockedDnsPayload = com.clearshield.adblocker.engine.dns.DnsPacket.buildBlockedResponse(parsedDns, asNxDomain = false)
        val responsePacketBytes = IpPacket.buildResponsePacket(parsedPacket, blockedDnsPayload)

        val responsePacket = IpPacket.parse(responsePacketBytes)
        assertNotNull("IPv6 response packet must be valid", responsePacket)
        assertEquals(6, responsePacket!!.version)
        assertEquals(1, responsePacket.srcIp[15].toInt())
        assertEquals(42, responsePacket.dstIp[15].toInt())

        val respUdp = responsePacket.udpHeader
        assertNotNull(respUdp)
        assertEquals(53, respUdp!!.srcPort)
        assertEquals(59876, respUdp.dstPort)
        assertNotEquals("IPv6 UDP checksum must be calculated", 0, respUdp.checksum)
    }

    @Test
    fun testIpv6MalformedHeaderSafelyHandled() {
        // Less than 40 bytes
        assertNull(IpPacket.parse(ByteArray(39)))

        // Total payload length exceeds actual byte buffer
        val raw = createSampleIpv6UdpPacket()
        raw[4] = 0x7F // Corrupt payload length to 0x7F00
        assertNull(IpPacket.parse(raw))

        // Version bit corrupted from 6 to 4 with invalid IPv4 length is safely rejected
        val raw2 = createSampleIpv6UdpPacket()
        raw2[0] = 0x45.toByte()
        assertNull(IpPacket.parse(raw2))
    }

    @Test
    fun testUnrelatedTcpTrafficNotReset() {
        // IPv4 TCP packet targeting port 80 (HTTP) or 443 (HTTPS)
        val httpPacketRaw = createSampleIpv4TcpSynPacket(srcPort = 35000, dstPort = 80)
        val httpPacket = IpPacket.parse(httpPacketRaw)
        assertNotNull(httpPacket)
        assertEquals(80, httpPacket!!.tcpHeader!!.dstPort)

        val rstHttp = IpPacket.buildTcpRstPacket(httpPacket)
        assertNull("Non-DNS TCP traffic (port 80) must NEVER be reset", rstHttp)

        val httpsPacketRaw = createSampleIpv4TcpSynPacket(srcPort = 35000, dstPort = 443)
        val httpsPacket = IpPacket.parse(httpsPacketRaw)
        assertNotNull(httpsPacket)
        assertEquals(443, httpsPacket!!.tcpHeader!!.dstPort)

        val rstHttps = IpPacket.buildTcpRstPacket(httpsPacket)
        assertNull("Non-DNS TCP traffic (port 443) must NEVER be reset", rstHttps)
    }
}
