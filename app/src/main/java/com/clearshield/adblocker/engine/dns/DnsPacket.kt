package com.clearshield.adblocker.engine.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsQuestion(
    val qName: String,
    val qType: Int,
    val qClass: Int,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DnsQuestion
        return qName == other.qName && qType == other.qType && qClass == other.qClass
    }

    override fun hashCode(): Int {
        var result = qName.hashCode()
        result = 31 * result + qType
        result = 31 * result + qClass
        return result
    }
}

data class DnsHeader(
    val id: Int,
    val qr: Boolean,
    val opcode: Int,
    val aa: Boolean,
    val tc: Boolean,
    val rd: Boolean,
    val ra: Boolean,
    val rcode: Int,
    val qdCount: Int,
    val anCount: Int,
    val nsCount: Int,
    val arCount: Int
)

class DnsPacket(
    val header: DnsHeader,
    val questions: List<DnsQuestion>,
    val rawData: ByteArray
) {
    companion object {
        // Standard and Modern DNS Record Types
        const val TYPE_A = 1
        const val TYPE_NS = 2
        const val TYPE_CNAME = 5
        const val TYPE_SOA = 6
        const val TYPE_PTR = 12
        const val TYPE_MX = 15
        const val TYPE_TXT = 16
        const val TYPE_AAAA = 28
        const val TYPE_SRV = 33
        const val TYPE_SVCB = 64
        const val TYPE_HTTPS = 65
        const val TYPE_ANY = 255
        const val TYPE_OPT = 41 // RFC 6891 EDNS(0)

        // RFC 8914 Extended DNS Error (EDE) Option Codes
        const val EDE_OPTION_CODE = 15
        const val EDE_INFO_CODE_FILTERED = 17 // Prohibited by administrative/ad-blocker filtering
        const val EDE_INFO_CODE_BLOCKED = 22 // Blocked by administrative policy

        const val CLASS_IN = 1

        // Maximum DNS packet size over standard UDP (RFC 1035)
        const val MAX_DNS_UDP_SIZE = 4096
        const val MAX_LABEL_LENGTH = 63
        const val MAX_NAME_LENGTH = 255
        const val MAX_COMPRESSION_HOPS = 16

        fun getTypeName(type: Int): String = when (type) {
            TYPE_A -> "A"
            TYPE_AAAA -> "AAAA"
            TYPE_CNAME -> "CNAME"
            TYPE_HTTPS -> "HTTPS"
            TYPE_SVCB -> "SVCB"
            TYPE_TXT -> "TXT"
            TYPE_NS -> "NS"
            TYPE_MX -> "MX"
            TYPE_SOA -> "SOA"
            TYPE_PTR -> "PTR"
            TYPE_SRV -> "SRV"
            TYPE_ANY -> "ANY"
            else -> "TYPE_$type"
        }

        fun parse(data: ByteArray, length: Int = data.size): DnsPacket? {
            // Header must be at least 12 bytes and packet cannot exceed max UDP size
            if (length < 12 || length > MAX_DNS_UDP_SIZE || length > data.size) {
                return null
            }
            try {
                val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)

                val id = buffer.short.toInt() and 0xFFFF
                val flags = buffer.short.toInt() and 0xFFFF
                val qdCount = buffer.short.toInt() and 0xFFFF
                val anCount = buffer.short.toInt() and 0xFFFF
                val nsCount = buffer.short.toInt() and 0xFFFF
                val arCount = buffer.short.toInt() and 0xFFFF

                // Sanity check question count: normal DNS queries have 1 question, rarely more
                if (qdCount > 100) return null

                val qr = (flags and 0x8000) != 0
                val opcode = (flags and 0x7800) shr 11
                val aa = (flags and 0x0400) != 0
                val tc = (flags and 0x0200) != 0
                val rd = (flags and 0x0100) != 0
                val ra = (flags and 0x0080) != 0
                val rcode = flags and 0x000F

                val header = DnsHeader(
                    id = id, qr = qr, opcode = opcode, aa = aa, tc = tc,
                    rd = rd, ra = ra, rcode = rcode,
                    qdCount = qdCount, anCount = anCount, nsCount = nsCount, arCount = arCount
                )

                val questions = mutableListOf<DnsQuestion>()
                for (i in 0 until qdCount) {
                    if (!buffer.hasRemaining()) return null
                    val questionStart = buffer.position()
                    val qName = readDomainName(buffer, data, length) ?: return null

                    // Type (2 bytes) + Class (2 bytes) = 4 bytes required
                    if (buffer.remaining() < 4) return null
                    val qType = buffer.short.toInt() and 0xFFFF
                    val qClass = buffer.short.toInt() and 0xFFFF
                    val questionEnd = buffer.position()

                    val rawQuestion = ByteArray(questionEnd - questionStart)
                    System.arraycopy(data, questionStart, rawQuestion, 0, rawQuestion.size)

                    questions.add(DnsQuestion(qName, qType, qClass, rawQuestion))
                }

                val packetCopy = ByteArray(length)
                System.arraycopy(data, 0, packetCopy, 0, length)
                return DnsPacket(header, questions, packetCopy)
            } catch (e: Exception) {
                return null
            }
        }

        private fun readDomainName(buffer: ByteBuffer, rawData: ByteArray, limit: Int): String? {
            val sb = StringBuilder()
            var jumped = false
            var originalPos = -1
            var hops = 0
            val visitedOffsets = HashSet<Int>()

            while (buffer.hasRemaining() && hops < MAX_COMPRESSION_HOPS) {
                val len = buffer.get().toInt() and 0xFF
                if (len == 0) {
                    break
                }

                if ((len and 0xC0) == 0xC0) {
                    // Compression pointer (RFC 1035 Section 4.1.4)
                    if (!buffer.hasRemaining()) return null
                    val offset = ((len and 0x3F) shl 8) or (buffer.get().toInt() and 0xFF)

                    // Compression offset must be inside the packet, after header (12 bytes), and not cyclical
                    if (offset < 12 || offset >= limit || !visitedOffsets.add(offset)) {
                        return null
                    }

                    if (!jumped) {
                        originalPos = buffer.position()
                        jumped = true
                    }
                    buffer.position(offset)
                    hops++
                } else if ((len and 0xC0) == 0) {
                    // Standard label: 1 <= length <= 63
                    if (len > MAX_LABEL_LENGTH || buffer.remaining() < len) {
                        return null
                    }
                    val labelBytes = ByteArray(len)
                    buffer.get(labelBytes)

                    if (sb.length + len + 1 > MAX_NAME_LENGTH) {
                        return null
                    }

                    if (sb.isNotEmpty()) {
                        sb.append('.')
                    }
                    sb.append(String(labelBytes, Charsets.US_ASCII))
                } else {
                    // Reserved or unsupported label prefix (0x40 or 0x80)
                    return null
                }
            }

            if (jumped && originalPos != -1 && originalPos <= limit) {
                buffer.position(originalPos)
            }

            val domain = sb.toString().trim().lowercase()
            return if (domain.isNotEmpty()) domain else null
        }

        /**
         * Synthesizes a valid, fully standard-compliant DNS response:
         * - For A: returns 0.0.0.0 (NOERROR)
         * - For AAAA: returns :: (NOERROR)
         * - For HTTPS/SVCB/TXT/etc.: returns NODATA (NOERROR with ANCOUNT = 0)
         * - If asNxDomain = true: returns NXDOMAIN (RCODE = 3)
         * - If includeEde = true: appends RFC 8914 Extended DNS Error (EDE) OPT RR (code 17 Filtered)
         */
        fun buildBlockedResponse(
            query: DnsPacket,
            asNxDomain: Boolean = false,
            ttlSeconds: Int = 300,
            includeEde: Boolean = false,
            edeText: String = "Blocked by ClearShield"
        ): ByteArray {
            val question = query.questions.firstOrNull()
            val out = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)

            // 1. Transaction ID
            out.putShort(query.header.id.toShort())

            // 2. Flags
            val qr = 1 shl 15            // Response
            val aa = 1 shl 10            // Authoritative Answer
            val rd = if (query.header.rd) (1 shl 8) else 0 // Recursion Desired echoed
            val ra = 1 shl 7            // Recursion Available
            val rcode = if (asNxDomain) 3 else 0 // 3 = NXDOMAIN, 0 = NOERROR

            val flags = qr or aa or rd or ra or rcode
            out.putShort(flags.toShort())

            // 3. Section Counts
            out.putShort(if (question != null) 1.toShort() else 0.toShort()) // QDCOUNT

            val hasAnswer = !asNxDomain && question != null &&
                    (question.qType == TYPE_A || question.qType == TYPE_AAAA)

            out.putShort((if (hasAnswer) 1 else 0).toShort()) // ANCOUNT
            out.putShort(0.toShort()) // NSCOUNT
            out.putShort((if (includeEde) 1 else 0).toShort()) // ARCOUNT (EDNS0 OPT RR)

            // 4. Question Section
            if (question != null) {
                out.put(question.rawBytes)
            }

            // 5. Answer Section (if returning dummy IP)
            if (hasAnswer) {
                // Name pointer to question at byte 12: 0xC00C
                out.putShort(0xC00C.toShort())
                out.putShort(question.qType.toShort())
                out.putShort(CLASS_IN.toShort())
                out.putInt(ttlSeconds)

                if (question.qType == TYPE_A) {
                    out.putShort(4.toShort()) // RDLENGTH = 4 bytes (IPv4)
                    out.put(0.toByte())
                    out.put(0.toByte())
                    out.put(0.toByte())
                    out.put(0.toByte()) // 0.0.0.0
                } else if (question.qType == TYPE_AAAA) {
                    out.putShort(16.toShort()) // RDLENGTH = 16 bytes (IPv6)
                    for (i in 0 until 16) {
                        out.put(0.toByte()) // ::
                    }
                }
            }

            // 6. Additional Section (RFC 6891 EDNS0 OPT RR with RFC 8914 EDE)
            if (includeEde) {
                val textBytes = edeText.toByteArray(Charsets.UTF_8)
                val optionDataLen = 2 + textBytes.size // 2 bytes info-code + text
                val rdLength = 4 + optionDataLen // 2 bytes option code + 2 bytes option len + data

                out.put(0.toByte()) // Root name (empty label)
                out.putShort(TYPE_OPT.toShort()) // Type 41 (OPT)
                out.putShort(1232.toShort()) // UDP Payload Size (RFC 8900 recommended MTU safe buffer)
                out.put(0.toByte()) // Extended RCODE (0)
                out.put(0.toByte()) // EDNS Version (0)
                out.putShort(0.toShort()) // Flags (DO = 0, Z = 0)
                out.putShort(rdLength.toShort()) // RDLENGTH

                // Option 15: Extended DNS Error (RFC 8914)
                out.putShort(EDE_OPTION_CODE.toShort()) // Option Code 15
                out.putShort(optionDataLen.toShort()) // Option Length
                out.putShort(EDE_INFO_CODE_FILTERED.toShort()) // INFO-CODE 17 (Filtered)
                out.put(textBytes) // EXTRA-TEXT
            }

            val result = ByteArray(out.position())
            System.arraycopy(out.array(), 0, result, 0, result.size)
            return result
        }
    }
}
