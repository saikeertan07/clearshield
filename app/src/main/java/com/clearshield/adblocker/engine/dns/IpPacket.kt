package com.clearshield.adblocker.engine.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class UdpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val length: Int,
    val checksum: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UdpHeader
        return srcPort == other.srcPort && dstPort == other.dstPort &&
                length == other.length && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = srcPort
        result = 31 * result + dstPort
        result = 31 * result + length
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val seqNumber: Long,
    val ackNumber: Long,
    val flags: Int,
    val dataOffset: Int
)

class IpPacket(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val identification: Int,
    val flags: Int,
    val fragmentOffset: Int,
    val ttl: Int,
    val protocol: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val udpHeader: UdpHeader?,
    val tcpHeader: TcpHeader? = null
) {
    companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val IPV4_MIN_HEADER_LEN = 20
        const val IPV6_HEADER_LEN = 40
        const val UDP_HEADER_LEN = 8
        const val TCP_HEADER_LEN = 20

        // IPv6 Extension Headers (RFC 8200)
        const val NEXT_HEADER_HOPOPT = 0
        const val NEXT_HEADER_ROUTING = 43
        const val NEXT_HEADER_FRAGMENT = 44
        const val NEXT_HEADER_ESP = 50
        const val NEXT_HEADER_AH = 51
        const val NEXT_HEADER_DESTOPT = 60
        const val NEXT_HEADER_NONE = 59

        fun parse(data: ByteArray, length: Int = data.size): IpPacket? {
            if (length < IPV4_MIN_HEADER_LEN || length > data.size) return null
            return try {
                val version = (data[0].toInt() and 0xFF) shr 4
                when (version) {
                    4 -> parseIpv4(data, length)
                    6 -> parseIpv6(data, length)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun parseIpv4(data: ByteArray, length: Int): IpPacket? {
            val versionAndIhl = data[0].toInt() and 0xFF
            val version = versionAndIhl shr 4
            val headerLength = (versionAndIhl and 0x0F) * 4
            if (headerLength < IPV4_MIN_HEADER_LEN || length < headerLength) return null

            val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
            buffer.position(2)
            val totalLength = buffer.short.toInt() and 0xFFFF
            if (totalLength < headerLength || totalLength > length) {
                return null
            }

            val identification = buffer.short.toInt() and 0xFFFF
            val flagsAndFragment = buffer.short.toInt() and 0xFFFF
            val flags = flagsAndFragment shr 13
            val fragmentOffset = (flagsAndFragment and 0x1FFF) * 8

            // Fragmented packets cannot be parsed as standalone DNS datagrams
            val isFragmented = (flagsAndFragment and 0x3FFF) != 0

            val ttl = buffer.get().toInt() and 0xFF
            val protocol = buffer.get().toInt() and 0xFF

            buffer.position(12)
            val srcIp = ByteArray(4)
            buffer.get(srcIp)
            val dstIp = ByteArray(4)
            buffer.get(dstIp)

            var udpHeader: UdpHeader? = null
            var tcpHeader: TcpHeader? = null

            // Parse UDP if unfragmented and protocol is UDP (17)
            if (!isFragmented && protocol == PROTOCOL_UDP && totalLength >= headerLength + UDP_HEADER_LEN) {
                buffer.position(headerLength)
                val srcPort = buffer.short.toInt() and 0xFFFF
                val dstPort = buffer.short.toInt() and 0xFFFF
                val udpLen = buffer.short.toInt() and 0xFFFF
                val udpChecksum = buffer.short.toInt() and 0xFFFF

                // UDP length includes the 8-byte UDP header
                if (udpLen >= UDP_HEADER_LEN && headerLength + udpLen <= totalLength) {
                    val payloadLen = udpLen - UDP_HEADER_LEN
                    val payload = ByteArray(payloadLen)
                    buffer.get(payload)
                    udpHeader = UdpHeader(srcPort, dstPort, udpLen, udpChecksum, payload)
                }
            } else if (!isFragmented && protocol == PROTOCOL_TCP && totalLength >= headerLength + TCP_HEADER_LEN) {
                buffer.position(headerLength)
                val srcPort = buffer.short.toInt() and 0xFFFF
                val dstPort = buffer.short.toInt() and 0xFFFF
                val seqNum = buffer.int.toLong() and 0xFFFFFFFFL
                val ackNum = buffer.int.toLong() and 0xFFFFFFFFL
                val offsetAndFlags = buffer.short.toInt() and 0xFFFF
                val dataOffset = (offsetAndFlags ushr 12) * 4
                val tcpFlags = offsetAndFlags and 0x0FFF
                if (dataOffset >= TCP_HEADER_LEN && headerLength + dataOffset <= totalLength) {
                    tcpHeader = TcpHeader(srcPort, dstPort, seqNum, ackNum, tcpFlags, dataOffset)
                }
            }

            return IpPacket(
                version = version,
                headerLength = headerLength,
                totalLength = totalLength,
                identification = identification,
                flags = flags,
                fragmentOffset = fragmentOffset,
                ttl = ttl,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp,
                udpHeader = udpHeader,
                tcpHeader = tcpHeader
            )
        }

        private fun parseIpv6(data: ByteArray, length: Int): IpPacket? {
            if (length < IPV6_HEADER_LEN) return null
            val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)

            // Byte 0-3: Version (4 bits), Traffic Class (8 bits), Flow Label (20 bits)
            val vtcFlow = buffer.int
            val version = (vtcFlow ushr 28) and 0x0F
            if (version != 6) return null

            // Bytes 4-5: Payload length (length of payload including ext headers and upper-layer protocol)
            val payloadLength = buffer.short.toInt() and 0xFFFF
            val totalLength = IPV6_HEADER_LEN + payloadLength
            if (totalLength > length) return null

            // Byte 6: Next Header
            val nextHeader = buffer.get().toInt() and 0xFF
            // Byte 7: Hop Limit
            val hopLimit = buffer.get().toInt() and 0xFF

            // Bytes 8-23: Source IPv6 (16 bytes)
            val srcIp = ByteArray(16)
            buffer.get(srcIp)

            // Bytes 24-39: Destination IPv6 (16 bytes)
            val dstIp = ByteArray(16)
            buffer.get(dstIp)

            var currentNextHeader = nextHeader
            var currentOffset = IPV6_HEADER_LEN
            var udpHeader: UdpHeader? = null
            var tcpHeader: TcpHeader? = null
            var isFragmented = false

            // Traverse extension headers safely (RFC 8200)
            var maxExtHeaders = 8
            while (currentOffset < totalLength && maxExtHeaders-- > 0) {
                when (currentNextHeader) {
                    PROTOCOL_UDP -> {
                        if (currentOffset + UDP_HEADER_LEN <= totalLength) {
                            buffer.position(currentOffset)
                            val srcPort = buffer.short.toInt() and 0xFFFF
                            val dstPort = buffer.short.toInt() and 0xFFFF
                            val udpLen = buffer.short.toInt() and 0xFFFF
                            val udpChecksum = buffer.short.toInt() and 0xFFFF

                            if (udpLen >= UDP_HEADER_LEN && currentOffset + udpLen <= totalLength) {
                                val payloadLen = udpLen - UDP_HEADER_LEN
                                val payload = ByteArray(payloadLen)
                                buffer.get(payload)
                                udpHeader = UdpHeader(srcPort, dstPort, udpLen, udpChecksum, payload)
                            }
                        }
                        break
                    }
                    PROTOCOL_TCP -> {
                        if (currentOffset + TCP_HEADER_LEN <= totalLength) {
                            buffer.position(currentOffset)
                            val srcPort = buffer.short.toInt() and 0xFFFF
                            val dstPort = buffer.short.toInt() and 0xFFFF
                            val seqNum = buffer.int.toLong() and 0xFFFFFFFFL
                            val ackNum = buffer.int.toLong() and 0xFFFFFFFFL
                            val offsetAndFlags = buffer.short.toInt() and 0xFFFF
                            val dataOffset = (offsetAndFlags ushr 12) * 4
                            val tcpFlags = offsetAndFlags and 0x0FFF
                            if (dataOffset >= TCP_HEADER_LEN && currentOffset + dataOffset <= totalLength) {
                                tcpHeader = TcpHeader(srcPort, dstPort, seqNum, ackNum, tcpFlags, dataOffset)
                            }
                        }
                        break
                    }
                    NEXT_HEADER_HOPOPT, NEXT_HEADER_ROUTING, NEXT_HEADER_DESTOPT -> {
                        if (currentOffset + 2 > totalLength) break
                        currentNextHeader = data[currentOffset].toInt() and 0xFF
                        val hdrExtLen = data[currentOffset + 1].toInt() and 0xFF
                        val extLen = (hdrExtLen + 1) * 8
                        if (currentOffset + extLen > totalLength || extLen < 8) break
                        currentOffset += extLen
                    }
                    NEXT_HEADER_FRAGMENT -> {
                        if (currentOffset + 8 > totalLength) break
                        currentNextHeader = data[currentOffset].toInt() and 0xFF
                        val fragOffsetAndFlags = buffer.getShort(currentOffset + 2).toInt() and 0xFFFF
                        val fragOffset = fragOffsetAndFlags shr 3
                        val moreFragments = (fragOffsetAndFlags and 0x01) != 0
                        if (fragOffset != 0 || moreFragments) {
                            isFragmented = true
                        }
                        if (fragOffset != 0) {
                            // Non-initial fragment; cannot parse UDP payload as standalone DNS datagram
                            break
                        }
                        currentOffset += 8
                    }
                    NEXT_HEADER_AH -> {
                        if (currentOffset + 2 > totalLength) break
                        currentNextHeader = data[currentOffset].toInt() and 0xFF
                        val payloadLen = data[currentOffset + 1].toInt() and 0xFF
                        val extLen = (payloadLen + 2) * 4
                        if (currentOffset + extLen > totalLength || extLen < 8) break
                        currentOffset += extLen
                    }
                    else -> {
                        // Unhandled next header (TCP, ESP, NONE, etc.)
                        break
                    }
                }
            }

            if (isFragmented) {
                udpHeader = null
            }

            return IpPacket(
                version = 6,
                headerLength = IPV6_HEADER_LEN,
                totalLength = totalLength,
                identification = 0,
                flags = 0,
                fragmentOffset = 0,
                ttl = hopLimit,
                protocol = if (udpHeader != null) PROTOCOL_UDP else if (tcpHeader != null) PROTOCOL_TCP else currentNextHeader,
                srcIp = srcIp,
                dstIp = dstIp,
                udpHeader = udpHeader,
                tcpHeader = tcpHeader
            )
        }

        /**
         * Builds an IPv4 or IPv6 UDP packet ready to write to the TUN interface.
         * Swaps original packet's source and destination IP and UDP port.
         */
        fun buildResponsePacket(
            originalPacket: IpPacket,
            udpPayload: ByteArray
        ): ByteArray {
            return if (originalPacket.version == 6) {
                buildIpv6ResponsePacket(originalPacket, udpPayload)
            } else {
                buildIpv4ResponsePacket(originalPacket, udpPayload)
            }
        }

        private fun buildIpv4ResponsePacket(
            originalPacket: IpPacket,
            udpPayload: ByteArray
        ): ByteArray {
            val udpLength = UDP_HEADER_LEN + udpPayload.size
            val ipTotalLength = IPV4_MIN_HEADER_LEN + udpLength
            val out = ByteBuffer.allocate(ipTotalLength).order(ByteOrder.BIG_ENDIAN)

            // IPv4 Header (20 bytes, no options)
            out.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
            out.put(0x00.toByte()) // DSCP/ECN
            out.putShort(ipTotalLength.toShort())
            out.putShort(((originalPacket.identification + 1) and 0xFFFF).toShort())
            out.putShort(0x4000.toShort()) // Flags: Don't Fragment (DF), Fragment offset: 0
            out.put(64.toByte()) // TTL = 64
            out.put(PROTOCOL_UDP.toByte()) // Protocol = 17 (UDP)
            out.putShort(0.toShort()) // Checksum placeholder

            // Swap Source IP (was dstIp) and Destination IP (was srcIp)
            out.put(originalPacket.dstIp)
            out.put(originalPacket.srcIp)

            // Calculate IPv4 Header Checksum
            val ipChecksum = computeOneComplementChecksum(out.array(), 0, IPV4_MIN_HEADER_LEN)
            out.putShort(10, ipChecksum.toShort())

            // UDP Header
            val origUdp = originalPacket.udpHeader
            val srcPort = origUdp?.dstPort ?: 53
            val dstPort = origUdp?.srcPort ?: 0

            val udpStart = IPV4_MIN_HEADER_LEN
            out.position(udpStart)
            out.putShort(srcPort.toShort())
            out.putShort(dstPort.toShort())
            out.putShort(udpLength.toShort())
            out.putShort(0.toShort()) // Checksum placeholder
            out.put(udpPayload)

            // Compute RFC 768 UDP Checksum (with IPv4 Pseudo-header)
            val udpChecksum = computeUdpChecksum(
                srcIp = originalPacket.dstIp,
                dstIp = originalPacket.srcIp,
                udpData = out.array(),
                offset = udpStart,
                length = udpLength
            )
            out.putShort(udpStart + 6, udpChecksum.toShort())

            return out.array()
        }

        private fun buildIpv6ResponsePacket(
            originalPacket: IpPacket,
            udpPayload: ByteArray
        ): ByteArray {
            val udpLength = UDP_HEADER_LEN + udpPayload.size
            val ipTotalLength = IPV6_HEADER_LEN + udpLength
            val out = ByteBuffer.allocate(ipTotalLength).order(ByteOrder.BIG_ENDIAN)

            // IPv6 Header (40 bytes fixed)
            out.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
            out.putShort(udpLength.toShort()) // Payload length = UDP Header + Payload
            out.put(PROTOCOL_UDP.toByte()) // Next Header = 17 (UDP)
            out.put(64.toByte()) // Hop Limit = 64

            // Swap Source IP (was dstIp) and Destination IP (was srcIp)
            out.put(originalPacket.dstIp)
            out.put(originalPacket.srcIp)

            // UDP Header
            val origUdp = originalPacket.udpHeader
            val srcPort = origUdp?.dstPort ?: 53
            val dstPort = origUdp?.srcPort ?: 0

            val udpStart = IPV6_HEADER_LEN
            out.position(udpStart)
            out.putShort(srcPort.toShort())
            out.putShort(dstPort.toShort())
            out.putShort(udpLength.toShort())
            out.putShort(0.toShort()) // Checksum placeholder
            out.put(udpPayload)

            // Compute RFC 8200 IPv6 UDP Checksum (with IPv6 Pseudo-header)
            val udpChecksum = computeIpv6UdpChecksum(
                srcIp = originalPacket.dstIp,
                dstIp = originalPacket.srcIp,
                udpData = out.array(),
                offset = udpStart,
                length = udpLength
            )
            out.putShort(udpStart + 6, udpChecksum.toShort())

            return out.array()
        }

        fun computeOneComplementChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0
            var i = offset
            val end = offset + length
            while (i < end - 1) {
                val b1 = data[i].toInt() and 0xFF
                val b2 = data[i + 1].toInt() and 0xFF
                sum += (b1 shl 8) or b2
                i += 2
            }
            if (i < end) {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return (sum.inv()) and 0xFFFF
        }

        /**
         * Builds a TCP RST (Reset) packet to cleanly reject TCP DNS connection attempts on port 53.
         * Prevents applications attempting TCP DNS from hanging on 30-second connection timeouts.
         */
        fun buildTcpRstPacket(incomingPacket: IpPacket): ByteArray? {
            val inTcp = incomingPacket.tcpHeader ?: return null
            if (inTcp.dstPort != 53) return null // Strictly reset port 53 DNS; never reset unrelated TCP connections
            val isIpv6 = incomingPacket.version == 6
            val ipHeaderLen = if (isIpv6) IPV6_HEADER_LEN else IPV4_MIN_HEADER_LEN
            val tcpHeaderLen = 20
            val totalLen = ipHeaderLen + tcpHeaderLen

            val out = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

            val isSyn = (inTcp.flags and 0x0002) != 0
            val outSeq: Long
            val outAck: Long
            val outFlags: Int

            if (isSyn) {
                // Incoming SYN: ACK = in.seq + 1, SEQ = 0, Flags = RST | ACK (0x0014)
                outSeq = 0L
                outAck = (inTcp.seqNumber + 1) and 0xFFFFFFFFL
                outFlags = 0x0014
            } else {
                // Non-SYN: SEQ = in.ack, ACK = 0, Flags = RST (0x0004)
                outSeq = inTcp.ackNumber
                outAck = 0L
                outFlags = 0x0004
            }

            if (isIpv6) {
                out.putInt(0x60000000)
                out.putShort(tcpHeaderLen.toShort())
                out.put(PROTOCOL_TCP.toByte())
                out.put(64.toByte())
                out.put(incomingPacket.dstIp) // Swap IP
                out.put(incomingPacket.srcIp)
            } else {
                out.put(0x45.toByte())
                out.put(0.toByte())
                out.putShort(totalLen.toShort())
                out.putShort(((incomingPacket.identification + 1) and 0xFFFF).toShort())
                out.putShort(0x4000.toShort()) // DF = 1
                out.put(64.toByte())
                out.put(PROTOCOL_TCP.toByte())
                out.putShort(0.toShort())
                out.put(incomingPacket.dstIp) // Swap IP
                out.put(incomingPacket.srcIp)

                val ipChecksum = computeOneComplementChecksum(out.array(), 0, IPV4_MIN_HEADER_LEN)
                out.putShort(10, ipChecksum.toShort())
            }

            val tcpStart = ipHeaderLen
            out.position(tcpStart)
            out.putShort(inTcp.dstPort.toShort()) // Swap Port
            out.putShort(inTcp.srcPort.toShort())
            out.putInt(outSeq.toInt())
            out.putInt(outAck.toInt())
            out.putShort(((5 shl 12) or outFlags).toShort()) // Data Offset (5 = 20B) + Flags
            out.putShort(0.toShort()) // Window = 0
            out.putShort(0.toShort()) // Checksum placeholder
            out.putShort(0.toShort()) // Urgent pointer = 0

            val tcpChecksum = if (isIpv6) {
                computeIpv6PseudoHeaderChecksum(
                    srcIp = incomingPacket.dstIp,
                    dstIp = incomingPacket.srcIp,
                    protocol = PROTOCOL_TCP,
                    transportData = out.array(),
                    offset = tcpStart,
                    length = tcpHeaderLen
                )
            } else {
                computeIpv4PseudoHeaderChecksum(
                    srcIp = incomingPacket.dstIp,
                    dstIp = incomingPacket.srcIp,
                    protocol = PROTOCOL_TCP,
                    transportData = out.array(),
                    offset = tcpStart,
                    length = tcpHeaderLen
                )
            }
            out.putShort(tcpStart + 16, tcpChecksum.toShort())

            return out.array()
        }

        fun computeUdpChecksum(
            srcIp: ByteArray,
            dstIp: ByteArray,
            udpData: ByteArray,
            offset: Int,
            length: Int
        ): Int = computeIpv4PseudoHeaderChecksum(srcIp, dstIp, PROTOCOL_UDP, udpData, offset, length)

        fun computeIpv4PseudoHeaderChecksum(
            srcIp: ByteArray,
            dstIp: ByteArray,
            protocol: Int,
            transportData: ByteArray,
            offset: Int,
            length: Int
        ): Int {
            var sum = 0

            // IPv4 Pseudo-header
            sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
            sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

            sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
            sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

            sum += protocol and 0xFF
            sum += length

            var i = offset
            val end = offset + length
            while (i < end - 1) {
                val b1 = transportData[i].toInt() and 0xFF
                val b2 = transportData[i + 1].toInt() and 0xFF
                sum += (b1 shl 8) or b2
                i += 2
            }
            if (i < end) {
                sum += (transportData[i].toInt() and 0xFF) shl 8
            }

            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }

            var result = (sum.inv()) and 0xFFFF
            if (result == 0) {
                result = 0xFFFF
            }
            return result
        }

        fun computeIpv6UdpChecksum(
            srcIp: ByteArray,
            dstIp: ByteArray,
            udpData: ByteArray,
            offset: Int,
            length: Int
        ): Int = computeIpv6PseudoHeaderChecksum(srcIp, dstIp, PROTOCOL_UDP, udpData, offset, length)

        fun computeIpv6PseudoHeaderChecksum(
            srcIp: ByteArray,
            dstIp: ByteArray,
            protocol: Int,
            transportData: ByteArray,
            offset: Int,
            length: Int
        ): Int {
            var sum = 0L

            for (i in 0 until 16 step 2) {
                sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            }

            for (i in 0 until 16 step 2) {
                sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
            }

            sum += (length ushr 16) and 0xFFFF
            sum += length and 0xFFFF
            sum += protocol and 0xFF

            var i = offset
            val end = offset + length
            while (i < end - 1) {
                val b1 = transportData[i].toInt() and 0xFF
                val b2 = transportData[i + 1].toInt() and 0xFF
                sum += (b1 shl 8) or b2
                i += 2
            }
            if (i < end) {
                sum += (transportData[i].toInt() and 0xFF) shl 8
            }

            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }

            var result = (sum.inv().toInt()) and 0xFFFF
            if (result == 0) {
                result = 0xFFFF
            }
            return result
        }
    }
}
